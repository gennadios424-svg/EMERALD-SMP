package net.emeraldsmp.drill;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.List;

public final class DrillCommand implements CommandExecutor, TabCompleter {
    private final DrillManager manager;
    public DrillCommand(net.emeraldsmp.EmeraldSMP plugin, DrillManager manager) { this.manager = manager; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.GREEN + "Usage: /drill give <player> [tier]");
            return true;
        }
        if (!sender.hasPermission("emerald.admin")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(ChatColor.RED + "Player must be online."); return true; }
        int tier = 1;
        if (args.length > 2) {
            try { tier = Math.max(1, Math.min(5, Integer.parseInt(args[2]))); }
            catch (NumberFormatException ignored) { sender.sendMessage(ChatColor.RED + "Invalid tier."); return true; }
        }
        target.getInventory().addItem(manager.createItem(tier));
        sender.sendMessage(ChatColor.GREEN + "Gave Emerald Drill Tier " + tier + " to " + target.getName());
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("give");
        return List.of();
    }
}