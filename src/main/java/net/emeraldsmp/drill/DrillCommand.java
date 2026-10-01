package net.emeraldsmp.drill;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.List;

public final class DrillCommand implements CommandExecutor,TabCompleter{
    private final net.emeraldsmp.EmeraldSMP plugin;
    private final DrillManager manager;
    public DrillCommand(net.emeraldsmp.EmeraldSMP plugin,DrillManager manager){this.plugin=plugin;this.manager=manager;}
    private boolean admin(CommandSender s){
        if(!s.hasPermission("emerald.admin")){
            s.sendMessage(ChatColor.RED+"❌ You do not have permission to use this command.");
            return false;
        }
        return true;
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(label.equalsIgnoreCase("shardgainer")){
            if(!admin(sender))return true;
            Player target=args.length>0?Bukkit.getPlayerExact(args[0]):(sender instanceof Player p?p:null);
            if(target==null){sender.sendMessage(ChatColor.RED+"Player must be online.");return true;}
            target.getInventory().addItem(manager.createItem(DrillManager.Tool.EMERALD_GAINER,1));
            sender.sendMessage(ChatColor.GREEN+"Gave an Emerald Gainer to "+target.getName()+".");
            return true;
        }
        if(!admin(sender))return true;
        Player target=args.length>0?Bukkit.getPlayerExact(args[0]):(sender instanceof Player p?p:null);
        if(target==null){sender.sendMessage(ChatColor.RED+"Only an online player can receive the Original Drill.");return true;}
        target.getInventory().addItem(manager.createItem(DrillManager.Tool.ORIGINAL_DRILL,1));
        sender.sendMessage(ChatColor.GREEN+"Gave an Original Drill to "+target.getName()+".");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}