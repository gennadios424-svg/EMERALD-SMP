package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;

public final class ShopListener implements Listener{
    private final EmeraldSMP plugin;
    public ShopListener(EmeraldSMP plugin){this.plugin=plugin;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(plugin.getShopManager().isSellInventory(p,e.getView().getTopInventory())){if(e.getRawSlot()>=45)e.setCancelled(true);return;}
        String title=ChatColor.stripColor(e.getView().getTitle());
        if(!title.contains("EMERALD SMP SHOP")&&!title.startsWith("💚 SHOP")&&!title.equals("💚 BUY ITEM"))return;
        e.setCancelled(true);int slot=e.getRawSlot();if(slot<0||slot>=e.getView().getTopInventory().getSize())return;
        if(title.contains("EMERALD SMP SHOP")){if(slot==31){p.closeInventory();return;}String key=categoryAt(slot);if(key!=null)plugin.getShopManager().openCategory(p,key,0);return;}
        ShopManager.ShopView v=plugin.getShopManager().view(p);if(v==null)return;
        if(title.startsWith("💚 SHOP")){if(slot==45){plugin.getShopManager().openMain(p);return;}if(slot==53){p.closeInventory();return;}if(slot==48){plugin.getShopManager().openCategory(p,v.category(),v.page()-1);return;}if(slot==50){plugin.getShopManager().openCategory(p,v.category(),v.page()+1);return;}ShopManager.ShopItem item=plugin.getShopManager().itemFor(p,slot);if(item!=null)plugin.getShopManager().openItem(p,item,v.category(),v.page());return;}
        if(title.equals("💚 BUY ITEM")) {
            if(slot==18){ plugin.getShopManager().openCategory(p,v.category(),v.page()); return; }
            if(slot==22){ p.closeInventory(); return; }
            ShopManager.ShopItem item=v.items().isEmpty() ? null : (ShopManager.ShopItem)v.items().get(0);
            if(item==null)return;
            int qty;
            switch(slot){ case 10: qty=1; break; case 11: qty=16; break; case 12: qty=32; break; case 14: qty=64; break; default: qty=0; }
            if(qty<=0)return;
            boolean ok=plugin.getShopManager().buy(p,item,qty);
            p.sendMessage(ok ? "§a💚 Purchase completed." : "§cPurchase could not be completed.");
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void drag(InventoryDragEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(plugin.getShopManager().isSellInventory(p,e.getView().getTopInventory())){if(e.getRawSlots().stream().anyMatch(s->s>=e.getView().getTopInventory().getSize()||(s>=45&&s<54)))e.setCancelled(true);return;}
        String title=ChatColor.stripColor(e.getView().getTitle());if(title.contains("EMERALD SMP SHOP")||title.startsWith("💚 SHOP")||title.equals("💚 BUY ITEM"))e.setCancelled(true);
    }
    @EventHandler public void close(InventoryCloseEvent e){if(e.getPlayer() instanceof Player p&&plugin.getShopManager().isSellInventory(p,e.getInventory()))plugin.getShopManager().closeSell(p);}
    private String categoryAt(int slot){for(int i=0;i<CATEGORY_SLOTS.length;i++)if(CATEGORY_SLOTS[i]==slot)return CATEGORY_KEYS[i];return null;}
    private static final int[] CATEGORY_SLOTS={11,13,15,21,23};
    private static final String[] CATEGORY_KEYS={"farm","resources","redstone","utility","nether"};
}
