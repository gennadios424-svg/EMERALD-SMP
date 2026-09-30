package net.emeraldsmp.roles;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class RoleChatListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Component prefix = Component.text(strip(RoleUtil.roleLabel(event.getPlayer())) + " ");
        event.renderer((source, displayName, message, viewer) ->
            prefix.append(displayName).append(Component.text(" §8» §f")).append(message));
    }
    private String strip(String value) { return value.replaceAll("§[0-9A-FK-ORa-fk-or]", ""); }
}