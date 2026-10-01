package net.emeraldsmp.shop;

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
import org.bukkit.event.player.PlayerQuitEvent;

/** Inventory routing for the shop and the real writable /sell container. */
public final class ShopListener implements Listener {
    private final EmeraldSMP plugin;

    public ShopListener(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (plugin.getCleanSellManager().isOpen(player, event.getView().getTopInventory())) {
            handleSellClick(event, player);
            return;
        }

        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!title.contains("EMERALD SMP SHOP") && !title.startsWith("💚 SHOP") && !title.startsWith("💚 BUY")) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        if (title.contains("EMERALD SMP SHOP")) {
            if (slot == 31) {
                player.closeInventory();
                return;
            }
            String key = categoryAt(slot);
            if (key != null) plugin.getShopManager().openCategory(player, key, 0);
            return;
        }

        var view = plugin.getShopManager().view(player);
        if (view == null) return;

        if (title.startsWith("💚 SHOP")) {
            if (slot == 45) {
                plugin.getShopManager().openMain(player);
                return;
            }
            if (slot == 53) {
                player.closeInventory();
                return;
            }
            if (slot == 48) {
                plugin.getShopManager().openCategory(player, view.category(), view.page() - 1);
                return;
            }
            if (slot == 50) {
                plugin.getShopManager().openCategory(player, view.category(), view.page() + 1);
                return;
            }
            var item = plugin.getShopManager().itemFor(player, slot);
            if (item != null) plugin.getShopManager().openItem(player, item, view.category(), view.page());
            return;
        }

        if (title.startsWith("💚 BUY")) {
            if (slot == 18) {
                plugin.getShopManager().openCategory(player, view.category(), view.page());
                return;
            }
            if (slot == 22) {
                player.closeInventory();
                return;
            }
            var item = view.items().isEmpty() ? null : (ShopManager.ShopItem) view.items().get(0);
            if (item == null) return;
            int quantity = switch (slot) {
                case 10 -> 1;
                case 11 -> 16;
                case 12 -> 32;
                case 14 -> 64;
                default -> 0;
            };
            if (quantity <= 0) return;
            boolean ok = plugin.getShopManager().buy(player, item, quantity);
            player.sendMessage(ok
                    ? "§a💚 Purchase completed: §f" + quantity + "x " + item.material().name() + "§a."
                    : "§cPurchase could not be completed. Check your balance and inventory space.");
        }
    }

    private void handleSellClick(InventoryClickEvent event, Player player) {
        var manager = plugin.getCleanSellManager();
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        // Control slots are the ONLY protected sell-GUI slots.
        if (rawSlot >= 0 && rawSlot < topSize && manager.isControl(rawSlot)) {
            event.setCancelled(true);
            manager.control(player, rawSlot);
            return;
        }

        // Prevent collect-to-cursor/double-click from harvesting protected control items.
        // All ordinary clicks in input slots 0-44 and the player inventory stay vanilla.
        if (event.getAction().name().equals("COLLECT_TO_CURSOR") || event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        // Number-key/offhand swaps targeting a protected slot are rejected.
        if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND) {
            if (rawSlot >= 0 && rawSlot < topSize && manager.isControl(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }

        // Shift-click from player inventory uses vanilla transfer into slots 0-44.
        // Only reject items with no valid /worth entry.
        if (event.isShiftClick() && event.getClickedInventory() == player.getInventory()) {
            var item = event.getCurrentItem();
            if (item != null && !item.getType().isAir()) {
                var worth = plugin.getWorthManager().get(item.getType());
                if (worth == null || !worth.enabled() || worth.worth() <= 0) {
                    event.setCancelled(true);
                    player.sendMessage("§c❌ This item cannot be sold.");
                    return;
                }
            }
        }

        // CRITICAL: no blanket cancellation here. The sell input area is a real container.
        manager.refreshLater(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void drag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (plugin.getCleanSellManager().isOpen(player, event.getView().getTopInventory())) {
            int topSize = event.getView().getTopInventory().getSize();
            var manager = plugin.getCleanSellManager();

            // Bukkit cannot partially accept one drag event. If it touches a protected
            // control slot, reject the whole drag. Drags wholly in slots 0-44 are vanilla.
            if (event.getRawSlots().stream().anyMatch(slot ->
                    slot >= 0 && slot < topSize && manager.isControl(slot))) {
                event.setCancelled(true);
                return;
            }

            manager.refreshLater(player);
            return;
        }

        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title.contains("EMERALD SMP SHOP") || title.startsWith("💚 SHOP") || title.startsWith("💚 BUY")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && plugin.getCleanSellManager().isOpen(player, event.getInventory())) {
            plugin.getCleanSellManager().close(player);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        plugin.getCleanSellManager().quit(event.getPlayer());
    }

    private String categoryAt(int slot) {
        int[] slots = {11, 13, 15, 21, 23};
        String[] keys = {"resources", "blocks", "redstone", "cpvp", "nether"};
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) return keys[i];
        }
        return null;
    }
}
