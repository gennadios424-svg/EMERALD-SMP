package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.worth.WorthEntry;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class ShopManager {
    private static final int[] CATEGORY_SLOTS={10,12,14,16,21,23};
    private static final String[] CATEGORY_KEYS={"resources","blocks","redstone","cpvp","nether","spawners"};
    private static final String[] CATEGORY_NAMES={"§a§l🌾 RESOURCES","§2§l🧱 BLOCKS","§c§l🔴 REDSTONE","§5§l⚔ CPvP","§4§l🔥 NETHER","§a§l🧟 SPAWNERS"};
    private static final Material[] CATEGORY_ICONS={Material.WHEAT,Material.BRICKS,Material.REDSTONE,Material.END_CRYSTAL,Material.NETHER_BRICKS,Material.SPAWNER};
    private static final Map<String,List<Material>> CURATED=Map.ofEntries(
        Map.entry("resources",List.of(Material.KELP,Material.WHEAT,Material.CARROT,Material.POTATO,Material.BAMBOO,Material.CACTUS,Material.COCOA_BEANS,Material.SUGAR_CANE,Material.NETHER_WART)),
        Map.entry("blocks",List.of(Material.STONE,Material.COBBLESTONE,Material.MOSSY_COBBLESTONE,Material.SMOOTH_STONE,Material.DEEPSLATE,Material.COBBLED_DEEPSLATE,Material.POLISHED_DEEPSLATE,Material.BRICKS,Material.STONE_BRICKS,Material.OAK_PLANKS,Material.SPRUCE_PLANKS,Material.BIRCH_PLANKS,Material.JUNGLE_PLANKS,Material.ACACIA_PLANKS,Material.DARK_OAK_PLANKS,Material.MANGROVE_PLANKS,Material.CHERRY_PLANKS,Material.BAMBOO_PLANKS,Material.WHITE_WOOL,Material.BLACK_WOOL,Material.GRAY_WOOL,Material.LIGHT_GRAY_WOOL,Material.RED_WOOL,Material.ORANGE_WOOL,Material.YELLOW_WOOL,Material.LIME_WOOL,Material.GREEN_WOOL,Material.CYAN_WOOL,Material.LIGHT_BLUE_WOOL,Material.BLUE_WOOL,Material.PURPLE_WOOL,Material.MAGENTA_WOOL,Material.PINK_WOOL,Material.BROWN_WOOL,Material.WHITE_CONCRETE,Material.BLACK_CONCRETE,Material.GRAY_CONCRETE,Material.LIGHT_GRAY_CONCRETE,Material.RED_CONCRETE,Material.ORANGE_CONCRETE,Material.YELLOW_CONCRETE,Material.LIME_CONCRETE,Material.GREEN_CONCRETE,Material.CYAN_CONCRETE,Material.LIGHT_BLUE_CONCRETE,Material.BLUE_CONCRETE,Material.PURPLE_CONCRETE,Material.MAGENTA_CONCRETE,Material.PINK_CONCRETE,Material.BROWN_CONCRETE,Material.GLASS,Material.GLASS_PANE,Material.TERRACOTTA,Material.WHITE_TERRACOTTA,Material.BLACK_TERRACOTTA,Material.SMOOTH_QUARTZ,Material.QUARTZ_BLOCK,Material.GLOWSTONE,Material.SEA_LANTERN)),
        Map.entry("redstone",List.of(Material.REDSTONE,Material.REDSTONE_TORCH,Material.REPEATER,Material.COMPARATOR,Material.PISTON,Material.STICKY_PISTON,Material.OBSERVER,Material.DISPENSER,Material.DROPPER,Material.HOPPER,Material.TARGET,Material.LEVER,Material.STONE_BUTTON,Material.TRIPWIRE_HOOK,Material.DAYLIGHT_DETECTOR,Material.NOTE_BLOCK)),
        Map.entry("cpvp",List.of(Material.OBSIDIAN,Material.CRYING_OBSIDIAN,Material.END_CRYSTAL,Material.RESPAWN_ANCHOR,Material.GLOWSTONE,Material.TNT,Material.ENDER_PEARL,Material.TOTEM_OF_UNDYING,Material.GOLDEN_APPLE)),
        Map.entry("nether",List.of(Material.NETHERRACK,Material.SOUL_SAND,Material.SOUL_SOIL,Material.BASALT,Material.BLACKSTONE,Material.NETHER_BRICKS,Material.NETHER_BRICK_FENCE,Material.GLOWSTONE))
    );
    private final EmeraldSMP plugin; private final Map<UUID,ShopView> views=new HashMap<>(); private final Set<UUID> buyProcessing=new HashSet<>();
    public ShopManager(EmeraldSMP plugin){this.plugin=plugin;} public void reload(){views.clear();}

    public void openMain(Player p){
        Inventory inv=plugin.getServer().createInventory(new ShopHolder("MAIN",null,0),36,"§2§l💚 EMERALD SMP SHOP");
        for(int i=0;i<CATEGORY_SLOTS.length;i++)inv.setItem(CATEGORY_SLOTS[i],icon(CATEGORY_ICONS[i],CATEGORY_NAMES[i],List.of("§7Curated survival essentials","§8Click to browse")));
        inv.setItem(4,icon(Material.EMERALD,"§a§l💚 EMERALD SMP",List.of("§7A compact survival economy shop","§8Resources • Blocks • Redstone • CPvP • Nether")));
        inv.setItem(31,icon(Material.BARRIER,"§c§l✕ CLOSE",List.of()));
        views.put(p.getUniqueId(),new ShopView(null,0,List.of()));p.openInventory(inv);
    }
    public void openCategory(Player p,String key,int page){
        if(!CATEGORY_INDEX.containsKey(key))return;
        List<ShopItem> items=configuredItems(key);int pages=Math.max(1,(items.size()+44)/45);int safe=Math.max(0,Math.min(page,pages-1));
        Inventory inv=plugin.getServer().createInventory(new ShopHolder("CATEGORY",key,safe),54,"§2§l💚 SHOP §8• §f"+categoryDisplay(key)+" §8• §f"+(safe+1)+"/"+pages);
        int from=safe*45,to=Math.min(from+45,items.size());for(int i=from;i<to;i++)inv.setItem(i-from,shopDisplay(items.get(i)));
        inv.setItem(45,icon(Material.ARROW,"§e§l⬅ BACK",List.of("§7Return to shop categories")));
        inv.setItem(48,icon(Material.ARROW,"§a⬅ PREVIOUS",List.of("§7Previous page")));
        inv.setItem(49,icon(Material.EMERALD,"§a§l"+categoryDisplay(key),List.of("§7Page §f"+(safe+1)+"§7/§f"+pages,"§7"+items.size()+" curated items")));
        inv.setItem(50,icon(Material.ARROW,"§aNEXT ➡",List.of("§7Next page")));inv.setItem(53,icon(Material.BARRIER,"§c§l✕ CLOSE",List.of()));
        views.put(p.getUniqueId(),new ShopView(key,safe,items));p.openInventory(inv);
    }
    public void openItem(Player p,ShopItem item,String key,int page){
        Inventory inv=plugin.getServer().createInventory(new ShopHolder("BUY",key,page),27,"§2§l💚 BUY §8• §f"+pretty(item.material()));
        inv.setItem(13,shopDisplay(item));inv.setItem(10,buyButton(item,1));inv.setItem(11,buyButton(item,16));inv.setItem(12,buyButton(item,32));inv.setItem(14,buyButton(item,64));
        inv.setItem(16,icon(Material.EMERALD,"§a§l💰 BUY",List.of("§7Select an amount above","§8Purchase only if you can afford it")));
        inv.setItem(18,icon(Material.ARROW,"§e§l⬅ BACK",List.of("§7Return to "+categoryDisplay(key))));inv.setItem(22,icon(Material.BARRIER,"§c§l✕ CLOSE",List.of()));
        views.put(p.getUniqueId(),new ShopView(key,page,List.of(item)));p.openInventory(inv);
    }
    public ShopView view(Player p){return views.get(p.getUniqueId());}
    public ShopItem itemFor(Player p,int slot){ShopView v=views.get(p.getUniqueId());if(v==null||slot<0||slot>=45)return null;int idx=v.page()*45+slot;return idx>=0&&idx<v.items().size()?(ShopItem)v.items().get(idx):null;}
    public boolean buy(Player p,ShopItem item,int qty){
        UUID uuid=p.getUniqueId();if(!buyProcessing.add(uuid))return false;
        try{if(item==null||qty<=0||qty>64)return false;long total;try{total=Math.multiplyExact(item.buyPrice(),qty);}catch(ArithmeticException ex){return false;}if(total<=0||!hasSpace(p,item.material(),qty))return false;
            if(item.shards()){if(plugin.getPlayerDataManager().getEmeraldShards(uuid)<total||!plugin.getPlayerDataManager().withdrawEmeraldShards(uuid,total))return false;}
            else{if(plugin.getEconomyManager().getBalance(uuid)<total||!plugin.getEconomyManager().withdraw(uuid,total))return false;}
            ItemStack product=item.specialSpawnerType()==null?new ItemStack(item.material(),qty):plugin.getSpawnerManager().createItem(item.specialSpawnerType(),1);if(item.specialSpawnerType()!=null)product.setAmount(Math.min(qty,64));
            Map<Integer,ItemStack> left=p.getInventory().addItem(product);if(!left.isEmpty())for(ItemStack leftover:left.values())p.getWorld().dropItemNaturally(p.getLocation(),leftover);return true;
        }finally{buyProcessing.remove(uuid);}
    }
    private List<ShopItem> configuredItems(String key){
        if(key.equals("spawners")){List<ShopItem> out=new ArrayList<>();for(String type:List.of("skeleton","zombie","spider","creeper"))out.add(new ShopItem(type+"-spawner",Material.SPAWNER,"§a§l💚 "+Character.toUpperCase(type.charAt(0))+type.substring(1)+" Spawner",1500L,List.of("§7Price: §a1,500 Emerald Shards","§7Uses the existing Emerald SMP spawner system","§8Click for purchase amounts"),true,type));return out;}
        List<Material> mats=CURATED.getOrDefault(key,List.of());List<ShopItem> out=new ArrayList<>();
        for(Material m:mats){WorthEntry w=plugin.getWorthManager().get(m);if(m!=Material.END_CRYSTAL&&(w==null||!w.enabled()))continue;long sellWorth=w==null?0L:w.worth();long buy=m==Material.END_CRYSTAL?500L:plugin.getWorthManager().buyValue(m,1);if(buy<=sellWorth)continue;out.add(new ShopItem(m.name().toLowerCase(Locale.ROOT),m,roleStyledName(m),buy,List.of("§7Buy: §a"+plugin.getEconomyManager().format(buy)+" §7/ item","§7Sell: §a"+plugin.getEconomyManager().format(sellWorth)+" §7/ item","§8Click for purchase amounts"),false,null));}
        return out;
    }
    private static final Map<String,Integer> CATEGORY_INDEX=Map.of("resources",0,"blocks",1,"redstone",2,"cpvp",3,"nether",4,"spawners",5);
    private String categoryDisplay(String key){for(int i=0;i<CATEGORY_KEYS.length;i++)if(CATEGORY_KEYS[i].equals(key))return ChatColor.stripColor(CATEGORY_NAMES[i]);return key;}
    private ItemStack shopDisplay(ShopItem item){return icon(item.material(),item.displayName(),item.lore());}
    private ItemStack buyButton(ShopItem item,int qty){long total;try{total=Math.multiplyExact(item.buyPrice(),qty);}catch(ArithmeticException ex){total=Long.MAX_VALUE;}return icon(Material.EMERALD,"§a§lBUY ×"+qty,List.of("§7Price: §f"+(item.shards()?String.format(Locale.US,"%,d Emerald Shards",total):plugin.getEconomyManager().format(total)),"§8Click to purchase"));}
    private ItemStack icon(Material m,String n,List<String> l){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();if(meta!=null){meta.setDisplayName(n);meta.setLore(l);i.setItemMeta(meta);}return i;}
    private boolean hasSpace(Player p,Material m,int qty){int remaining=qty;for(ItemStack s:p.getInventory().getStorageContents()){if(remaining<=0)return true;if(s==null||s.getType().isAir())remaining-=m.getMaxStackSize();else if(s.getType()==m)remaining-=Math.max(0,m.getMaxStackSize()-s.getAmount());}return remaining<=0;}
    private String roleStyledName(Material m){return "§a§l"+pretty(m);}
    private String pretty(Material m){String s=m.name().toLowerCase(Locale.ROOT).replace('_',' ');StringBuilder b=new StringBuilder();for(String w:s.split(" "))if(!w.isEmpty())b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');return b.toString().trim();}
    public record ShopView(String category,int page,List<?> items){}
    public record ShopItem(String key,Material material,String displayName,long buyPrice,List<String> lore,boolean shards,String specialSpawnerType){}
    public static final class ShopHolder implements InventoryHolder {
        private final String type,category; private final int page;
        public ShopHolder(String type,String category,int page){this.type=type;this.category=category;this.page=page;}
        public String type(){return type;} public String category(){return category;} public int page(){return page;}
        @Override public Inventory getInventory(){return null;}
    }
}
