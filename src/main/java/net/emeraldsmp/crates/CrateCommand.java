package net.emeraldsmp.crates;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.List;

public final class CrateCommand implements CommandExecutor, TabCompleter {
    private static final List<String> TYPES = List.of("common","spawner","gold","crimson","emerald");
    private final CrateManager manager;

    public CrateCommand(CrateManager manager) { this.manager = manager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GREEN + "/crates give <player> <key> <amount>");
            sender.sendMessage(ChatColor.GREEN + "/crates place <type>");
            sender.sendMessage(ChatColor.GREEN + "/crates remove");
            return true;
        }
        if (!sender.hasPermission("emerald.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args[0].equalsIgnoreCase("give") && args.length >= 3) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player must be online.");
                return true;
            }
            int amount = 1;
            if (args.length >= 4) {
                try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[3]))); }
                catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Invalid amount.");
                    return true;
                }
            }
            MapGive.give(target, manager.createKey(args[2], amount));
            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " " + args[2] + " key(s) to " + target.getName());
            return true;
        }
        if (args[0].equalsIgnoreCase("place") && sender instanceof Player player && args.length >= 2) {
            manager.place(player, args[1]);
            return true;
        }
        if (args[0].equalsIgnoreCase("remove") && sender instanceof Player player) {
            manager.remove(player);
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "Usage: /crates give <player> <key> <amount> | /crates place <type> | /crates remove");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("give", "place", "remove");
        if (args.length == 2 && args[0].equalsIgnoreCase("place")) return TYPES;
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return TYPES;
        return List.of();
    }

    private static final class MapGive {
        static void give(Player player, org.bukkit.inventory.ItemStack item) {
            for (org.bukkit.inventory.ItemStack left : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
    }
}