package net.emeraldsmp.roles;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class RoleUtil {
    private RoleUtil() {}
    public static String role(Player player) {
        if (player.hasPermission("emerald.owner")) return "OWNER";
        if (player.hasPermission("emerald.dev")) return "DEV";
        if (player.hasPermission("emerald.mod")) return "MOD";
        if (player.hasPermission("emerald.media")) return "MEDIA";
        return "PLAYER";
    }
    public static String rolePrefix(Player player) {
        return switch (role(player)) {
            case "OWNER" -> ChatColor.GOLD + "👑 OWNER ";
            case "DEV" -> ChatColor.AQUA + "🛠 DEV ";
            case "MOD" -> ChatColor.RED + "🛡 MOD ";
            case "MEDIA" -> ChatColor.LIGHT_PURPLE + "🎥 MEDIA ";
            default -> ChatColor.GREEN + "💚 PLAYER ";
        };
    }
    public static String roleLabel(Player player) {
        return switch (role(player)) {
            case "OWNER" -> ChatColor.GOLD + "👑 OWNER";
            case "DEV" -> ChatColor.AQUA + "🛠 DEV";
            case "MOD" -> ChatColor.RED + "🛡 MOD";
            case "MEDIA" -> ChatColor.LIGHT_PURPLE + "🎥 MEDIA";
            default -> ChatColor.GREEN + "💚 PLAYER";
        };
    }
}