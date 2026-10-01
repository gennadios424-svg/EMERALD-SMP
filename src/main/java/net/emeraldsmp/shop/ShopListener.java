package net.emeraldsmp.shop;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ShopListener implements Listener {
    private final net.emeraldsmp.EmeraldSMP plugin;
    public ShopListener(net.emeraldsmp.EmeraldSMP plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (plugin.getShopManager().isSellInventory(p, e.getView().getTopInventory())) {
            int slot = e.getRawSlot();
            // The lower control row is never a valid item destination. Shift-click,
            // double-click and keyboard transfers are disabled to close duplication paths.
            if (e.isShiftClick() || e.getClick().isKeyboardClick() || e.getClick() == ClickType.DOUBLE_CLICK) {
                e.setCancelled(true);
                return;
            }
            if (slot >= 45) {
                e.setCancelled(true);
                plugin.getShopManager().handleSellClick(p, slot);
                return;
            }
            if (slot >= 0 && slot < 45) {
                // Normal clicks are allowed inside the sell input area.
                plugin.getShopManager().handleSellClick(p, slot);
            }
            return;
        }

        String title = ChatColor.stripColor(e.getView().getTitle());
        if (!title.contains("EMERALD SMP SHOP") && !title.startsWith("💚 SHOP") && !title.startsWith("💚 BUY")) return;
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getView().getTopInventory().getSize()) return;

        if (title.contains("EMERALD SMP SHOP")) {
            if (slot == 31) { p.closeInventory(); return; }
            String key = categoryAt(slot);
            if (key != null) plugin.getShopManager().openCategory(p, key, 0);
            return;
        }

        ShopManager.ShopView v = plugin.getShopManager().view(p);
        if (v == null) return;
        if (title.startsWith("💚 SHOP")) {
            if (slot == 45) { plugin.getShopManager().openMain(p); return; }
            if (slot == 53) { p.closeInventory(); return; }
            if (slot == 48) { plugin.getShopManager().openCategory(p, v.category(), v.page() - 1); return; }
            if (slot == 50) { plugin.getShopManager().openCategory(p, v.category(), v.page() + 1); return; }
            ShopManager.ShopItem item = plugin.getShopManager().itemFor(p, slot);
            if (item != null) plugin.getShopManager().openItem(p, item, v.category(), v.page());
            return;
        }

        if (title.startsWith("💚 BUY")) {
            if (slot == 18) { plugin.getShopManager().openCategory(p, v.category(), v.page()); return; }
            if (slot == 22) { p.closeInventory(); return; }
            ShopManager.ShopItem item = v.items().isEmpty() ? null : (ShopManager.ShopItem) v.items().get(0);
            if (item == null) return;
            int qty = switch (slot) { case 10 -> 1; case 11 -> 16; case 12 -> 32; case 14 -> 64; default -> 0; };
            if (qty <= 0) return;
            boolean ok = plugin.getShopManager().buy(p, item, qty);
            p.sendMessage(ok ? "§a💚 Purchase completed: §f" + qty + "x " + item.material().name() + "§a." : "§cPurchase could not be completed. Check your balance and inventory space.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void drag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (plugin.getShopManager().isSellInventory(p, e.getView().getTopInventory())) {
            if (e.getRawSlots().stream().anyMatch(s -> s >= 45) || e.getRawSlots().stream().anyMatch(s -> s < 0)) e.setCancelled(true);
            else plugin.getShopManager().handleSellClick(p, e.getRawSlots().iterator().next());
            return;
        }
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (title.contains("EMERALD SMP SHOP") || title.startsWith("💚 SHOP") || title.startsWith("💚 BUY")) e.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p && plugin.getShopManager().isSellInventory(p, e.getInventory())) plugin.getShopManager().closeSell(p);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        plugin.getShopManager().returnSellItems(e.getPlayer());
    }

    private String categoryAt(int slot) {
        for (int i = 0; i < CATEGORY_SLOTS.length; i++) if (CATEGORY_SLOTS[i] == slot) return CATEGORY_KEYS[i];
        return null;
    }
    private static final int[] CATEGORY_SLOTS = {11, 13, 15, 21, 23};
    private static final String[] CATEGORY_KEYS = {"resources", "blocks", "redstone", "cpvp", "nether"};
}
