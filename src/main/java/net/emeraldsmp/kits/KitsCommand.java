package net.emeraldsmp.kits;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class KitsCommand implements CommandExecutor {
    private final EmeraldSMP plugin;
    public KitsCommand(EmeraldSMP plugin){this.plugin=plugin;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}
        plugin.getKitsManager().open(p);return true;
    }
}
