package net.emeraldsmp.roles;

import net.emeraldsmp.EmeraldSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.*;

public final class RoleChatListener implements Listener {
    private final EmeraldSMP plugin;

    public RoleChatListener(EmeraldSMP plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        RoleManager.Role r = plugin.getRoleManager().get(event.getPlayer());
        Component badge = RoleUtil.componentBadge(r);

        Component afk = plugin.getAfkManager() != null
                && plugin.getAfkManager().isAfk(event.getPlayer().getUniqueId())
                ? Component.text("💤 [AFK] ", NamedTextColor.GRAY)
                : Component.empty();

        String team = plugin.getTeamManager() == null
                ? ""
                : plugin.getTeamManager().tag(event.getPlayer().getUniqueId());

        Component teamComponent = team.isEmpty()
                ? Component.text("[NONE] ", NamedTextColor.DARK_GRAY)
                : Component.text("[" + team + "] ", NamedTextColor.GRAY);

        event.renderer((source, displayName, message, viewer) ->
                afk.append(badge)
                        .append(Component.text("  "))
                        .append(teamComponent)
                        .append(displayName)
                        .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                        .append(message)
        );
    }
}
