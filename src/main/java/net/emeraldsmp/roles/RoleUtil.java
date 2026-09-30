package net.emeraldsmp.roles;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class RoleUtil {
    private RoleUtil() {}

    public static String role(Player player) {
        if (player.hasPermission("emerald.owner")) return "OWNER";
        if (player.hasPermission("emerald.dev")) return "DEV";
        if (player.hasPermission("emerald.mod")) return "MOD";
        return "";
    }

    public static String rolePrefix(Player player) {
        String role = role(player);
        if (role.isEmpty()) return "";
        return switch (role) {
            case "OWNER" -> ChatColor.GOLD + "👑 ";
            case "DEV" -> ChatColor.AQUA + "🛠 ";
            case "MOD" -> ChatColor.RED + "🔨 ";
            default -> "";
        };
    }

    public static String roleLabel(Player player) {
        String role = role(player);
        if (role.isEmpty()) return "";
        return switch (role) {
            case "OWNER" -> ChatColor.GOLD + "👑 OWNER";
            case "DEV" -> ChatColor.AQUA + "🛠 DEV";
            case "MOD" -> ChatColor.RED + "🔨 MOD";
            default -> role;
        };
    }
}
