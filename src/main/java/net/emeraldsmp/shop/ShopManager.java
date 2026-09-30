package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class ShopManager {
    public static final String MAIN = "shop_main";
    public static final String CATEGORY = "shop_category";
    public static final String ITEM = "shop_item";
    private final EmeraldSMP plugin;
    private final Map<String, String> titles = new LinkedHashMap<>();

    public ShopManager(EmeraldSMP plugin) { this.plugin = plugin; reload(); }
    public void reload() { titles.clear(); ConfigurationSection s=plugin.getConfig().getConfigurationSection("shop.categories"); if(s!=null) for(String k:s.getKeys(false)) titles.put(k,s.getString(k+".display-name",k)); }
    public void openMain(Player p) {
        Inventory inv=plugin.getServer().createInventory(null,27,"§2§l💚 EMERALD SMP SHOP");
        ConfigurationSection s=plugin.getConfig().getConfigurationSection("shop.categories");
        if(s!=null) for(String k:s.getKeys(false)){ if(!s.getBoolean(k+".enabled",true)) continue; int slot=s.getInt(k+".slot",-1); if(slot<0||slot>=27) continue; inv.setItem(slot,icon(s.getString(k+".icon","CHEST"),s.getString(k+".display-name",k),s.getStringList(k+".lore"))); }
        inv.setItem(22,icon("BARRIER","§cClose",List.of())); p.openInventory(inv);
    }
    public void openCategory(Player p,String cat) {
        ConfigurationSection c=plugin.getConfig().getConfigurationSection("shop.categories."+cat); if(c==null) return;
        Inventory inv=plugin.getServer().createInventory(null,54,"§2§l"+c.getString("display-name",cat));
        ConfigurationSection items=c.getConfigurationSection("items");
        if(items!=null) for(String id:items.getKeys(false)){String base="shop.categories."+cat+".items."+id; if(!plugin.getConfig().getBoolean(base+".enabled",true))continue; int slot=plugin.getConfig().getInt(base+".slot",-1); if(slot<0||slot>=54)continue; Material m=Material.matchMaterial(plugin.getConfig().getString(base+".material","STONE")); if(m==null)continue; ItemStack it=new ItemStack(m); ItemMeta meta=it.getItemMeta(); meta.setDisplayName(plugin.getConfig().getString(base+".display-name",id)); List<String> lore=new ArrayList<>(plugin.getConfig().getStringList(base+".lore")); lore.add("§aBuy: §f$"+plugin.getConfig().getLong(base+".buy",0)); lore.add("§cSell: §f$"+plugin.getConfig().getLong(base+".sell",0)); lore.add("§7Click to view options"); meta.setLore(lore); it.setItemMeta(meta); inv.setItem(slot,it); }
        inv.setItem(45,icon("ARROW","§eBack",List.of())); inv.setItem(49,icon("BARRIER","§cClose",List.of())); p.openInventory(inv);
    }
    public void openItem(Player p,String cat,String id) {
        String b="shop.categories."+cat+".items."+id; Material m=Material.matchMaterial(plugin.getConfig().getString(b+".material","STONE")); if(m==null)return;
        Inventory inv=plugin.getServer().createInventory(null,27,"§2§lPurchase");
        ItemStack item=new ItemStack(m); ItemMeta meta=item.getItemMeta(); meta.setDisplayName(plugin.getConfig().getString(b+".display-name",id)); meta.setLore(List.of("§aBuy: §f$"+plugin.getConfig().getLong(b+".buy",0),"§cSell: §f$"+plugin.getConfig().getLong(b+".sell",0))); item.setItemMeta(meta); inv.setItem(13,item);
        int[] q={1,16,32,64}; int[] slots={10,11,12,14}; for(int i=0;i<q.length;i++) inv.setItem(slots[i],icon("PAPER","§eBuy "+q[i],List.of("§7Click to buy")));
        inv.setItem(16,icon("GOLD_INGOT","§aSell 1",List.of("§7Click to sell one"))); inv.setItem(18,icon("ARROW","§eBack",List.of())); inv.setItem(22,icon("BARRIER","§cClose",List.of())); p.openInventory(inv);
    }
    public boolean buy(Player p,String cat,String id,int qty){String b="shop.categories."+cat+".items."+id; long price=plugin.getConfig().getLong(b+".buy",-1); Material m=Material.matchMaterial(plugin.getConfig().getString(b+".material","STONE")); if(price<0||m==null)return false; long total=Math.multiplyExact(price,qty); if(p.getInventory().firstEmpty()==-1 && !hasSpace(p,m,qty))return false; if(plugin.getEconomyManager().getBalance(p.getUniqueId())<total)return false; if(!plugin.getEconomyManager().withdraw(p.getUniqueId(),total))return false; HashMap<Integer,ItemStack> left=p.getInventory().addItem(new ItemStack(m,qty)); if(!left.isEmpty()){plugin.getEconomyManager().deposit(p.getUniqueId(),total); return false;} return true;}
    public boolean sell(Player p,String cat,String id,int qty){String b="shop.categories."+cat+".items."+id; long price=plugin.getConfig().getLong(b+".sell",-1); Material m=Material.matchMaterial(plugin.getConfig().getString(b+".material","STONE")); if(price<0||m==null)return false; if(!hasSpaceForRemoval(p,m,qty))return false; long total=Math.multiplyExact(price,qty); remove(p,m,qty); if(!plugin.getEconomyManager().deposit(p.getUniqueId(),total)){p.getInventory().addItem(new ItemStack(m,qty));return false;} return true;}
    private boolean hasSpace(Player p,Material m,int qty){int cap=0;for(ItemStack i:p.getInventory().getStorageContents()){if(i==null)cap+=m.getMaxStackSize();else if(i.getType()==m)cap+=m.getMaxStackSize()-i.getAmount();}return cap>=qty;}
    private boolean hasSpaceForRemoval(Player p,Material m,int qty){int n=0;for(ItemStack i:p.getInventory().getStorageContents())if(i!=null&&i.getType()==m)n+=i.getAmount();return n>=qty;}
    private void remove(Player p,Material m,int qty){for(int i=0;i<p.getInventory().getSize()&&qty>0;i++){ItemStack s=p.getInventory().getItem(i);if(s==null||s.getType()!=m)continue;int take=Math.min(qty,s.getAmount());s.setAmount(s.getAmount()-take);qty-=take;}}
    private ItemStack icon(String material,String name,List<String> lore){Material m=Material.matchMaterial(material);if(m==null)m=Material.STONE;ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.setDisplayName(name);meta.setLore(lore);i.setItemMeta(meta);return i;}
}
