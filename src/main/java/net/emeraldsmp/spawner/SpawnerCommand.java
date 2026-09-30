package net.emeraldsmp.spawner;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;

public final class SpawnerCommand implements CommandExecutor, TabCompleter {
    private final EmeraldSMP plugin;
    private final SpawnerManager manager;
    public SpawnerCommand(EmeraldSMP plugin, SpawnerManager manager){this.plugin=plugin;this.manager=manager;}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(args.length==0){sender.sendMessage(ChatColor.GREEN+"Spawner system: /spawner give <player> skeleton [amount]");return true;}
        if(!args[0].equalsIgnoreCase("give")||args.length<3){sender.sendMessage(ChatColor.RED+"Usage: /spawner give <player> skeleton [amount]");return true;}
        if(!sender.hasPermission("emerald.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
        Player target=Bukkit.getPlayerExact(args[1]);if(target==null){sender.sendMessage(ChatColor.RED+"Player must be online.");return true;}
        if(!args[2].equalsIgnoreCase("skeleton")){sender.sendMessage(ChatColor.RED+"Only skeleton spawners are currently available.");return true;}
        int amount=1;try{amount=Math.max(1,Math.min(64,Integer.parseInt(args.length>3?args[3]:"1")));}catch(NumberFormatException ex){sender.sendMessage(ChatColor.RED+"Invalid amount.");return true;}
        target.getInventory().addItem(manager.createItem(amount));sender.sendMessage(ChatColor.GREEN+"Gave "+amount+" Skeleton Spawner(s) to "+target.getName());return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return List.of("give");
        if(args.length==3)return List.of("skeleton");
        return List.of();
    }
}
