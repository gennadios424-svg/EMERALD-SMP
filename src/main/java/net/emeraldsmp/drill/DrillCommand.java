package net.emeraldsmp.drill;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.List;

public final class DrillCommand implements CommandExecutor, TabCompleter {
    private final DrillManager manager;
    public DrillCommand(net.emeraldsmp.EmeraldSMP plugin, DrillManager manager){this.manager=manager;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(args.length==0){
            if(!(sender instanceof Player p)){sender.sendMessage(ChatColor.RED+"Only players can open /drill.");return true;}
            manager.open(p); return true;
        }
        if(!sender.hasPermission("emerald.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
        if(args.length<2||!args[0].equalsIgnoreCase("give")){sender.sendMessage(ChatColor.GREEN+"Usage: /drill [give <player> [original|gainer] [tier]]");return true;}
        Player target=Bukkit.getPlayerExact(args[1]);if(target==null){sender.sendMessage(ChatColor.RED+"Player must be online.");return true;}
        DrillManager.Tool tool=DrillManager.Tool.ORIGINAL_DRILL; int tier=1;
        if(args.length>2){
            if(args[2].equalsIgnoreCase("gainer")||args[2].equalsIgnoreCase("emeraldgainer"))tool=DrillManager.Tool.EMERALD_GAINER;
            else if(args[2].equalsIgnoreCase("original")||args[2].equalsIgnoreCase("drill"))tool=DrillManager.Tool.ORIGINAL_DRILL;
            else {try{tier=Integer.parseInt(args[2]);}catch(NumberFormatException ex){sender.sendMessage(ChatColor.RED+"Tool must be original or gainer.");return true;}}
        }
        if(args.length>3)try{tier=Integer.parseInt(args[3]);}catch(NumberFormatException ex){sender.sendMessage(ChatColor.RED+"Invalid tier.");return true;}
        tier=Math.max(1,Math.min(5,tier));target.getInventory().addItem(manager.createItem(tool,tier));
        sender.sendMessage(ChatColor.GREEN+"Gave "+(tool==DrillManager.Tool.ORIGINAL_DRILL?"Original Drill":"Emerald Gainer")+" Tier "+tier+" to "+target.getName());return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return List.of("give"); if(args.length==3)return List.of("original","gainer"); return List.of();
    }
}