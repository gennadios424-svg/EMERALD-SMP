package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PayCommand implements CommandExecutor {
    private final EmeraldSMP plugin;
    public PayCommand(EmeraldSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "&cOnly players can use this command.");
            return true;
        }
        if (args.length != 2) {
            plugin.getMessageService().send(player, "&cUsage: /pay <player> <amount>");
            return true;
        }
        Player receiver = Bukkit.getPlayerExact(args[0]);
        if (receiver == null) {
            plugin.getMessageService().send(player, "&cThat player must be online.");
            return true;
        }
        if (receiver.getUniqueId().equals(player.getUniqueId())) {
            plugin.getMessageService().send(player, "&cYou cannot pay yourself.");
            return true;
        }
        long amount = plugin.getEconomyManager().parseAmount(args[1]);
        if (amount <= 0) {
            plugin.getMessageService().send(player, "&cAmount must be a positive whole number.");
            return true;
        }
        if (amount > plugin.getEconomyManager().getBalance(player.getUniqueId())) {
            plugin.getMessageService().send(player, "&cYou do not have enough money.");
            return true;
        }
        if (!plugin.getEconomyManager().transfer(player, receiver, amount)) {
            plugin.getMessageService().send(player, "&cPayment failed safely. No money was transferred.");
            return true;
        }
        String money = plugin.getEconomyManager().format(amount);
        plugin.getMessageService().send(player, "&fYou paid &a" + receiver.getName() + " &f" + money + ".");
        plugin.getMessageService().send(receiver, "&fYou received &a" + money + " &ffrom &a" + player.getName() + "&f.");
        return true;
    }
}