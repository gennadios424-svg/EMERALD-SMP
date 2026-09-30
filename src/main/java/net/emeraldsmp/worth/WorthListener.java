package net.emeraldsmp.worth;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class WorthListener implements Listener {
    private final EmeraldSMP plugin;
    private final Map<UUID, String> queries = new HashMap<>();
    private final Map<UUID, Integer> pages = new HashMap<>();
    private final Map<UUID, WorthCategoryFilter> filters = new HashMap<>();
    private final Map<UUID, WorthSort> sorts = new HashMap<>();

    public WorthListener(EmeraldSMP plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void command(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage().trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.equals("/worth") || lower.startsWith("/worth ")) {
            String q = raw.length() > 6 ? raw.substring(6).trim() : "";
            UUID id = e.getPlayer().getUniqueId();
            queries.put(id, q);
            pages.put(id, 0);
            filters.put(id, WorthCategoryFilter.ALL);
            sorts.put(id, WorthSort.NAME_ASC);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void open(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        if (!isWorth(e.getView().getTitle())) return;
        // The WorthManager owns the GUI layout and click handling.
        // Do not modify its control slots here; doing so can desync buttons from their handlers.
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isWorth(e.getView().getTitle())) return;

        e.setCancelled(true);
        if (e.getClick() == ClickType.DOUBLE_CLICK
                || e.getClick() == ClickType.NUMBER_KEY
                || e.getClick() == ClickType.SWAP_OFFHAND
                || e.getClick() == ClickType.DROP
                || e.getClick() == ClickType.CONTROL_DROP) return;

        int top = e.getView().getTopInventory().getSize();
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= top) return;

        // One source of truth: WorthManager.click().
        // This fixes pagination state getting out of sync with the displayed page,
        // and keeps search/sort controls mapped to the same slots they are rendered in.
        if (top == 54 || (top == 27 && slot == 22)) {
            plugin.getWorthManager().click(p, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e) {
        if (isWorth(e.getView().getTitle())) e.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (isWorth(e.getView().getTitle()) && e.getPlayer() instanceof Player p) {
            pages.putIfAbsent(p.getUniqueId(), 0);
        }
    }

    private boolean isWorth(String title) {
        String t = ChatColor.stripColor(title);
        return t.startsWith("💚 WORTH") || t.startsWith("💚 ");
    }
}
