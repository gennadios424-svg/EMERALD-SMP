package net.emeraldsmp.roles;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class RoleUtil {
    private RoleUtil() {}

    public static String role(Player player) { return "MEMBER"; }
    public static String role(Player player, RoleManager manager) { return manager.get(player).label(); }

    public static String legacyHex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) return "§a";
        StringBuilder b = new StringBuilder("§x");
        for (char c : h.toCharArray()) b.append('§').append(c);
        return b.toString();
    }

    public static String legacyBadge(RoleManager.Role r) {
        String color = r.colors().length > 1 ? r.colors()[1] : "#ffffff";
        return legacyHex(color) + "§l" + r.icon() + " " + r.label();
    }

    public static String roleBadge(Player player, RoleManager manager) { return legacyBadge(manager.get(player)); }
    public static String rolePrefix(Player player, RoleManager manager) { return roleBadge(player, manager) + " §f"; }
    public static String roleLabel(Player player, RoleManager manager) { return roleBadge(player, manager); }
    public static String tabName(Player player, RoleManager manager) { return rolePrefix(player, manager) + player.getName(); }

    public static net.kyori.adventure.text.Component componentBadge(RoleManager.Role r) {
        String text = r.icon() + " " + r.label();
        String color = r.colors().length > 1 ? r.colors()[1] : "#ffffff";
        net.kyori.adventure.text.format.TextColor c =
                net.kyori.adventure.text.format.TextColor.fromHexString(color);
        return net.kyori.adventure.text.Component.text(text)
                .color(c)
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
    }

    public static ChatColor chatColor(RoleManager.Role role) {
        return switch (role) {
            case OWNER -> ChatColor.RED;
            case DEV -> ChatColor.DARK_AQUA;
            case MOD -> ChatColor.DARK_GREEN;
            case EMERALD -> ChatColor.GREEN;
            case MVP -> ChatColor.LIGHT_PURPLE;
            case VIP -> ChatColor.GOLD;
            case MEDIA -> ChatColor.AQUA;
            case MEMBER -> ChatColor.GRAY;
        };
    }
}
