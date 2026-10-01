package net.emeraldsmp.roles;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.emeraldsmp.EmeraldSMP;
import net.kyori.adventure.text.Component;
import org.bukkit.event.*;

public final class RoleChatListener implements Listener {
    private final EmeraldSMP plugin;
    public RoleChatListener(EmeraldSMP plugin){this.plugin=plugin;}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        String prefix=RoleUtil.roleLabel(event.getPlayer(),plugin.getRoleManager());
        event.renderer((source, displayName, message, viewer) ->
            Component.text(prefix+" ").append(displayName).append(Component.text(" §8» §f")).append(message));
    }
}
