package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class EcoCommand implements CommandExecutor, TabCompleter {
    private final EmeraldSMP plugin;
    public EcoCommand(EmeraldSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("emerald.admin")) {
            plugin.getMessageService().send(sender, "&cYou do not have permission to do that.");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessageService().send(sender, "&cUsage: /eco <give|take|set|reset> <player> [amount]");
            return true;
        }
        String action = args[0].toLowerCase();
        UUID uuid = plugin.getPlayerDataManager().resolveUuid(args[1]);
        if (uuid == null) {
            plugin.getMessageService().send(sender, "&cThat player is not present in Emerald SMP's persistent player data.");
            return true;
        }
        String targetName = plugin.getPlayerDataManager().getStoredUsername(uuid);
        if (targetName == null || targetName.isBlank()) targetName = args[1];

        if (action.equals("reset")) {
            if (args.length != 2) {
                plugin.getMessageService().send(sender, "&cUsage: /eco reset <player>");
                return true;
            }
            boolean ok = plugin.getEconomyManager().reset(uuid);
            plugin.getMessageService().send(sender, ok ? "&aReset " + targetName + "'s balance." : "&cReset failed safely.");
            return true;
        }
        if (args.length != 3 || !action.matches("give|take|set")) {
            plugin.getMessageService().send(sender, "&cUsage: /eco <give|take|set> <player> <amount>");
            return true;
        }
        long amount = plugin.getEconomyManager().parseAmount(args[2]);
        if (amount <= 0) {
            plugin.getMessageService().send(sender, "&cAmount must be a positive whole amount (examples: 500, 1.5M, 2B).");
            return true;
        }
        boolean ok = switch (action) {
            case "give" -> plugin.getEconomyManager().depositToUuid(uuid, amount, targetName);
            case "take" -> plugin.getEconomyManager().withdraw(uuid, amount);
            case "set" -> plugin.getEconomyManager().setBalance(uuid, amount);
            default -> false;
        };
        plugin.getMessageService().send(sender, ok ? "&a" + action + " " + plugin.getEconomyManager().format(amount) + " " + targetName + "." : "&cTransaction failed safely. No balance was changed.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("emerald.admin")) return Collections.emptyList();
        if (args.length == 1) return List.of("give", "take", "set", "reset");
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            for (var entry : plugin.getPlayerDataManager().snapshotAll().entrySet()) {
                String name = entry.getValue().getUsername();
                if (name != null && !name.isBlank() && !names.contains(name)) names.add(name);
            }
            return names;
        }
        return Collections.emptyList();
    }
}
