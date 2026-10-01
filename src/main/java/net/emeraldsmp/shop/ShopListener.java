package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;

public final class ShopListener implements Listener {
    private final EmeraldSMP plugin;
    public ShopListener(EmeraldSMP plugin) { this.plugin = plugin; }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!title.contains("EMERALD SMP SHOP") && !title.startsWith("💚 SHOP") && !title.startsWith("💚 BUY")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (title.contains("EMERALD SMP SHOP")) {
            if (slot == 31) { player.closeInventory(); return; }
            String key = categoryAt(slot);
            if (key != null) plugin.getShopManager().openCategory(player, key, 0);
            return;
        }
        var view = plugin.getShopManager().view(player);
        if (view == null) return;
        if (title.startsWith("💚 SHOP")) {
            if (slot == 45) { plugin.getShopManager().openMain(player); return; }
            if (slot == 53) { player.closeInventory(); return; }
            if (slot == 48) { plugin.getShopManager().openCategory(player, view.category(), view.page()-1); return; }
            if (slot == 50) { plugin.getShopManager().openCategory(player, view.category(), view.page()+1); return; }
            var item = plugin.getShopManager().itemFor(player, slot);
            if (item != null) plugin.getShopManager().openItem(player, item, view.category(), view.page());
            return;
        }
        if (title.startsWith("💚 BUY")) {
            if (slot == 18) { plugin.getShopManager().openCategory(player, view.category(), view.page()); return; }
            if (slot == 22) { player.closeInventory(); return; }
            var item = view.items().isEmpty() ? null : (ShopManager.ShopItem)view.items().get(0);
            if (item == null) return;
            int qty = switch(slot) { case 10 -> 1; case 11 -> 16; case 12 -> 32; case 14 -> 64; default -> 0; };
            if (qty <= 0) return;
            boolean ok = plugin.getShopManager().buy(player, item, qty);
            player.sendMessage(ok ? "§a💚 Purchase completed: §f"+qty+"x "+item.material().name()+"§a."
                    : "§cPurchase could not be completed. Check your balance and inventory space.");
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void drag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title.contains("EMERALD SMP SHOP") || title.startsWith("💚 SHOP") || title.startsWith("💚 BUY")) event.setCancelled(true);
    }

    private String categoryAt(int slot) {
        int[] slots={11,13,15,21,23}; String[] keys={"resources","blocks","redstone","cpvp","nether"};
        for(int i=0;i<slots.length;i++) if(slots[i]==slot) return keys[i];
        return null;
    }
}
