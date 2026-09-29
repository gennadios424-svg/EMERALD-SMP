package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BalanceCommand implements CommandExecutor {
    private final EmeraldSMP plugin;
    public BalanceCommand(EmeraldSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "&cOnly players can use this command.");
            return true;
        }
        plugin.getMessageService().send(player, "&fYour balance: &a" + plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player.getUniqueId())));
        return true;
    }
}