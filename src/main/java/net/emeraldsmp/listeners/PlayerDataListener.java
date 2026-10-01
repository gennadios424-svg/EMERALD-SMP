package net.emeraldsmp.listeners;

import net.emeraldsmp.data.PlayerDataManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerDataListener implements Listener {
    private final PlayerDataManager playerDataManager;
    public PlayerDataListener(PlayerDataManager playerDataManager) { this.playerDataManager = playerDataManager; }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var data = playerDataManager.loadOrCreate(player);
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            data.setLastIp(player.getAddress().getAddress().getHostAddress());
            playerDataManager.save(data);
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        playerDataManager.markSeen(player);
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            var data = playerDataManager.getLoaded(player.getUniqueId());
            if (data != null) {
                data.setLastIp(player.getAddress().getAddress().getHostAddress());
                playerDataManager.save(data);
            }
        }
    }
}
