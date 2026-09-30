package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.worth.WorthCategory;
import net.emeraldsmp.worth.WorthEntry;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class ShopListener implements Listener {
    private final EmeraldSMP plugin;

    public ShopListener(EmeraldSMP plugin) { this.plugin = plugin; }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (!title.contains("EMERALD SMP SHOP") && !title.startsWith("💚 SHOP") && !title.startsWith("💚 SELL")
                && !title.equals("💚 BUY ITEM") && !title.equals("💚 SELL ITEM")) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getView().getTopInventory().getSize()) return;

        if (title.contains("EMERALD SMP SHOP")) {
            if (slot == 22) { p.closeInventory(); return; }
            WorthCategory category = categoryAt(slot);
            if (category != null) plugin.getShopManager().openCategory(p, category, 0, false);
            return;
        }

        ShopManager.ShopView view = plugin.getShopManager().view(p);
        if (view == null) return;

        if (title.startsWith("💚 SHOP") || title.startsWith("💚 SELL")) {
            if (slot == 45) { plugin.getShopManager().openMain(p); return; }
            if (slot == 53) { p.closeInventory(); return; }
            if (slot == 48 && view.page() > 0) {
                plugin.getShopManager().openCategory(p, view.category(), view.page() - 1, view.sellMode());
                return;
            }
            if (slot == 50 && view.items() != null && (view.page() + 1) * 45 < view.items().size()) {
                plugin.getShopManager().openCategory(p, view.category(), view.page() + 1, view.sellMode());
                return;
            }
            WorthEntry entry = plugin.getShopManager().entryFor(p, slot);
            if (entry != null) plugin.getShopManager().openItem(p, entry, view.sellMode());
            return;
        }

        if (title.equals("💚 BUY ITEM") || title.equals("💚 SELL ITEM")) {
            if (slot == 18) {
                plugin.getShopManager().openCategory(p, view.category(), view.page(), view.sellMode());
                return;
            }
            if (slot == 22) { p.closeInventory(); return; }
            WorthEntry entry = view.items() == null || view.items().isEmpty() ? null : view.items().get(0);
            if (entry == null) return;

            int qty = switch (slot) {
                case 10 -> 1;
                case 11 -> 16;
                case 12 -> 32;
                case 14 -> 64;
                default -> 0;
            };
            boolean success = false;
            if (qty > 0) {
                success = view.sellMode()
                        ? plugin.getShopManager().sell(p, entry, qty)
                        : plugin.getShopManager().buy(p, entry, qty);
            } else if (view.sellMode() && slot == 16) {
                int amount = plugin.getShopManager().count(p, entry.material());
                success = amount > 0 && plugin.getShopManager().sell(p, entry, amount);
            }
            p.sendMessage(success
                    ? "§a💚 §2§lEmerald SMP §8» §fTransaction completed."
                    : "§a💚 §2§lEmerald SMP §8» §cTransaction could not be completed.");
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (title.contains("EMERALD SMP SHOP") || title.startsWith("💚 SHOP")
                || title.startsWith("💚 SELL") || title.equals("💚 BUY ITEM") || title.equals("💚 SELL ITEM")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        plugin.getShopManager().clear((Player) e.getPlayer());
    }

    private WorthCategory categoryAt(int slot) {
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25};
        WorthCategory[] categories = WorthCategory.values();
        for (int i = 0; i < slots.length && i < categories.length; i++)
            if (slots[i] == slot) return categories[i];
        return null;
    }
}
