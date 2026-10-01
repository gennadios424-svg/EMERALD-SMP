package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;

public final class ShopListener implements Listener {
    private final EmeraldSMP plugin;
    public ShopListener(EmeraldSMP plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (plugin.getShopManager().isSellInventory(p, e.getView().getTopInventory())) {
            int slot = e.getRawSlot();

            // Footer is read-only. Shift-click / hotbar-swap into or out of the
            // sell inventory is blocked so the transaction can only contain
            // deliberate items placed in slots 0-44.
            if (slot >= 45 && slot < 54) {
                e.setCancelled(true);
                return;
            }
            if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || e.getAction() == InventoryAction.HOTBAR_SWAP
                    || e.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
                e.setCancelled(true);
                return;
            }
            // Normal clicks may move items between the player's inventory and
            // sell slots 0-44. Bottom-inventory clicks are also allowed.
            return;
        }

        String title = ChatColor.stripColor(e.getView().getTitle());
        if (!title.contains("EMERALD SMP SHOP") && !title.startsWith("💚 SHOP") && !title.equals("💚 BUY ITEM"))
            return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getView().getTopInventory().getSize()) return;

        if (title.contains("EMERALD SMP SHOP")) {
            if (slot == 22 || slot == 53) { p.closeInventory(); return; }
            String key = categoryAt(slot);
            if (key != null) plugin.getShopManager().openCategory(p, key, 0);
            return;
        }

        ShopManager.ShopView view = plugin.getShopManager().view(p);
        if (view == null) return;

        if (title.startsWith("💚 SHOP")) {
            if (slot == 45) { plugin.getShopManager().openMain(p); return; }
            if (slot == 53) { p.closeInventory(); return; }
            if (slot == 48 && view.page() > 0) {
                plugin.getShopManager().openCategory(p, view.category(), view.page() - 1);
                return;
            }
            if (slot == 50) {
                plugin.getShopManager().openCategory(p, view.category(), view.page() + 1);
                return;
            }
            ShopManager.ShopItem item = plugin.getShopManager().itemFor(p, slot);
            if (item != null) plugin.getShopManager().openItem(p, item, view.category(), view.page());
            return;
        }

        if (title.equals("💚 BUY ITEM")) {
            if (slot == 18) {
                plugin.getShopManager().openCategory(p, view.category(), view.page());
                return;
            }
            if (slot == 22) { p.closeInventory(); return; }

            ShopManager.ShopItem item = view.items().isEmpty() ? null : (ShopManager.ShopItem) view.items().get(0);
            if (item == null) return;

            int qty = switch (slot) {
                case 10 -> 1;
                case 11 -> 16;
                case 12 -> 32;
                case 14 -> 64;
                default -> 0;
            };
            if (qty <= 0) return;

            boolean success = plugin.getShopManager().buy(p, item, qty);
            p.sendMessage(success
                    ? "§a💚 §2§lEmerald SMP §8» §aPurchase completed."
                    : "§a💚 §2§lEmerald SMP §8» §cPurchase could not be completed.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void drag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (plugin.getShopManager().isSellInventory(p, e.getView().getTopInventory())) {
            int topSize = e.getView().getTopInventory().getSize();
            boolean invalid = e.getRawSlots().stream().anyMatch(s -> s >= topSize || (s >= 45 && s < 54));
            if (invalid) e.setCancelled(true);
            return;
        }

        String title = ChatColor.stripColor(e.getView().getTitle());
        if (title.contains("EMERALD SMP SHOP") || title.startsWith("💚 SHOP") || title.equals("💚 BUY ITEM"))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void close(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (plugin.getShopManager().isSellInventory(p, e.getInventory())) {
            // ESC, the close button, or any other normal inventory close is the
            // confirmation that sells the contents.
            plugin.getShopManager().closeSell(p);
        }
        plugin.getShopManager().view(p);
    }

    private String categoryAt(int slot) {
        int[] slots = {10, 11, 12, 13, 14, 15};
        String[] keys = {"blocks", "cpvp", "redstone", "food", "farm", "end"};
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) return keys[i];
        return null;
    }
}
