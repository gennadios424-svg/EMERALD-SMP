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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorthListener implements Listener {
    private final EmeraldSMP plugin;
    private final Map<UUID, Boolean> waitingForSearch = new ConcurrentHashMap<>();

    public WorthListener(EmeraldSMP plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void formatMoneyOnOpen(InventoryOpenEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (!title.startsWith("💚 WORTH")) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!(e.getPlayer() instanceof Player p) || !p.isOnline()) return;
            var inv = p.getOpenInventory().getTopInventory();
            if (title.startsWith("💚 WORTH INFO")) {
                ItemStack subject = inv.getItem(4);
                if (subject != null) {
                    WorthEntry entry = plugin.getWorthManager().get(subject.getType());
                    if (entry != null) {
                        setLore(inv.getItem(11), List.of("§7Worth: §f" + plugin.getEconomyManager().format(entry.worth()) + " §7/ item", "§7Category: §f" + entry.category().displayName()));
                        setLore(inv.getItem(13), List.of("§7Stack Worth: §f" + plugin.getEconomyManager().format(entry.stackWorth()), "§7Stack size: §f" + entry.material().getMaxStackSize()));
                    }
                }
                return;
            }
            for (int slot = 0; slot < Math.min(45, inv.getSize()); slot++) {
                ItemStack item = inv.getItem(slot);
                if (item == null) continue;
                WorthEntry entry = plugin.getWorthManager().get(item.getType());
                if (entry == null) continue;
                ItemMeta meta = item.getItemMeta();
                if (meta == null || !meta.hasLore()) continue;
                List<String> lore = new ArrayList<>(meta.getLore());
                for (int i = 0; i < lore.size(); i++) {
                    String line = ChatColor.stripColor(lore.get(i));
                    if (line.startsWith("Worth:")) lore.set(i, "§aWorth: §f" + plugin.getEconomyManager().format(entry.worth()) + " §7/ item");
                    else if (line.startsWith("Stack Worth:")) lore.set(i, "§7Stack Worth: §f" + plugin.getEconomyManager().format(entry.stackWorth()));
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        });
    }

    private void setLore(ItemStack item, List<String> lore) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = ChatColor.stripColor(e.getView().getTitle());
        boolean browser = title.startsWith("💚 WORTH");
        boolean info = e.getView().getTopInventory().getSize() == 27 && title.startsWith("💚 WORTH INFO");
        if (!browser && !info) return;

        int slot = e.getRawSlot();
        int topSize = e.getView().getTopInventory().getSize();
        if (slot < 0 || slot >= topSize) return;

        e.setCancelled(true);

        if (e.getClick() == ClickType.DOUBLE_CLICK || e.getClick() == ClickType.NUMBER_KEY
                || e.getClick() == ClickType.SWAP_OFFHAND || e.getClick() == ClickType.DROP
                || e.getClick() == ClickType.CONTROL_DROP) return;

        plugin.getWorthManager().click(p, slot);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void drag(InventoryDragEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (title.startsWith("💚 WORTH")) e.setCancelled(true);
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
            } else plugin.getWorthManager().searchCurrent(p, query);
        });
    }

    public void beginSearch(Player p) {
        waitingForSearch.put(p.getUniqueId(), true);
        plugin.getMessageService().send(p, "&eType an item name in chat to search the Worth database. Type &fcancel &eto stop.");
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        waitingForSearch.remove(e.getPlayer().getUniqueId());
    }
}
