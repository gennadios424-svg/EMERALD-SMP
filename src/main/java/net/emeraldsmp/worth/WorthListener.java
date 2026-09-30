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
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
    private final Map<UUID, Boolean> waitingForSearch = new HashMap<>();

    public WorthListener(EmeraldSMP plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void command(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage().trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (!lower.equals("/worth") && !lower.startsWith("/worth ")) return;

        UUID id = e.getPlayer().getUniqueId();
        String q = raw.length() > 6 ? raw.substring(6).trim() : "";
        queries.put(id, q);
        pages.put(id, 0);
        filters.put(id, WorthCategoryFilter.ALL);
        sorts.put(id, WorthSort.NAME_ASC);
        waitingForSearch.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void open(InventoryOpenEvent e) {
        // GUI is identified by its title; all actual control handling lives in WorthManager.
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

        // Every worth browser click is routed directly to the single WorthManager handler.
        // Do not duplicate slot logic here: that was causing buttons to become desynchronised.
        if (top == 54 || (top == 27 && slot == 22)) {
            plugin.getWorthManager().click(p, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e) {
        if (isWorth(e.getView().getTitle())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void searchChat(AsyncPlayerChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!Boolean.TRUE.equals(waitingForSearch.remove(id))) return;

        e.setCancelled(true);
        String query = e.getMessage().trim();
        Player p = e.getPlayer();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (query.equalsIgnoreCase("cancel")) {
                plugin.getMessageService().send(p, "&7Search cancelled.");
                return;
            }
            queries.put(id, query);
            pages.put(id, 0);
            filters.putIfAbsent(id, WorthCategoryFilter.ALL);
            sorts.putIfAbsent(id, WorthSort.NAME_ASC);
            plugin.getWorthManager().openBrowser(p, query, 0, filters.get(id), sorts.get(id));
        });
    }

    public void beginSearch(Player p) {
        waitingForSearch.put(p.getUniqueId(), true);
        plugin.getMessageService().send(p, "&eType an item name in chat to search the Worth database. Type &fcancel &eto stop.");
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (isWorth(e.getView().getTitle()) && e.getPlayer() instanceof Player p) {
            pages.putIfAbsent(p.getUniqueId(), 0);
        }
    }

    private boolean isWorth(String title) {
        String t = ChatColor.stripColor(title == null ? "" : title);
        return t.startsWith("💚 WORTH") || t.startsWith("💚 ");
    }
}
