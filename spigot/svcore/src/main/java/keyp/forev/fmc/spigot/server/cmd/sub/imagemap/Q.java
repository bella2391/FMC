package keyp.forev.fmc.spigot.server.cmd.sub.imagemap;

import java.util.Collections;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import keyp.forev.fmc.spigot.Main;
import keyp.forev.fmc.spigot.server.ImageMap;

public class Q implements TabExecutor {
  @Override
  public boolean onCommand(CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
    if (!sender.hasPermission("fmc.q")) {
      sender.sendMessage(ChatColor.RED + "権限がありません。");
      return true;
    }
    if (args.length < 1) {
      sender.sendMessage("Usage: /q <code>");
      return true;
    }
    Main.getInjector().getInstance(ImageMap.class).executeQ(sender, args, true);
    return true;
  }

  @Override
  public java.util.List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
    // List<String> ret = new ArrayList<>();
    // if (!sender.hasPermission("fmc.q")) {
    //   ret.add("code");
    //   return ret;
    // }
    return Collections.emptyList();
  }
}
