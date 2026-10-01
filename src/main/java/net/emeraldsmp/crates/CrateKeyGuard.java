package net.emeraldsmp.crates;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class CrateKeyGuard implements Listener {
    private final NamespacedKey legacyKey;

    public CrateKeyGuard(EmeraldSMP plugin) { legacyKey = new NamespacedKey(plugin, "emerald-crate-key"); }

    private boolean legacy(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(legacyKey, PersistentDataType.STRING);
    }

    private void purge(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) if (legacy(contents[i])) p.getInventory().setItem(i, null);
        ItemStack cursor = p.getItemOnCursor();
        if (legacy(cursor)) p.setItemOnCursor(null);
    }

    @EventHandler public void join(PlayerJoinEvent e) { purge(e.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if ((e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) || !legacy(e.getItem())) return;
        e.setCancelled(true);
        purge(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (legacy(e.getCurrentItem()) || legacy(e.getCursor())) { e.setCancelled(true); purge(p); }
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (legacy(e.getOldCursor())) { e.setCancelled(true); purge(p); }
    }

    @EventHandler(ignoreCancelled = true)
    public void drop(PlayerDropItemEvent e) {
        Item entity = e.getItemDrop();
        if (legacy(entity.getItemStack())) { e.setCancelled(true); entity.remove(); purge(e.getPlayer()); }
    }
}
