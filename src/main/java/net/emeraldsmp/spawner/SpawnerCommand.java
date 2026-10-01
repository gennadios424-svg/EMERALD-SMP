package net.emeraldsmp.spawner;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;

public final class SpawnerCommand implements CommandExecutor, TabCompleter {
    private final SpawnerManager manager;
    public SpawnerCommand(EmeraldSMP plugin, SpawnerManager manager) { this.manager = manager; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GREEN + "Usage: /spawner give <player> <skeleton|zombie|spider|creeper> [stack]");
            return true;
        }
        if (!args[0].equalsIgnoreCase("give") || args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /spawner give <player> <skeleton|zombie|spider|creeper> [stack]");
            return true;
        }
        if (!sender.hasPermission("emerald.admin")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(ChatColor.RED + "Player must be online."); return true; }
        String type = args[2].toLowerCase();
        if (!List.of(SpawnerManager.TYPE_SKELETON, SpawnerManager.TYPE_ZOMBIE, SpawnerManager.TYPE_SPIDER, SpawnerManager.TYPE_CREEPER).contains(type)) {
            sender.sendMessage(ChatColor.RED + "Unknown spawner type.");
            return true;
        }
        int stack = 1;
        try { stack = Math.max(1, Math.min(64, Integer.parseInt(args.length > 3 ? args[3] : "1"))); }
        catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Invalid stack amount."); return true; }
        target.getInventory().addItem(manager.createItem(type, stack));
        sender.sendMessage(ChatColor.GREEN + "Gave " + stack + "x " + type + " spawner to " + target.getName());
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("give");
        if (args.length == 3) return List.of("skeleton", "zombie", "spider", "creeper");
        return List.of();
    }
}
