package velocity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerInfo;

public class PlayerUtil
{
	private final ProxyServer server;
	private final DatabaseInterface db;
	private final Logger logger;
	
	private Connection conn = null;
	private PreparedStatement ps = null;
	private ResultSet playerlist = null, dbuuid = null, bj_logs = null;
	private ResultSet[] resultsets = {playerlist, dbuuid, bj_logs};
	private List<String> Players = new CopyOnWriteArrayList<>();
	private boolean isLoaded = false;
	
	@Inject
	public PlayerUtil(ProxyServer server, Logger logger, DatabaseInterface db)
	{
		this.server = server;
		this.logger = logger;
		this.db = db;
	}
	
	public synchronized void loadPlayers()
	{
		if (isLoaded) return;
		
		try
		{
			conn = db.getConnection();
			
			if(Objects.isNull(conn))	return;
			
			String sql = "SELECT * FROM minecraft;";
			ps = conn.prepareStatement(sql);
			playerlist = ps.executeQuery();
			
			while(playerlist.next())
			{
				Players.add(playerlist.getString("name"));
			}
			isLoaded = true;
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			db.close_resorce(resultsets, conn, ps);
		}
 	}
	
	public void updatePlayers()
	{
		try
		{
			conn = db.getConnection();
			String sql = "SELECT * FROM minecraft;";
			ps = conn.prepareStatement(sql);
			playerlist = ps.executeQuery();
			
			// Playersリストを初期化
			Players.clear();
					
			while(playerlist.next())
			{
				Players.add(playerlist.getString("name"));
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			db.close_resorce(resultsets, conn, ps);
		}
 	}
	
	public List<String> getPlayerList()
	{
		return Players;
	}
	
	public Optional<Player> getPlayerByName(String playerName)
	{
        return server.getAllPlayers().stream()
                .filter(player -> player.getUsername().equalsIgnoreCase(playerName))
                .findFirst();
    }
	
	public String getPlayerUUIDByName(String playerName)
	{
		try
		{
			conn = db.getConnection();
			String sql = "SELECT uuid FROM minecraft WHERE name=? ORDER BY id DESC LIMIT 1;";
			ps = conn.prepareStatement(sql);
			ps.setString(1, playerName);
			dbuuid = ps.executeQuery();
			if(dbuuid.next())
			{
				return dbuuid.getString("uuid");
			}
			else
			{
				return null;
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}
	
	public int getPlayerTime(Player player, ServerInfo serverInfo)
	{
		try
    	{
        	conn = db.getConnection();
    		
    		// calc playtime
    		String sql = "SELECT * FROM mine_log WHERE uuid=? AND `join`=? ORDER BY id DESC LIMIT 1;";
    		ps = conn.prepareStatement(sql);
    		ps.setString(1, player.getUniqueId().toString());
    		ps.setBoolean(2, true);
    		bj_logs = ps.executeQuery();
    		
    		if(bj_logs.next())
    		{
    			long now_timestamp = Instant.now().getEpochSecond();
                Timestamp bj_time = bj_logs.getTimestamp("time");
                long bj_timestamp = bj_time.getTime() / 1000L;
    			
    			long bj_sa = now_timestamp-bj_timestamp;
        		
    			return (int) bj_sa;
    		}
    	}
    	catch (SQLException | ClassNotFoundException e1)
		{
            e1.printStackTrace();
            return 0;
        }
		return 0;
	}
	
	public String secondsToStr(int seconds) {
        if (seconds < 60) {
            if (seconds < 10) {
                return String.format("00:00:0%d", seconds);
            }
            return String.format("00:00:%d", seconds);
        } else if (seconds < 3600) {
            int minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("00:%02d:%02d", minutes, seconds);
        } else {
            int hours = seconds / 3600;
            int remainingSeconds = seconds % 3600;
            int minutes = remainingSeconds / 60;
            seconds = remainingSeconds % 60;
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
    }
	
	public String getPlayerNameFromUUID(UUID uuid)
    {
        String uuidString = uuid.toString().replace("-", "");
        String urlString = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidString;
        
        try
        {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(urlString))
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200)
            {
                // JSONレスポンスを解析
                Gson gson = new Gson();
                JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
                return jsonResponse.get("name").getAsString();
            }
            else
            {
            	logger.error("GETリクエストに失敗しました。HTTPエラーコード: " + response.statusCode());
                return null;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }
}
