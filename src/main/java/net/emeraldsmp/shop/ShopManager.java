package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.worth.WorthEntry;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class ShopManager {
    private static final int[] CATEGORY_SLOTS={11,13,15,21,23};
    private static final String[] CATEGORY_KEYS={"farm","resources","redstone","utility","nether"};
    private static final String[] CATEGORY_NAMES={"§2🌿 Farming","§b💎 Resources","§c🔴 Redstone","§e🔧 Utility","§4🔥 Nether"};
    private static final Material[] CATEGORY_ICONS={Material.WHEAT,Material.IRON_INGOT,Material.REDSTONE,Material.HOPPER,Material.NETHERRACK};
    private static final Map<String,List<Material>> CURATED=Map.of(
        "farm",List.of(Material.WHEAT,Material.CARROT,Material.POTATO,Material.BEETROOT,Material.SUGAR_CANE,Material.CACTUS,Material.BAMBOO,Material.COCOA_BEANS,Material.NETHER_WART,Material.KELP),
        "resources",List.of(Material.COBBLESTONE,Material.STONE,Material.COAL,Material.IRON_INGOT,Material.COPPER_INGOT,Material.GOLD_INGOT,Material.REDSTONE,Material.LAPIS_LAZULI,Material.QUARTZ,Material.AMETHYST_SHARD),
        "redstone",List.of(Material.REDSTONE,Material.RAIL,Material.POWERED_RAIL,Material.PISTON,Material.STICKY_PISTON,Material.OBSERVER,Material.REPEATER,Material.COMPARATOR,Material.HOPPER),
        "utility",List.of(Material.GLASS,Material.SAND,Material.GRAVEL,Material.CLAY,Material.TORCH,Material.CHEST,Material.BARREL),
        "nether",List.of(Material.NETHERRACK,Material.SOUL_SAND,Material.SOUL_SOIL,Material.QUARTZ,Material.GLOWSTONE_DUST)
    );
    private final EmeraldSMP plugin;
    private final Map<UUID,ShopView> views=new HashMap<>();
    private final Map<UUID,Inventory> sellInventories=new HashMap<>();
    private final Set<UUID> sellProcessing=new HashSet<>();
    public ShopManager(EmeraldSMP plugin){this.plugin=plugin;}
    public void reload(){views.clear();}
    public void openMain(Player p){
        Inventory inv=plugin.getServer().createInventory(null,36,"§2§l💚 EMERALD SMP SHOP");
        for(int i=0;i<CATEGORY_SLOTS.length;i++)inv.setItem(CATEGORY_SLOTS[i],icon(CATEGORY_ICONS[i],CATEGORY_NAMES[i],List.of("§7Useful survival resources","§8No gear • No weapons • No mob drops")));
        inv.setItem(31,icon(Material.BARRIER,"§cClose",List.of()));views.put(p.getUniqueId(),new ShopView(false,null,0,List.of()));p.openInventory(inv);
    }
    public void openCategory(Player p,String key,int page){
        List<ShopItem> items=configuredItems(key);int pages=Math.max(1,(items.size()+44)/45),safe=Math.max(0,Math.min(page,pages-1));
        Inventory inv=plugin.getServer().createInventory(null,54,"§2§l💚 SHOP §8• §f"+categoryDisplay(key)+" §8• §f"+(safe+1)+"/"+pages);
        int from=safe*45,to=Math.min(from+45,items.size());for(int i=from;i<to;i++)inv.setItem(i-from,shopDisplay(items.get(i)));
        inv.setItem(45,icon(Material.ARROW,"§e⬅ Back",List.of()));inv.setItem(48,icon(Material.ARROW,"§a⬅ Previous",List.of()));
        inv.setItem(49,icon(Material.PAPER,"§fPage "+(safe+1)+"/"+pages,List.of("§7"+items.size()+" selected items")));
        inv.setItem(50,icon(Material.ARROW,"§aNext ➡",List.of()));inv.setItem(53,icon(Material.BARRIER,"§cClose",List.of()));
        views.put(p.getUniqueId(),new ShopView(false,key,safe,items));p.openInventory(inv);
    }
    public void openItem(Player p,ShopItem item,String key,int page){
        Inventory inv=plugin.getServer().createInventory(null,27,"§2§l💚 BUY ITEM");inv.setItem(13,shopDisplay(item));
        inv.setItem(10,buyButton(item,1));inv.setItem(11,buyButton(item,16));inv.setItem(12,buyButton(item,32));inv.setItem(14,buyButton(item,64));
        inv.setItem(18,icon(Material.ARROW,"§e⬅ Back",List.of()));inv.setItem(22,icon(Material.BARRIER,"§cClose",List.of()));
        views.put(p.getUniqueId(),new ShopView(false,key,page,List.of(item)));p.openInventory(inv);
    }
    public void openSell(Player p){
        returnSellItems(p);Inventory inv=plugin.getServer().createInventory(null,54,"§2§l💚 SELL ITEMS");
        for(int i=45;i<54;i++)inv.setItem(i,filler());inv.setItem(49,icon(Material.EMERALD,"§a§lAUTO SELL",List.of("§7Place sellable items above","§7Close the menu to sell","§7Unsellable items are returned")));
        sellInventories.put(p.getUniqueId(),inv);p.openInventory(inv);
    }
    public boolean isSellInventory(Player p,Inventory inv){return sellInventories.get(p.getUniqueId())==inv;}
    public void handleSellClick(Player p,int rawSlot){}
    public void sellContents(Player p){processSellOnClose(p);}
    public void closeSell(Player p){processSellOnClose(p);}
    private void processSellOnClose(Player p){
        UUID u=p.getUniqueId();if(sellProcessing.contains(u))return;Inventory inv=sellInventories.remove(u);if(inv==null)return;sellProcessing.add(u);
        try{List<ItemStack> unsellable=new ArrayList<>();long total=0;int stacks=0;
            for(int slot=0;slot<45;slot++){ItemStack s=inv.getItem(slot);if(s==null||s.getType().isAir())continue;long value=plugin.getWorthManager().sellValue(s.getType(),s.getAmount());inv.setItem(slot,null);
                if(value>0){total=Math.addExact(total,value);stacks++;}else unsellable.add(s.clone());}
            if(total>0){if(!plugin.getEconomyManager().deposit(u,total)){restoreItems(p,unsellable);p.sendMessage("§cSell transaction failed; items were returned.");return;}p.sendMessage("§a💚 Sold §f"+stacks+" §astack(s) for §f"+plugin.getEconomyManager().format(total)+"§a.");}
            restoreItems(p,unsellable);if(total==0&&!unsellable.isEmpty())p.sendMessage("§cThose items cannot be sold.");
        }catch(ArithmeticException ex){restoreSellInventory(p,inv);p.sendMessage("§cSell value was too large; nothing was sold.");}finally{sellProcessing.remove(u);}
    }
    private void restoreSellInventory(Player p,Inventory inv){List<ItemStack> all=new ArrayList<>();for(int i=0;i<45;i++){ItemStack s=inv.getItem(i);if(s!=null&&!s.getType().isAir())all.add(s.clone());inv.setItem(i,null);}restoreItems(p,all);}
    public void returnSellItems(Player p){Inventory inv=sellInventories.remove(p.getUniqueId());if(inv==null)return;List<ItemStack> all=new ArrayList<>();for(int i=0;i<45;i++){ItemStack s=inv.getItem(i);if(s!=null&&!s.getType().isAir())all.add(s.clone());inv.setItem(i,null);}restoreItems(p,all);}
    private void restoreItems(Player p,List<ItemStack> items){for(ItemStack s:items){Map<Integer,ItemStack> left=p.getInventory().addItem(s);for(ItemStack x:left.values())p.getWorld().dropItemNaturally(p.getLocation(),x);}}
    public ShopView view(Player p){return views.get(p.getUniqueId());}
    public ShopItem itemFor(Player p,int slot){ShopView v=views.get(p.getUniqueId());if(v==null||slot<0||slot>=45)return null;int idx=v.page()*45+slot;return idx>=0&&idx<v.items().size()?(ShopItem)v.items().get(idx):null;}
    public boolean buy(Player p,ShopItem item,int qty){
        if(item==null||qty<=0||qty>64)return false;long total;try{total=Math.multiplyExact(item.buyPrice(),qty);}catch(Exception ex){return false;}
        if(plugin.getEconomyManager().getBalance(p.getUniqueId())<total||!hasSpace(p,item.material(),qty))return false;
        if(!plugin.getEconomyManager().withdraw(p.getUniqueId(),total))return false;Map<Integer,ItemStack> left=p.getInventory().addItem(new ItemStack(item.material(),qty));
        if(!left.isEmpty()){plugin.getEconomyManager().deposit(p.getUniqueId(),total);return false;}return true;
    }
    private List<ShopItem> configuredItems(String key){
        List<Material> mats=CURATED.getOrDefault(key,List.of());List<ShopItem> out=new ArrayList<>();
        for(Material m:mats){WorthEntry w=plugin.getWorthManager().get(m);if(w==null||!w.enabled())continue;long buy=plugin.getWorthManager().buyValue(m,1);if(buy<=w.worth())continue;
            out.add(new ShopItem(m.name().toLowerCase(Locale.ROOT),m,"§f"+pretty(m),buy,List.of("§7Sell value: §a"+plugin.getEconomyManager().format(w.worth())+" §7/ item")));}return out;
    }
    private String categoryDisplay(String key){for(int i=0;i<CATEGORY_KEYS.length;i++)if(CATEGORY_KEYS[i].equals(key))return ChatColor.stripColor(CATEGORY_NAMES[i]);return key;}
    private ItemStack shopDisplay(ShopItem item){ItemStack s=new ItemStack(item.material());ItemMeta m=s.getItemMeta();if(m!=null){m.setDisplayName(item.displayName());List<String> l=new ArrayList<>();l.add("§aBuy: §f"+plugin.getEconomyManager().format(item.buyPrice())+" §7/ item");l.addAll(item.lore());l.add("§8Click for quantities");m.setLore(l);s.setItemMeta(m);}return s;}
    private ItemStack buyButton(ShopItem item,int qty){long total;try{total=Math.multiplyExact(item.buyPrice(),qty);}catch(Exception e){total=Long.MAX_VALUE;}return icon(Material.PAPER,"§eBuy "+qty,List.of("§7Price: §a"+plugin.getEconomyManager().format(total)));}
    private ItemStack filler(){return icon(Material.GRAY_STAINED_GLASS_PANE," ",List.of());}
    private ItemStack icon(Material m,String n,List<String> l){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();if(meta!=null){meta.setDisplayName(n);meta.setLore(l);i.setItemMeta(meta);}return i;}
    private boolean hasSpace(Player p,Material m,int qty){int cap=0;for(ItemStack s:p.getInventory().getStorageContents()){if(s==null||s.getType().isAir())cap+=m.getMaxStackSize();else if(s.getType()==m)cap+=m.getMaxStackSize()-s.getAmount();if(cap>=qty)return true;}return false;}
    private String pretty(Material m){String s=m.name().toLowerCase(Locale.ROOT).replace('_',' ');StringBuilder b=new StringBuilder();for(String w:s.split(" "))if(!w.isEmpty())b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');return b.toString().trim();}
    public record ShopView(boolean sellMode,String category,int page,List<?> items){}
    public record ShopItem(String key,Material material,String displayName,long buyPrice,List<String> lore){}
}
