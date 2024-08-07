package discord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerInfo;

import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import common.ColorUtil;
import net.dv8tion.jda.api.entities.MessageEmbed;
import velocity.Config;
import velocity.Database;
import velocity.EventListener;
import velocity.Main;
import velocity.PlayerUtil;

public class MessageEditor
{
	private PreparedStatement ps = null;
	public Connection conn = null;
	public final Main plugin;
	private final Logger logger;
	private final Config config;
	private final Database db;
	private final DiscordListener discord;
	private final EmojiManager emoji;
	private final PlayerUtil pu;
	private String avatarUrl = null, addMessage = null, 
			Emoji = null, FaceEmoji = null, targetServerName = null,
			uuid = null, playerName = null;
	private MessageEmbed sendEmbed = null, createEmbed = null;
	private WebhookMessageBuilder builder = null;
	private WebhookEmbed embed = null;
	
	@Inject
	public MessageEditor
	(
		Main plugin, Logger logger, ProxyServer server,
		Config config, Database db, DiscordListener discord,
		EmojiManager emoji, PlayerUtil pu
	)
	{
		this.plugin = plugin;
		this.logger = logger;
		this.config = config;
		this.db = db;
		this.discord = discord;
		this.emoji = emoji;
		this.pu = pu;
	}
	
	public void AddEmbedSomeMessage
	(
		String type, Player player, ServerInfo serverInfo, 
		String serverName, String alternativePlayerName, int playTime,
		String chatMessage
	) 
	{
		if(Objects.isNull(player))
		{
			// player変数がnullかつalternativePlayerNameが与えられていたとき
			if(Objects.nonNull(alternativePlayerName))
			{
				// データベースからuuidを取ってくる
				uuid = pu.getPlayerUUIDByName(alternativePlayerName);
				playerName = alternativePlayerName;
			}
		}
		else
		{
			uuid = player.getUniqueId().toString();
			playerName = player.getUsername();
		}
		
	    avatarUrl = "https://minotar.net/avatar/" + uuid;
	    
	    String EmojiName = config.getString("Discord." + type + "EmojiName", "");

	    // 第二引数に画像URLが入っていないため、もし、EmojiNameという絵文字がなかったら、追加せずにnullで返る
	    // createOrgetEmojiIdの第一引数がnull Or Emptyであった場合、nullで返るので、DiscordBotへのリクエスト回数を減らせる
	    CompletableFuture<String> EmojiFutureId = emoji.createOrgetEmojiId(EmojiName);
	    CompletableFuture<String> FaceEmojiFutureId = emoji.createOrgetEmojiId(playerName, avatarUrl);
	    CompletableFuture<Void> allOf = CompletableFuture.allOf(EmojiFutureId, FaceEmojiFutureId);

	    allOf.thenRun(() -> 
	    {
	        try 
	        {
	            String currentServerName = null;
	            if (Objects.nonNull(serverInfo)) 
	            {
	                currentServerName = serverInfo.getName();
	            } 
	            else 
	            {
	                currentServerName = "";
	            }

	            targetServerName = serverName;
	            if (Objects.isNull(serverName)) 
	            {
	                targetServerName = "";
	            }

	            String EmojiId = EmojiFutureId.get(); // プラスとかマイナスとかの絵文字ID取得
	            String FaceEmojiId = FaceEmojiFutureId.get(); // minecraftのアバターの顔の絵文字Id取得
	            Emoji = emoji.getEmojiString(EmojiName, EmojiId);
	            FaceEmoji = emoji.getEmojiString(playerName, FaceEmojiId);
	            
	            String messageId = EventListener.PlayerMessageIds.getOrDefault(uuid, null);

	            addMessage = null;
	            switch (type) 
	            {
	            	case "MenteOn":
	            		builder = new WebhookMessageBuilder();
				        builder.setUsername("サーバー");
				        if(!config.getString("Discord.MaintenanceOnImageUrl","").isEmpty())
				        {
				        	builder.setAvatarUrl(config.getString("Discord.MaintenanceOnImageUrl"));
				        }
				        
				        embed = new WebhookEmbedBuilder()
				            .setColor(ColorUtil.BLUE.getRGB())  // Embedの色
				            .setDescription("メンテナンスモードが有効になりました。\nいまは遊べないカッ...")
				            .build();
				        builder.addEmbeds(embed);
				        
				        discord.sendWebhookMessage(builder);
	            		break;
	            	
	            	case "MenteOff":
	            		builder = new WebhookMessageBuilder();
				        builder.setUsername("サーバー");
				        if(!config.getString("Discord.MaintenanceOffImageUrl","").isEmpty())
				        {
				        	builder.setAvatarUrl(config.getString("Discord.MaintenanceOffImageUrl"));
				        }
				        
				        embed = new WebhookEmbedBuilder()
				            .setColor(ColorUtil.RED.getRGB())  // Embedの色
				            .setDescription("メンテナンスモードが無効になりました。\nまだまだ遊べるドン！")
				            .build();
				        builder.addEmbeds(embed);
				        
				        discord.sendWebhookMessage(builder);
	            		break;
	            		
	            	case "Invader":
	            		// Invader専用の絵文字は追加する予定はないので、Emojiのnullチェックは不要
	            		if(Objects.nonNull(FaceEmoji))
	            		{
	            			addMessage = "侵入者が現れました。\n\n:arrow_down: 侵入者情報:arrow_down:\nスキン: " + FaceEmoji
	            					+ "\n\nプレイヤーネーム: " + playerName + "\n\nプレイヤーUUID: " + uuid;
	            			
	            			createEmbed = discord.createEmbed
	                                (
	                                		addMessage,
	                                        ColorUtil.RED.getRGB()
	                                );
	            			
	            			discord.sendBotMessage(createEmbed);
	            		}
	            		break;
	            		
	            	case "Chat":
	            		// Chat専用の絵文字は追加する予定はないので、Emojiのnullチェックは不要
	            		if(Objects.nonNull(FaceEmoji))
	            		{
	            			if(config.getBoolean("Discord.ChatType", false))
	            			{
	            				// 編集embedによるChatメッセージ送信
	            				if(Objects.isNull(DiscordEventListener.PlayerChatMessageId))
	            				{
	            					// 直前にEmbedによるChatメッセージを送信しなかった場合
	            					// EmbedChatMessageを送って、MessageIdを
	            					createEmbed = discord.createEmbed
	    	                                (
	    	                                		addMessage,
	    	                                        ColorUtil.GREEN.getRGB()
	    	                                );
	            					
	    	                        discord.sendBotMessageAndgetMessageId(createEmbed, true).thenAccept(messageId2 ->
	    	                        {
	    	                            //logger.info("Message sent with ID: " + messageId2);
	    	                            DiscordEventListener.PlayerChatMessageId = messageId2;
	    	                        });
	            				}
	            				else
	            				{
	            					if (Objects.nonNull(messageId)) 
	        	                	{
	        		                    addMessage = "\n\n" + "<" + FaceEmoji + playerName + ">" + 
	        		                    		"\n" + chatMessage;
	        		                    discord.editBotEmbed(messageId, addMessage);
	        		                    EventListener.PlayerMessageIds.remove(uuid);
	        	                	}
	            				}
	            			}
	            			else
	            			{
	            				// デフォルトのChatメッセージ送信(Webhook送信)
	            				builder = new WebhookMessageBuilder();
	            		        builder.setUsername(playerName);
	            		        builder.setAvatarUrl(avatarUrl);
	            		        builder.setContent(chatMessage);
	            		        
	            		        discord.sendWebhookMessage(builder);
	            			}
	            		}
	            		break;
	            		
	            	case "AddMember":
	                	if (Objects.nonNull(Emoji) && Objects.nonNull(FaceEmoji)) 
	                	{
                            addMessage = Emoji + FaceEmoji +
                                    playerName + "が新規FMCメンバーになりました！:congratulations: ";
                        }
	                	else
	                	{
                            addMessage = playerName + "が新規FMCメンバーになりました！:congratulations: ";
                        }

                        createEmbed = discord.createEmbed
                                (
                                		addMessage,
                                        ColorUtil.PINK.getRGB()
                                );
                        discord.sendBotMessage(createEmbed);
	            		break;
	            		
	                case "Start":
	    	            if (Objects.nonNull(Emoji) && Objects.nonNull(FaceEmoji) && Objects.nonNull(messageId)) 
	    	            {
	                        addMessage = "\n\n" + Emoji + FaceEmoji + playerName + "が" +
	                        		targetServerName+ "サーバーを起動させました。";
	                        discord.editBotEmbed(messageId, addMessage);
	    	            }
	                    break;
	
	                case "Exit":
	                	if (Objects.nonNull(Emoji) && Objects.nonNull(FaceEmoji) && Objects.nonNull(messageId)) 
	                	{
	                		String convStringTime = pu.secondsToStr(playTime);
		                    addMessage = "\n\n" + Emoji + FaceEmoji + playerName + "が" +
		                            currentServerName + "サーバーから退出しました。\n\n:alarm_clock: プレイ時間: "+convStringTime;
		                    discord.editBotEmbed(messageId, addMessage);
		                    EventListener.PlayerMessageIds.remove(uuid);
	                	}
	                    break;
	
	                case "Move":
	                	if (Objects.nonNull(Emoji) && Objects.nonNull(FaceEmoji) && Objects.nonNull(messageId)) 
	                	{
		                    addMessage = "\n\n" + Emoji + FaceEmoji + playerName + "が" +
		                            currentServerName + "サーバーへ移動しました。";
		                    discord.editBotEmbed(messageId, addMessage);
	                	}
	                    break;
	
	                case "Request":
	                	if (Objects.nonNull(Emoji) && Objects.nonNull(FaceEmoji) && Objects.nonNull(messageId)) 
	                	{
		                    addMessage = "\n\n" + Emoji + FaceEmoji + playerName + "が" +
		                            targetServerName + "サーバーの起動リクエストを送りました。";
		                    discord.editBotEmbed(messageId, addMessage);
	                	}
	                    break;
	                    
	                case "Join":
	                	try {
		                	conn = db.getConnection();
		                	if (Objects.nonNull(Emoji) && Objects.nonNull(FaceEmoji)) 
		                	{
	                            // 絵文字IDをアップデートしておく
	                            if(Objects.nonNull(conn))
	                            {
	                            	ps = conn.prepareStatement("UPDATE minecraft SET emid=? WHERE uuid=?;");
		                            ps.setString(1, FaceEmojiId);
		                            ps.setString(2, uuid);
		                            ps.executeUpdate();
	                            }
	
	                            addMessage = Emoji + FaceEmoji +
	                                    playerName + "が" + serverInfo.getName() +
	                                    "サーバーに参加しました。";
	                        } 
		                	else 
		                	{
	                            //logger.info("Emoji ID is null");
	                            if(Objects.nonNull(conn))
	                            {
	                            	// 絵文字IDをアップデートしておく
		                            ps = conn.prepareStatement("UPDATE minecraft SET emid=? WHERE uuid=?;");
		                            ps.setString(1, null);
		                            ps.setString(2, uuid);
		                            ps.executeUpdate();
	                            }
	
	                            addMessage = playerName + "が" + serverInfo.getName() +
	                                    "サーバーに参加しました。";
	                        }
	
	                        createEmbed = discord.createEmbed
	                                (
	                                		addMessage,
	                                        ColorUtil.GREEN.getRGB()
	                                );
	                        discord.sendBotMessageAndgetMessageId(createEmbed).thenAccept(messageId2 ->
	                        {
	                            //logger.info("Message sent with ID: " + messageId2);
	                            EventListener.PlayerMessageIds.put(uuid, messageId2);
	                        });
	                	}
	                	catch (SQLException | ClassNotFoundException e1) 
                        {
                            logger.error("An onConnection error occurred: " + e1.getMessage());
                            for (StackTraceElement element : e1.getStackTrace()) 
                            {
                                logger.error(element.toString());
                            }
                        }
                        break;
                        
	                case "FirstJoin":
                        try {
                            conn = db.getConnection();
                            if (Objects.nonNull(Emoji) && Objects.nonNull(FaceEmoji)) 
                            {
                                //logger.info("Emoji ID retrieved: " + emojiId);
                                ps = conn.prepareStatement("INSERT INTO minecraft (name,uuid,server, emid) VALUES (?,?,?,?);");
                                ps.setString(1, playerName);
                                ps.setString(2, uuid);
                                ps.setString(3, serverInfo.getName());
                                ps.setString(4, FaceEmojiId);
                                ps.executeUpdate();

                                addMessage = Emoji + FaceEmoji +
                                        playerName + "が" + serverInfo.getName() +
                                        "サーバーに初参加です！";
                            }
                            else
                            {
                                //logger.info("Emoji ID is null");
                                ps = conn.prepareStatement("INSERT INTO minecraft (name,uuid,server) VALUES (?,?,?);");
                                ps.setString(1, playerName);
                                ps.setString(2, uuid);
                                ps.setString(3, serverInfo.getName());
                                ps.executeUpdate();

                                addMessage = playerName + "が" + serverInfo.getName() +
                                        "サーバーに初参加です！";
                            }

                            createEmbed = discord.createEmbed
                                    (
                                            addMessage,
                                            ColorUtil.ORANGE.getRGB()
                                    );
                            discord.sendBotMessageAndgetMessageId(createEmbed).thenAccept(messageId2 -> {
                                //logger.info("Message sent with ID: " + messageId2);
                                EventListener.PlayerMessageIds.put(uuid, messageId2);
                            });
                        } 
                        catch (SQLException | ClassNotFoundException e1) 
                        {
                            logger.error("An onConnection error occurred: " + e1.getMessage());
                            for (StackTraceElement element : e1.getStackTrace()) 
                            {
                                logger.error(element.toString());
                            }
                        }
	                    break;
	
	                case "RequestOK":
	                	if (Objects.nonNull(Emoji) && Objects.nonNull(FaceEmoji)) 
	                	{
	                        addMessage = Emoji + "管理者が" + FaceEmoji + playerName + "の" +
	                                targetServerName + "サーバー起動リクエストを受諾しました。";
	                        sendEmbed = discord.createEmbed
	                                (
	                                        addMessage,
	                                        ColorUtil.GREEN.getRGB()
	                                );
	                        discord.sendBotMessage(sendEmbed);
	                	}
	                    break;
	
	                case "RequestCancel":
	                	if (Objects.nonNull(Emoji) && Objects.nonNull(FaceEmoji)) 
	                	{
	                        addMessage = Emoji + "管理者が" + FaceEmoji + playerName + "の" +
	                                targetServerName + "サーバー起動リクエストをキャンセルしました。";
	                        sendEmbed = discord.createEmbed
	                                (
	                                        addMessage,
	                                        ColorUtil.RED.getRGB()
	                                );
	                        discord.sendBotMessage(sendEmbed);
	                	}
	                    break;
	
	                case "RequestNoRes":
	                	if (Objects.nonNull(Emoji) && Objects.nonNull(FaceEmoji))
	                	{
	                        addMessage = Emoji + "管理者が" + FaceEmoji + playerName + "の" +
	                                targetServerName + "サーバー起動リクエストに対して、応答しませんでした。";
	                        sendEmbed = discord.createEmbed
	                                (
	                                        addMessage,
	                                        ColorUtil.BLUE.getRGB()
	                                );
	                        discord.sendBotMessage(sendEmbed);
	                	}
	                    break;
	
	                default:
	                    break;
	            }
	        }
	        catch (Exception e1)
	        {
	            e1.printStackTrace();
	        }
	    });
	}
	
	public void AddEmbedSomeMessage(String type, Player player, String serverName)
	{
		AddEmbedSomeMessage(type, player, null, serverName, null, 0, null);
	}
	
	public void AddEmbedSomeMessage(String type, Player player, ServerInfo serverInfo)
	{
		AddEmbedSomeMessage(type, player, serverInfo, null, null, 0, null);
	}
	
	public void AddEmbedSomeMessage(String type, Player player)
	{
		AddEmbedSomeMessage(type, player, null, null, null, 0, null);
	}
	
	public void AddEmbedSomeMessage(String type, String alternativePlayerName)
	{
		AddEmbedSomeMessage(type, null, null, null, alternativePlayerName, 0, null);
	}
	
	public void AddEmbedSomeMessage(String type, String alternativePlayerName, String serverName)
	{
		AddEmbedSomeMessage(type, null, null, serverName, alternativePlayerName, 0, null);
	}
	
	public void AddEmbedSomeMessage(String type, Player player, ServerInfo serverInfo, int playTime)
	{
		AddEmbedSomeMessage(type, player, serverInfo, null, null, playTime, null);
	}
	
	public void AddEmbedSomeMessage(String type, Player player, ServerInfo serverInfo, String chatMessage)
	{
		AddEmbedSomeMessage(type, player, serverInfo, null, null, 0, chatMessage);
	}
	
	public void AddEmbedSomeMessage(String type)
	{
		AddEmbedSomeMessage(type, null, null, null, null, 0, null);
	}
}
