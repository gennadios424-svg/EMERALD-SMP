package net.emeraldsmp.roles;

import net.emeraldsmp.EmeraldSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.*;

public final class RoleChatListener implements Listener {
    private final EmeraldSMP plugin;
    public RoleChatListener(EmeraldSMP plugin){this.plugin=plugin;}

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        RoleManager.Role r = plugin.getRoleManager().get(event.getPlayer());
        NamedTextColor color = switch(r) {
            case OWNER -> NamedTextColor.RED;
            case DEV -> NamedTextColor.AQUA;
            case MOD -> NamedTextColor.BLUE;
            case MEDIA -> NamedTextColor.LIGHT_PURPLE;
            case EMERALD -> NamedTextColor.GREEN;
            case MVP -> NamedTextColor.GOLD;
            case VIP -> NamedTextColor.YELLOW;
            case MEMBER -> NamedTextColor.GRAY;
        };
        Component badge = Component.text("✦ " + r.label() + " ✦", color).decorate(TextDecoration.BOLD);
        event.renderer((source, displayName, message, viewer) ->
            badge.append(Component.text("  ")).append(displayName)
                 .append(Component.text(" §8» §f")).append(message));
    }
}
