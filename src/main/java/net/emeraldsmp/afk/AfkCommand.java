package net.emeraldsmp.afk;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AfkCommand implements CommandExecutor {
    private final EmeraldSMP plugin;
    public AfkCommand(EmeraldSMP plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.getMessageService().send(sender, "&cOnly players can use /afk.");
            return true;
        }
        if (!plugin.getConfig().getBoolean("afk.enabled", true)) {
            plugin.getMessageService().send(p, "&cAFK is currently disabled.");
            return true;
        }
        plugin.getAfkManager().toggle(p);
        return true;
    }
}
