package net.emeraldsmp.worth;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorthListener implements Listener {
    private final EmeraldSMP plugin;
    private final Map<UUID, Boolean> waitingForSearch = new ConcurrentHashMap<>();

    public WorthListener(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = ChatColor.stripColor(e.getView().getTitle());
        boolean browser = title.startsWith("💚 WORTH");
        boolean info = e.getView().getTopInventory().getSize() == 27
                && title.startsWith("💚 ")
                && e.getRawSlot() == 22;

        if (!browser && !info) return;

        e.setCancelled(true);

        if (e.getClick() == ClickType.DOUBLE_CLICK
                || e.getClick() == ClickType.NUMBER_KEY
                || e.getClick() == ClickType.SWAP_OFFHAND
                || e.getClick() == ClickType.DROP
                || e.getClick() == ClickType.CONTROL_DROP) {
            return;
        }

        int topSize = e.getView().getTopInventory().getSize();
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= topSize) return;

        plugin.getWorthManager().click(p, slot);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (title.startsWith("💚 WORTH") || title.startsWith("💚 ")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void searchChat(AsyncPlayerChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (waitingForSearch.remove(id) == null) return;

        e.setCancelled(true);
        String query = e.getMessage().trim();
        Player p = e.getPlayer();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (query.equalsIgnoreCase("cancel")) {
                plugin.getMessageService().send(p, "&7Search cancelled.");
                plugin.getWorthManager().reopenCurrent(p);
                return;
            }
            plugin.getWorthManager().searchCurrent(p, query);
        });
    }

    public void beginSearch(Player p) {
        waitingForSearch.put(p.getUniqueId(), true);
        plugin.getMessageService().send(
                p,
                "&eType an item name in chat to search the Worth database. Type &fcancel &eto stop."
        );
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        waitingForSearch.remove(e.getPlayer().getUniqueId());
    }
}
