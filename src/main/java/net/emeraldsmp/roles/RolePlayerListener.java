package net.emeraldsmp.roles;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class RolePlayerListener implements Listener {
    private final EmeraldSMP plugin;
    public RolePlayerListener(EmeraldSMP plugin){this.plugin=plugin;}
    @EventHandler public void join(PlayerJoinEvent e){plugin.getRoleManager().ensureMember(e.getPlayer());plugin.getRoleManager().refresh(e.getPlayer());}
}
