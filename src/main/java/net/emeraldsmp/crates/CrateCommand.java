package net.emeraldsmp.crates;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public final class CrateCommand implements CommandExecutor,TabCompleter{
    private final CrateManager manager;
    public CrateCommand(CrateManager manager){this.manager=manager;}
    private static final List<String> TYPES=CrateManager.TYPES;
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(label.equalsIgnoreCase("keyall")){
            if(!sender.hasPermission("emerald.admin")){sender.sendMessage(ChatColor.RED+"❌ You do not have permission to use this command.");return true;}
            if(args.length<1||!TYPES.contains(args[0].toLowerCase(Locale.ROOT))){sender.sendMessage(ChatColor.RED+"Usage: /keyall <common|spawner|gold|crimson|emerald> [amount]");return true;}
            int amount=1; if(args.length>1)try{amount=Math.max(1,Math.min(64,Integer.parseInt(args[1])));}catch(Exception e){sender.sendMessage(ChatColor.RED+"Invalid amount.");return true;}
            manager.keyAll(args[0],amount,sender);return true;
        }
        if(args.length==0){if(sender instanceof Player p)manager.openInfo(p);else sender.sendMessage("/crates give <player> <key> <amount> | /crates place <type> | /crate edit <type>");return true;}
        if(!sender.hasPermission("emerald.admin")){sender.sendMessage(ChatColor.RED+"❌ You do not have permission to use this command.");return true;}
        if(args[0].equalsIgnoreCase("edit")&&sender instanceof Player p&&args.length>=2){manager.openEditor(p,args[1]);return true;}
        if(args[0].equalsIgnoreCase("give")&&args.length>=3){
            Player target=Bukkit.getPlayerExact(args[1]);if(target==null){sender.sendMessage(ChatColor.RED+"Player must be online.");return true;}
            int amount=1;if(args.length>=4)try{amount=Math.max(1,Math.min(64,Integer.parseInt(args[3])));}catch(Exception e){sender.sendMessage(ChatColor.RED+"Invalid amount.");return true;}
            for(ItemStack left:target.getInventory().addItem(manager.createKey(args[2],amount)).values())target.getWorld().dropItemNaturally(target.getLocation(),left);
            sender.sendMessage(ChatColor.GREEN+"Gave "+amount+" "+args[2]+" key(s) to "+target.getName());return true;
        }
        if(args[0].equalsIgnoreCase("place")&&sender instanceof Player p&&args.length>=2){manager.place(p,args[1]);return true;}
        if(args[0].equalsIgnoreCase("remove")&&sender instanceof Player p){manager.remove(p);return true;}
        sender.sendMessage(ChatColor.GREEN+"Usage: /crates | /crates give <player> <key> <amount> | /crates place <type> | /crate edit <type> | /crates remove");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return List.of("give","place","remove","edit");
        if(args.length==2&&(args[0].equalsIgnoreCase("place")||args[0].equalsIgnoreCase("edit")))return TYPES;
        if(args.length==3&&args[0].equalsIgnoreCase("give"))return TYPES;
        return List.of();
    }
}