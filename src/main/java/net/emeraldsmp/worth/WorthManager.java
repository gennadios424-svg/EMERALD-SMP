package net.emeraldsmp.worth;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

public final class WorthManager {
    private static final Set<Material> EXCLUDED = EnumSet.of(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR, Material.BEDROCK,
            Material.BARRIER, Material.LIGHT, Material.DEBUG_STICK, Material.KNOWLEDGE_BOOK,
            Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART, Material.JIGSAW, Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID, Material.END_PORTAL, Material.END_GATEWAY,
            Material.NETHER_PORTAL, Material.FIRE, Material.SOUL_FIRE,
            Material.SPAWNER, Material.END_PORTAL_FRAME, Material.REINFORCED_DEEPSLATE,
            Material.BUDDING_AMETHYST, Material.TRIAL_SPAWNER, Material.VAULT
    );
    private final EmeraldSMP plugin;
    private final File file;
    private final Map<Material, WorthEntry> entries = new EnumMap<>(Material.class);
    private final Map<UUID, ViewState> views = new HashMap<>();

    public WorthManager(EmeraldSMP plugin) { this.plugin=plugin; this.file=new File(plugin.getDataFolder(),"worth.yml"); }

    public void load() {
        if(!plugin.getDataFolder().exists()&&!plugin.getDataFolder().mkdirs()) throw new IllegalStateException("Could not create plugin data folder.");
        if(!file.exists()) generateDefaultFile();
        if(!reload()) throw new IllegalStateException("Initial worth data could not be loaded.");
    }

    public synchronized boolean reload() {
        try {
            if(!file.exists()) generateDefaultFile();
            YamlConfiguration next=YamlConfiguration.loadConfiguration(file);
            ConfigurationSection worth=next.getConfigurationSection("worth");
            if(worth==null) throw new IllegalArgumentException("Missing 'worth' section.");
            Map<Material,WorthEntry> parsed=new EnumMap<>(Material.class);
            for(String key:worth.getKeys(false)){
                Material m=Material.matchMaterial(key);
                if(m==null||!isSupported(m)){ plugin.getLogger().warning("[Worth] Ignoring invalid/unsupported material: "+key); continue; }
                Object raw=worth.get(key); long value;
                try { value=new BigDecimal(String.valueOf(raw)).longValueExact(); }
                catch(Exception ex){ plugin.getLogger().warning("[Worth] Invalid price for "+key+": "+raw+" (previous value kept)"); continue; }
                if(value<0){plugin.getLogger().warning("[Worth] Negative price for "+key+": "+value+" (previous value kept)");continue;}
                int stackSize=m.getMaxStackSize();
                if(stackSize>0 && value>Long.MAX_VALUE/stackSize){
                    plugin.getLogger().warning("[Worth] Price for "+key+" is too large for safe stack multiplication: "+value+" (previous value kept)");
                    continue;
                }
                String cn=next.getString("categories."+key,defaultCategory(m).name());
                WorthCategory cat; try{cat=WorthCategory.valueOf(cn.toUpperCase(Locale.ROOT));}catch(Exception ex){cat=defaultCategory(m);}
                parsed.put(m,new WorthEntry(m,value,cat,next.getBoolean("enabled."+key,true)));
            }
            if(parsed.isEmpty()) throw new IllegalArgumentException("No valid worth entries found.");
            for(Material m:Material.values()) if(isSupported(m)&&!parsed.containsKey(m)&&entries.containsKey(m)) parsed.put(m,entries.get(m));
            entries.clear(); entries.putAll(parsed);
            return true;
        } catch(Exception ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"[Worth] Reload failed; previous valid data was kept.",ex);
            return false;
        }
    }

    public synchronized Collection<WorthEntry> all(){return entries.values().stream().filter(WorthEntry::enabled).toList();}
    public synchronized WorthEntry get(Material m){return entries.get(m);}
    public synchronized long value(Material m,int amount){WorthEntry e=entries.get(m);if(e==null||!e.enabled()||amount<0)return 0;return Math.multiplyExact(e.worth(),(long)amount);}

    public void openBrowser(Player p,String query,int page,WorthCategoryFilter filter,WorthSort sort){
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        List<WorthEntry> list=all().stream()
                .filter(e->filter.category()==null||e.category()==filter.category())
                .filter(e->q.isEmpty()||e.material().name().toLowerCase(Locale.ROOT).contains(q)||pretty(e.material()).toLowerCase(Locale.ROOT).contains(q))
                .sorted(comparator(sort)).toList();
        if(list.isEmpty()){
            plugin.getMessageService().send(p,"&cNo items found for &f\""+(query==null?"":query)+"&c\".");
            return;
        }
        int pages=(list.size()+44)/45; int safe=Math.max(0,Math.min(page,pages-1));
        views.put(p.getUniqueId(),new ViewState(q,safe,filter,sort,list));
        Inventory inv=plugin.getServer().createInventory(null,54,"§2§l💚 WORTH §8• §fPage "+(safe+1)+"/"+pages);
        int from=safe*45,to=Math.min(from+45,list.size());
        for(int i=from;i<to;i++) inv.setItem(i,display(list.get(i-from),false));
        inv.setItem(45,button("ARROW","§a⬅ Previous",List.of("§7Go to the previous page")));
        inv.setItem(46,button("COMPASS","§b🏠 All Items",List.of("§7Clear category filter")));
        inv.setItem(47,button("NAME_TAG","§e🔎 Search",List.of("§7Use /worth <search>")));
        inv.setItem(48,button("BARRIER","§c🔎 Clear Search",List.of("§7Remove the current search")));
        inv.setItem(49,button("BOOK","§f📂 Category: §a"+filter.displayName(),List.of("§7Click to cycle categories")));
        inv.setItem(50,button("HOPPER","§f🔄 Sort: §a"+sort.displayName(),List.of("§7Click to cycle sorting")));
        inv.setItem(51,button("EMERALD","§a💚 Emerald SMP",List.of("§7Master item-value database")));
        inv.setItem(52,button("PAPER","§f"+list.size()+" items",List.of("§7Matching items")));
        inv.setItem(53,button("ARROW","§aNext ➡",List.of("§7Go to the next page")));
        p.openInventory(inv);
    }

    public void openInfo(Player p,WorthEntry e,ViewState state){
        Inventory inv=plugin.getServer().createInventory(null,27,"§2§l💚 "+pretty(e.material()));
        inv.setItem(4,display(e,true));
        inv.setItem(11,button("EMERALD","§aWorth: §f$"+e.worth()+" / item",List.of("§7Category: §f"+e.category().displayName())));
        inv.setItem(13,button("CHEST","§bStack Worth: §f$"+e.stackWorth(),List.of("§7Stack size: §f"+e.material().getMaxStackSize())));
        inv.setItem(15,button("BOOK","§fMaterial: §7"+e.material().name(),List.of("§7Java Edition material")));
        inv.setItem(22,button("ARROW","§a⬅ Back",List.of("§7Return to the worth browser")));
        views.put(p.getUniqueId(),state);
        p.openInventory(inv);
    }

    public void click(Player p,int slot){
        ViewState s=views.get(p.getUniqueId()); if(s==null)return;
        if(slot>=0&&slot<45){int idx=s.page*45+slot;if(idx<s.results.size())openInfo(p,s.results.get(idx),s);return;}
        if(slot==45){openBrowser(p,s.query,s.page-1,s.filter,s.sort);return;}
        if(slot==53){openBrowser(p,s.query,s.page+1,s.filter,s.sort);return;}
        if(slot==46){openBrowser(p,s.query,0,WorthCategoryFilter.ALL,s.sort);return;}
        if(slot==47){p.closeInventory();plugin.getMessageService().send(p,"&eUse &f/worth <item> &eto search the database.");return;}
        if(slot==48){openBrowser(p,"",0,s.filter,s.sort);return;}
        if(slot==49){WorthCategoryFilter[] v=WorthCategoryFilter.values();openBrowser(p,s.query,0,v[(s.filter.ordinal()+1)%v.length],s.sort);return;}
        if(slot==50){openBrowser(p,s.query,0,s.filter,s.sort.next());return;}
        if(slot==22){openBrowser(p,s.query,s.page,s.filter,s.sort);}
    }

    public void clear(Player p){views.remove(p.getUniqueId());}

    private Comparator<WorthEntry> comparator(WorthSort sort){
        return switch(sort){
            case NAME_ASC->Comparator.comparing(e->pretty(e.material()),String.CASE_INSENSITIVE_ORDER);
            case WORTH_ASC->Comparator.comparingLong(WorthEntry::worth).thenComparing(e->pretty(e.material()));
            case WORTH_DESC->Comparator.comparingLong(WorthEntry::worth).reversed().thenComparing(e->pretty(e.material()));
            case CATEGORY->Comparator.comparing((WorthEntry e)->e.category().displayName()).thenComparing(e->pretty(e.material()));
        };
    }

    private ItemStack display(WorthEntry e,boolean info){
        ItemStack item=new ItemStack(e.material());
        ItemMeta meta=item.getItemMeta();
        meta.setDisplayName("§f"+pretty(e.material()));
        List<String> lore=new ArrayList<>();
        lore.add("§aWorth: §f$"+e.worth()+" §7/ item");
        lore.add("§7Category: §f"+e.category().displayName());
        lore.add("§7Stack Worth: §f$"+e.stackWorth());
        if(!info) lore.add("§8Click for more information");
        meta.setLore(lore); item.setItemMeta(meta); return item;
    }

    private ItemStack button(String mat,String name,List<String> lore){
        Material m=Material.matchMaterial(mat); if(m==null)m=Material.PAPER;
        ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.setDisplayName(name);meta.setLore(lore);i.setItemMeta(meta);return i;
    }

    private String pretty(Material m){String s=m.name().toLowerCase(Locale.ROOT).replace('_',' ');StringBuilder b=new StringBuilder();for(String w:s.split(" ")){if(!w.isEmpty())b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');}return b.toString().trim();}

    private boolean isSupported(Material m){return m.isItem()&&!m.isLegacy()&&!EXCLUDED.contains(m)&&!m.name().endsWith("_SPAWN_EGG");}

    private void generateDefaultFile(){
        YamlConfiguration out=new YamlConfiguration();out.set("version",1);out.set("info","Emerald SMP master /worth database. Whole-dollar prices; edit worth.<MATERIAL> to rebalance.");
        for(Material m:Material.values())if(isSupported(m)){out.set("worth."+m.name(),defaultPrice(m));out.set("categories."+m.name(),defaultCategory(m).name());out.set("enabled."+m.name(),true);}
        try{out.save(file);}catch(IOException ex){throw new IllegalStateException("Could not create worth.yml",ex);}
    }

    private WorthCategory defaultCategory(Material m){
        String n=m.name();
        if(n.contains("NETHER")||n.contains("NETHERRACK")||n.contains("QUARTZ")||n.contains("BASALT")||n.contains("BLACKSTONE")||n.contains("CRIMSON")||n.contains("WARPED")||n.contains("SOUL"))return WorthCategory.NETHER;
        if(n.contains("END_")||n.startsWith("END")||n.contains("CHORUS")||n.contains("SHULKER"))return WorthCategory.END;
        if(n.contains("REDSTONE")||n.contains("REPEATER")||n.contains("COMPARATOR")||n.contains("PISTON")||n.contains("OBSERVER")||n.contains("DISPENSER")||n.contains("DROPPER")||n.contains("HOPPER")||n.contains("TARGET")||n.contains("TRIPWIRE")||n.contains("DAYLIGHT"))return WorthCategory.REDSTONE;
        if(n.contains("SWORD")||n.contains("AXE")||n.contains("BOW")||n.contains("CROSSBOW")||n.contains("TRIDENT")||n.contains("SHIELD")||n.contains("HELMET")||n.contains("CHESTPLATE")||n.contains("LEGGINGS")||n.contains("BOOTS")||n.contains("MACE")||n.contains("ARROW"))return WorthCategory.COMBAT;
        if(n.contains("PICKAXE")||n.contains("SHOVEL")||n.contains("HOE")||n.contains("SHEARS")||n.contains("FLINT_AND_STEEL")||n.contains("FISHING_ROD")||n.contains("BRUSH")||n.contains("BUCKET")||n.contains("COMPASS")||n.contains("CLOCK")||n.contains("LEAD")||n.contains("NAME_TAG"))return WorthCategory.TOOLS;
        if(m.isEdible())return WorthCategory.FOOD;
        if(n.contains("SEEDS")||n.contains("SAPLING")||n.contains("WHEAT")||n.contains("CARROT")||n.contains("POTATO")||n.contains("BEETROOT")||n.contains("BAMBOO")||n.contains("SUGAR_CANE")||n.contains("KELP")||n.contains("VINE")||n.contains("MELON")||n.contains("PUMPKIN")||n.contains("COCOA"))return WorthCategory.FARMING;
        if(n.contains("BONE")||n.contains("ROTTEN_FLESH")||n.contains("STRING")||n.contains("SPIDER_EYE")||n.contains("GUNPOWDER")||n.contains("LEATHER")||n.contains("FEATHER")||n.contains("SLIME")||n.contains("PRISMARINE")||n.contains("INK_SAC")||n.contains("BLAZE_ROD")||n.contains("ENDER_PEARL"))return WorthCategory.MOB_DROPS;
        if(n.contains("POTION")||n.contains("BREWING")||n.contains("BLAZE_POWDER")||n.contains("FERMENTED_SPIDER_EYE")||n.contains("GLISTERING_MELON")||n.contains("MAGMA_CREAM"))return WorthCategory.BREWING;
        if(n.contains("DIAMOND")||n.contains("EMERALD")||n.contains("IRON")||n.contains("GOLD")||n.contains("COPPER")||n.contains("NETHERITE")||n.contains("COAL")||n.contains("LAPIS")||n.contains("AMETHYST")||n.contains("RAW_"))return WorthCategory.RESOURCES;
        if(m.isBlock())return WorthCategory.BLOCKS;
        return WorthCategory.MISC;
    }

    private long defaultPrice(Material m){
        String n=m.name();long base=2;
        if(n.contains("DIAMOND"))base=500;else if(n.contains("NETHERITE"))base=2500;else if(n.contains("EMERALD"))base=300;else if(n.contains("ANCIENT_DEBRIS"))base=1800;else if(n.contains("GOLD"))base=70;else if(n.contains("IRON"))base=35;else if(n.contains("COPPER"))base=12;else if(n.contains("REDSTONE"))base=8;else if(n.contains("LAPIS"))base=12;else if(n.contains("COAL"))base=10;else if(n.contains("QUARTZ"))base=18;else if(n.contains("AMETHYST"))base=16;else if(n.contains("OBSIDIAN"))base=30;else if(n.contains("CRYING_OBSIDIAN"))base=45;else if(n.contains("PRISMARINE"))base=22;else if(n.contains("SCULK"))base=28;else if(n.contains("SHULKER"))base=1200;else if(n.contains("ELYTRA"))base=10000;else if(n.contains("DRAGON"))base=15000;else if(n.contains("BEACON"))base=12000;else if(n.contains("TOTEM"))base=3500;else if(n.contains("NETHER_STAR"))base=8000;else if(n.contains("SPONGE"))base=250;else if(n.contains("SLIME"))base=30;else if(n.contains("BLAZE"))base=45;else if(n.contains("ENDER"))base=55;else if(n.contains("GUNPOWDER"))base=18;else if(n.contains("GOLDEN_APPLE"))base=450;else if(n.contains("ENCHANTED_GOLDEN_APPLE"))base=5000;else if(m.isEdible())base=8;else if(m.isBlock())base=3;
        if(n.endsWith("_BLOCK")||n.endsWith("_ORE")){if(n.contains("DIAMOND"))return 4500;if(n.contains("EMERALD"))return 2700;if(n.contains("GOLD"))return 630;if(n.contains("IRON"))return 315;if(n.contains("COPPER"))return 108;if(n.contains("REDSTONE"))return 72;if(n.contains("LAPIS"))return 108;return Math.max(3,base*8);}
        if(n.contains("SWORD")||n.contains("PICKAXE")||n.contains("AXE")||n.contains("SHOVEL")||n.contains("HOE"))base=Math.max(base,40);
        return base;
    }

    public static final class ViewState {
        final String query; final int page; final WorthCategoryFilter filter; final WorthSort sort; final List<WorthEntry> results;
        ViewState(String q,int p,WorthCategoryFilter f,WorthSort s,List<WorthEntry> r){query=q;page=p;filter=f;sort=s;results=r;}
    }
}
