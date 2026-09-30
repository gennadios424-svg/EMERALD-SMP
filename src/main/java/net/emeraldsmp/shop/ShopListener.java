package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;

public final class ShopListener implements Listener {
    private final EmeraldSMP plugin;
    public ShopListener(EmeraldSMP plugin){this.plugin=plugin;}
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String title=ChatColor.stripColor(e.getView().getTitle());
        if(!title.contains("EMERALD SMP SHOP")&&!title.contains("Purchase")&&!isCategory(title))return;
        e.setCancelled(true);
        if(e.getRawSlot()<0||e.getRawSlot()>=e.getView().getTopInventory().getSize())return;
        if(title.contains("EMERALD SMP SHOP")){main(p,e.getRawSlot());return;}
        if(title.equals("Purchase")){purchase(p,e.getRawSlot());return;}
        category(p,title,e.getRawSlot());
    }
    @EventHandler public void drag(InventoryDragEvent e){String t=ChatColor.stripColor(e.getView().getTitle());if(t.contains("EMERALD SMP SHOP")||t.contains("Purchase")||isCategory(t))e.setCancelled(true);}
    private boolean isCategory(String t){ConfigurationSection s=plugin.getConfig().getConfigurationSection("shop.categories");if(s==null)return false;for(String k:s.getKeys(false))if(ChatColor.stripColor(s.getString(k+".display-name",k)).equals(t))return true;return false;}
    private void main(Player p,int slot){ConfigurationSection s=plugin.getConfig().getConfigurationSection("shop.categories");if(s==null)return;for(String k:s.getKeys(false))if(s.getBoolean(k+".enabled",true)&&s.getInt(k+".slot",-1)==slot){plugin.getShopManager().openCategory(p,k);return;}if(slot==22)p.closeInventory();}
    private void category(Player p,String title,int slot){if(slot==45){plugin.getShopManager().openMain(p);return;}if(slot==49){p.closeInventory();return;}ConfigurationSection s=plugin.getConfig().getConfigurationSection("shop.categories");if(s==null)return;for(String k:s.getKeys(false)){if(!ChatColor.stripColor(s.getString(k+".display-name",k)).equals(title))continue;ConfigurationSection items=s.getConfigurationSection(k+".items");if(items==null)return;for(String id:items.getKeys(false))if(items.getBoolean(id+".enabled",true)&&items.getInt(id+".slot",-1)==slot){plugin.getShopManager().openItem(p,k,id);return;}}}
    private void purchase(Player p,int slot){if(slot==18){p.closeInventory();return;}if(slot==22){p.closeInventory();return;}ItemStack center=p.getOpenInventory().getTopInventory().getItem(13);if(center==null)return;ConfigurationSection cats=plugin.getConfig().getConfigurationSection("shop.categories");if(cats==null)return;String cat=null,id=null;for(String c:cats.getKeys(false)){ConfigurationSection items=cats.getConfigurationSection(c+".items");if(items==null)continue;for(String x:items.getKeys(false)){Material m=Material.matchMaterial(items.getString(x+".material","STONE"));if(items.getBoolean(x+".enabled",true)&&m==center.getType()){cat=c;id=x;break;}}if(id!=null)break;}if(id==null)return;int q=switch(slot){case 10->1;case 11->16;case 12->32;case 14->64;case 16->1;default->0;};if(q==0)return;boolean sell=slot==16;boolean ok=sell?plugin.getShopManager().sell(p,cat,id,q):plugin.getShopManager().buy(p,cat,id,q);p.sendMessage(ok?"§a💚 §2§lEmerald SMP §8» §fTransaction completed.":"§a💚 §2§lEmerald SMP §8» §cTransaction could not be completed.");}
}
