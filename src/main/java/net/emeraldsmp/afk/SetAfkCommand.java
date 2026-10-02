package net.emeraldsmp.afk;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class SetAfkCommand implements CommandExecutor {
    private final EmeraldSMP plugin;
    public SetAfkCommand(EmeraldSMP plugin){this.plugin=plugin;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}
        if(!p.hasPermission("emerald.admin")){p.sendMessage("§cYou do not have permission to use /setafk.");return true;}
        Location l=p.getLocation();
        plugin.getAfkManager().setAfkLocation(l);
        p.sendMessage("§a💚 AFK location saved at your exact current location.");
        return true;
    }
}
