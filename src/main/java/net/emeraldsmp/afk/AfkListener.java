package net.emeraldsmp.afk;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AfkListener implements Listener {
    private final AfkManager manager;
    public AfkListener(AfkManager manager) { this.manager = manager; }
    private net.emeraldsmp.EmeraldSMP managerPlugin() { try { java.lang.reflect.Field f=AfkManager.class.getDeclaredField("plugin"); f.setAccessible(true); return (net.emeraldsmp.EmeraldSMP) f.get(manager); } catch(Exception ex) { throw new IllegalStateException(ex); } }

    @EventHandler public void join(PlayerJoinEvent e) { manager.join(e.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent e) { manager.quit(e.getPlayer()); }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void move(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (e.getFrom().getX() == e.getTo().getX() && e.getFrom().getY() == e.getTo().getY()
                && e.getFrom().getZ() == e.getTo().getZ()
                && e.getFrom().getYaw() == e.getTo().getYaw()
                && e.getFrom().getPitch() == e.getTo().getPitch()) return;
        manager.touch(e.getPlayer());
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void chat(AsyncPlayerChatEvent e) { org.bukkit.Bukkit.getScheduler().runTask(managerPlugin(), () -> manager.touch(e.getPlayer())); }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void command(PlayerCommandPreprocessEvent e) { if (e.getMessage().equalsIgnoreCase("/afk") || e.getMessage().toLowerCase().startsWith("/afk ")) return; manager.touch(e.getPlayer()); }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void interact(PlayerInteractEvent e) { manager.touch(e.getPlayer()); }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e) { manager.touch(e.getPlayer()); }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void placeBlock(BlockPlaceEvent e) { manager.touch(e.getPlayer()); }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void attack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) manager.touch(p);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void inventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) manager.touch(p);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void inventoryDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p) manager.touch(p);
    }
}
