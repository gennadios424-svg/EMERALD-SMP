package net.emeraldsmp.roles;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class RoleUtil {
    private RoleUtil() {}

    public static String role(Player player) {
        return "MEMBER";
    }

    public static String role(Player player, RoleManager manager) {
        return manager.get(player).label();
    }

    public static String rolePrefix(Player player, RoleManager manager) {
        RoleManager.Role r=manager.get(player);
        return r.color()+r.label()+" ";
    }

    public static String roleLabel(Player player, RoleManager manager) {
        RoleManager.Role r=manager.get(player);
        return r.color()+r.label();
    }

    public static String tabName(Player player, RoleManager manager) {
        return rolePrefix(player,manager)+"§f"+player.getName();
    }

    public static ChatColor chatColor(RoleManager.Role role) {
        return switch(role){
            case OWNER->ChatColor.RED; case DEV->ChatColor.AQUA; case MOD->ChatColor.BLUE;
            case MEDIA->ChatColor.LIGHT_PURPLE; case EMERALD->ChatColor.DARK_GREEN;
            case MVP->ChatColor.GOLD; case VIP->ChatColor.YELLOW; case MEMBER->ChatColor.GRAY;
        };
    }
}
