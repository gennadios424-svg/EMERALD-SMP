package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class BalanceCommand implements CommandExecutor, TabCompleter {
    private final EmeraldSMP plugin;
    public BalanceCommand(EmeraldSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "&cOnly players can use this command.");
            return true;
        }
        if (args.length == 0) {
            sendBalance(player, player.getName(), player.getUniqueId());
            return true;
        }
        if (args.length == 1) {
            Player online = Bukkit.getPlayerExact(args[0]);
            if (online != null) {
                sendBalance(player, online.getName(), online.getUniqueId());
                return true;
            }
            var offline = Bukkit.getOfflinePlayer(args[0]);
            if (!offline.hasPlayedBefore()) {
                plugin.getMessageService().send(player, "&cPlayer not found: &f" + args[0]);
                return true;
            }
            sendBalance(player, offline.getName() == null ? args[0] : offline.getName(), offline.getUniqueId());
            return true;
        }
        plugin.getMessageService().send(player, "&cUsage: /bal [player]");
        return true;
    }

    private void sendBalance(Player viewer, String name, java.util.UUID uuid) {
        long balance = plugin.getEconomyManager().getBalance(uuid);
        if (Bukkit.getPlayer(uuid) == null) {
            var offline = Bukkit.getOfflinePlayer(uuid);
            if (offline.hasPlayedBefore()) {
                balance = plugin.getPlayerDataManager().loadOrCreate(uuid, name).getBalance();
            }
        }
        plugin.getMessageService().send(viewer, "&f💰 " + name + "'s Balance: &a" + plugin.getEconomyManager().format(balance));
    }

    @Override public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return java.util.List.of();
    }
}
