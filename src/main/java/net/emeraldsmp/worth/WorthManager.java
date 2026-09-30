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
import java.util.concurrent.ConcurrentHashMap;

public final class WorthManager {
    private static final int PREVIOUS=45, ALL=46, SEARCH=47, CLEAR=48, CATEGORY=49, SORT=50, INFO=52, NEXT=53;
    private static final Set<Material> EXCLUDED=EnumSet.of(Material.AIR,Material.CAVE_AIR,Material.VOID_AIR,Material.BEDROCK,Material.BARRIER,Material.LIGHT,Material.DEBUG_STICK,Material.KNOWLEDGE_BOOK,Material.COMMAND_BLOCK,Material.CHAIN_COMMAND_BLOCK,Material.REPEATING_COMMAND_BLOCK,Material.COMMAND_BLOCK_MINECART,Material.JIGSAW,Material.STRUCTURE_BLOCK,Material.STRUCTURE_VOID,Material.END_PORTAL,Material.END_GATEWAY,Material.NETHER_PORTAL,Material.FIRE,Material.SOUL_FIRE,Material.SPAWNER,Material.END_PORTAL_FRAME,Material.REINFORCED_DEEPSLATE,Material.BUDDING_AMETHYST,Material.TRIAL_SPAWNER,Material.VAULT);
    private final EmeraldSMP plugin; private final File file; private final Map<Material,WorthEntry> entries=new EnumMap<>(Material.class); private final Map<UUID,ViewState> views=new ConcurrentHashMap<>();
    public WorthManager(EmeraldSMP plugin){this.plugin=plugin;this.file=new File(plugin.getDataFolder(),"worth.yml");}
    public void load(){if(!plugin.getDataFolder().exists()&&!plugin.getDataFolder().mkdirs())throw new IllegalStateException("Could not create plugin data folder.");if(!file.exists())generateDefaultFile();if(!reload())throw new IllegalStateException("Initial worth data could not be loaded.");}
    public synchronized boolean reload(){try{if(!file.exists())generateDefaultFile();YamlConfiguration next=YamlConfiguration.loadConfiguration(file);ConfigurationSection worth=next.getConfigurationSection("worth");if(worth==null)throw new IllegalArgumentException("Missing 'worth' section.");Map<Material,WorthEntry> parsed=new EnumMap<>(Material.class);for(String key:worth.getKeys(false)){Material m=Material.matchMaterial(key);if(m==null||!isSupported(m))continue;long value;try{value=new BigDecimal(String.valueOf(worth.get(key))).longValueExact();}catch(Exception ex){continue;}if(value<0)continue;String cn=next.getString("categories."+key,defaultCategory(m).name());WorthCategory cat;try{cat=WorthCategory.valueOf(cn.toUpperCase(Locale.ROOT));}catch(Exception ex){cat=defaultCategory(m);}parsed.put(m,new WorthEntry(m,value,cat,next.getBoolean("enabled."+key,true)));}if(parsed.isEmpty())throw new IllegalArgumentException("No valid worth entries found.");entries.clear();entries.putAll(parsed);return true;}catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"[Worth] Reload failed; previous valid data was kept.",ex);return false;}}
    public synchronized Collection<WorthEntry> all(){List<WorthEntry> r=new ArrayList<>();for(Material m:Material.values())if(isSupported(m)){WorthEntry e=entries.get(m);if(e==null||e.enabled())r.add(e==null?new WorthEntry(m,defaultPrice(m),defaultCategory(m),true):e);}return r;}
    public synchronized WorthEntry get(Material m){return entries.get(m);} public synchronized long value(Material m,int amount){WorthEntry e=entries.get(m);if(e==null||!e.enabled()||amount<0)return 0;return Math.multiplyExact(e.worth(),(long)amount);}
    public void openBrowser(Player p,String query,int page,WorthCategoryFilter filter,WorthSort sort){String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);WorthCategoryFilter f=filter==null?WorthCategoryFilter.ALL:filter;WorthSort s=sort==null?WorthSort.NAME_ASC:sort;List<WorthEntry> list=all().stream().filter(e->f==WorthCategoryFilter.ALL||e.category()==f.category()).filter(e->q.isEmpty()||e.material().name().toLowerCase(Locale.ROOT).contains(q)||pretty(e.material()).toLowerCase(Locale.ROOT).contains(q)).sorted(comparator(s)).toList();if(list.isEmpty()){plugin.getMessageService().send(p,"&cNo items found for &f\""+(query==null?"":query)+"&c\".");return;}int pages=Math.max(1,(list.size()+44)/45);int safe=Math.max(0,Math.min(page,pages-1));views.put(p.getUniqueId(),new ViewState(q,safe,f,s,list));Inventory inv=plugin.getServer().createInventory(null,54,"§2§l💚 WORTH §8• §fPage "+(safe+1)+"/"+pages);int from=safe*45,to=Math.min(from+45,list.size());for(int i=from;i<to;i++)inv.setItem(i,display(list.get(i),false));inv.setItem(PREVIOUS,button("ARROW","§a⬅ Previous",List.of("§7Go to the previous page")));inv.setItem(ALL,button("COMPASS","§b🏠 All Items",List.of("§7Clear search and category filter")));inv.setItem(SEARCH,button("NAME_TAG","§e🔎 Search",List.of("§7Click, then type a search in chat")));inv.setItem(CLEAR,button("BARRIER","§c✖ Clear Search",List.of("§7Remove the current search")));inv.setItem(CATEGORY,button("BOOK","§f📂 Category: §a"+f.displayName(),List.of("§7Click to cycle categories")));inv.setItem(SORT,button("HOPPER","§f🔄 Sort: §a"+s.displayName(),List.of("§7Click to cycle sorting")));inv.setItem(INFO,button("PAPER","§f"+list.size()+" items",List.of("§7Matching items")));inv.setItem(NEXT,button("ARROW","§aNext ➡",List.of("§7Go to the next page")));p.openInventory(inv);}
    public void openInfo(Player p,WorthEntry e,ViewState state){Inventory inv=plugin.getServer().createInventory(null,27,"§2§l💚 "+pretty(e.material()));inv.setItem(4,display(e,true));inv.setItem(11,button("EMERALD","§aWorth: §f$"+e.worth()+" / item",List.of("§7Category: §f"+e.category().displayName())));inv.setItem(13,button("CHEST","§bStack Worth: §f$"+e.stackWorth(),List.of("§7Stack size: §f"+e.material().getMaxStackSize())));inv.setItem(15,button("BOOK","§fMaterial: §7"+e.material().name(),List.of("§7Java Edition material")));inv.setItem(22,button("ARROW","§a⬅ Back",List.of("§7Return to the worth browser")));views.put(p.getUniqueId(),state);p.openInventory(inv);}
    public void click(Player p,int slot){
        ViewState s=views.get(p.getUniqueId());
        if(s==null)return;
        if(slot>=0&&slot<45){
            int idx=s.page*45+slot;
            if(idx>=0&&idx<s.results.size()){
                WorthEntry entry=s.results.get(idx);
                openInfo(p,entry,s);
            }
            return;
        }
        switch(slot){
            case PREVIOUS->{
                if(s.page<=0){plugin.getMessageService().send(p,"&7Already on the first page.");return;}
                String q=s.query; int page=s.page-1; WorthCategoryFilter f=s.filter; WorthSort sort=s.sort;
                openBrowser(p,q,page,f,sort);
            }
            case NEXT->{
                int last=(s.results.size()-1)/45;
                if(s.page>=last){plugin.getMessageService().send(p,"&7Already on the last page.");return;}
                String q=s.query; int page=s.page+1; WorthCategoryFilter f=s.filter; WorthSort sort=s.sort;
                openBrowser(p,q,page,f,sort);
            }
            case ALL->{
                WorthSort sort=s.sort;
                openBrowser(p,"",0,WorthCategoryFilter.ALL,sort);
            }
            case SEARCH->{p.closeInventory();plugin.beginWorthSearch(p);}
            case CLEAR->{
                WorthCategoryFilter f=s.filter; WorthSort sort=s.sort;
                openBrowser(p,"",0,f,sort);
            }
            case CATEGORY->{
                WorthCategoryFilter[] v=WorthCategoryFilter.values();
                String q=s.query; WorthCategoryFilter f=v[(s.filter.ordinal()+1)%v.length]; WorthSort sort=s.sort;
                openBrowser(p,q,0,f,sort);
            }
            case SORT->{
                String q=s.query; WorthCategoryFilter f=s.filter; WorthSort sort=s.sort.next();
                plugin.getServer().getScheduler().runTask(plugin,()->openBrowser(p,q,0,f,sort));
            }
            case 22->{
                String q=s.query; int page=s.page; WorthCategoryFilter f=s.filter; WorthSort sort=s.sort;
                plugin.getServer().getScheduler().runTask(plugin,()->openBrowser(p,q,page,f,sort));
            }
            default->{}
        }
    }
    public void searchCurrent(Player p, String query){
        ViewState s = views.get(p.getUniqueId());
        WorthCategoryFilter filter = s == null ? WorthCategoryFilter.ALL : s.filter;
        WorthSort sort = s == null ? WorthSort.NAME_ASC : s.sort;
        openBrowser(p, query, 0, filter, sort);
    }

    public void reopenCurrent(Player p){
        ViewState s = views.get(p.getUniqueId());
        if(s == null){
            openBrowser(p, "", 0, WorthCategoryFilter.ALL, WorthSort.NAME_ASC);
            return;
        }
        openBrowser(p, s.query, s.page, s.filter, s.sort);
    }

    public void clear(Player p){views.remove(p.getUniqueId());}
    private Comparator<WorthEntry> comparator(WorthSort sort){return switch(sort){case NAME_ASC->Comparator.comparing(e->pretty(e.material()),String.CASE_INSENSITIVE_ORDER);case WORTH_ASC->Comparator.comparingLong(WorthEntry::worth).thenComparing(e->pretty(e.material()),String.CASE_INSENSITIVE_ORDER);case WORTH_DESC->Comparator.comparingLong(WorthEntry::worth).reversed().thenComparing(e->pretty(e.material()),String.CASE_INSENSITIVE_ORDER);case CATEGORY->Comparator.comparing((WorthEntry e)->e.category().displayName(),String.CASE_INSENSITIVE_ORDER).thenComparing(e->pretty(e.material()),String.CASE_INSENSITIVE_ORDER);};}
    private ItemStack display(WorthEntry e,boolean info){ItemStack i=new ItemStack(e.material());ItemMeta m=i.getItemMeta();m.setDisplayName("§f"+pretty(e.material()));m.setLore(List.of("§aWorth: §f$"+e.worth()+" §7/ item","§7Category: §f"+e.category().displayName(),"§7Stack Worth: §f$"+e.stackWorth(),info?"":"§8Click for more information"));i.setItemMeta(m);return i;}
    private ItemStack button(String mat,String name,List<String> lore){Material m=Material.matchMaterial(mat);if(m==null)m=Material.PAPER;ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.setDisplayName(name);meta.setLore(lore);i.setItemMeta(meta);return i;}
    private String pretty(Material m){String s=m.name().toLowerCase(Locale.ROOT).replace('_',' ');StringBuilder b=new StringBuilder();for(String w:s.split(" "))if(!w.isEmpty())b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');return b.toString().trim();}
    private boolean isSupported(Material m){return m.isItem()&&!m.isLegacy()&&!EXCLUDED.contains(m)&&!m.name().endsWith("_SPAWN_EGG");}
    private void generateDefaultFile(){YamlConfiguration out=new YamlConfiguration();out.set("version",1);out.set("info","Emerald SMP master /worth database. Whole-dollar prices; edit worth.<MATERIAL> to rebalance.");for(Material m:Material.values())if(isSupported(m)){out.set("worth."+m.name(),defaultPrice(m));out.set("categories."+m.name(),defaultCategory(m).name());out.set("enabled."+m.name(),true);}try{out.save(file);}catch(IOException ex){throw new IllegalStateException("Could not create worth.yml",ex);}}
    private WorthCategory defaultCategory(Material m){String n=m.name();if(n.contains("NETHER")||n.contains("NETHERRACK")||n.contains("QUARTZ")||n.contains("BASALT")||n.contains("BLACKSTONE")||n.contains("CRIMSON")||n.contains("WARPED")||n.contains("SOUL"))return WorthCategory.NETHER;if(n.contains("END_")||n.startsWith("END")||n.contains("CHORUS")||n.contains("SHULKER"))return WorthCategory.END;if(n.contains("REDSTONE")||n.contains("REPEATER")||n.contains("COMPARATOR")||n.contains("PISTON")||n.contains("OBSERVER")||n.contains("DISPENSER")||n.contains("DROPPER")||n.contains("HOPPER")||n.contains("TARGET")||n.contains("TRIPWIRE")||n.contains("DAYLIGHT"))return WorthCategory.REDSTONE;if(n.contains("SWORD")||n.contains("AXE")||n.contains("BOW")||n.contains("CROSSBOW")||n.contains("TRIDENT")||n.contains("SHIELD")||n.contains("HELMET")||n.contains("CHESTPLATE")||n.contains("LEGGINGS")||n.contains("BOOTS")||n.contains("MACE")||n.contains("ARROW"))return WorthCategory.COMBAT;if(n.contains("PICKAXE")||n.contains("SHOVEL")||n.contains("HOE")||n.contains("SHEARS")||n.contains("FLINT_AND_STEEL")||n.contains("FISHING_ROD")||n.contains("BRUSH")||n.contains("BUCKET")||n.contains("COMPASS")||n.contains("CLOCK")||n.contains("LEAD")||n.contains("NAME_TAG"))return WorthCategory.TOOLS;if(m.isEdible())return WorthCategory.FOOD;if(n.contains("SEEDS")||n.contains("SAPLING")||n.contains("WHEAT")||n.contains("CARROT")||n.contains("POTATO")||n.contains("BEETROOT")||n.contains("BAMBOO")||n.contains("SUGAR_CANE")||n.contains("KELP")||n.contains("VINE")||n.contains("MELON")||n.contains("PUMPKIN")||n.contains("COCOA"))return WorthCategory.FARMING;if(n.contains("BONE")||n.contains("ROTTEN_FLESH")||n.contains("STRING")||n.contains("SPIDER_EYE")||n.contains("GUNPOWDER")||n.contains("LEATHER")||n.contains("FEATHER")||n.contains("SLIME")||n.contains("PRISMARINE")||n.contains("INK_SAC")||n.contains("BLAZE_ROD")||n.contains("ENDER_PEARL"))return WorthCategory.MOB_DROPS;if(n.contains("POTION")||n.contains("BREWING")||n.contains("BLAZE_POWDER")||n.contains("FERMENTED_SPIDER_EYE")||n.contains("GLISTERING_MELON")||n.contains("MAGMA_CREAM"))return WorthCategory.BREWING;if(n.contains("DIAMOND")||n.contains("EMERALD")||n.contains("IRON")||n.contains("GOLD")||n.contains("COPPER")||n.contains("NETHERITE")||n.contains("COAL")||n.contains("LAPIS")||n.contains("AMETHYST")||n.contains("RAW_"))return WorthCategory.RESOURCES;if(m.isBlock())return WorthCategory.BLOCKS;return WorthCategory.MISC;}
    private long defaultPrice(Material m){String n=m.name();long base=2;if(n.contains("DIAMOND"))base=500;else if(n.contains("NETHERITE"))base=2500;else if(n.contains("EMERALD"))base=300;else if(n.contains("ANCIENT_DEBRIS"))base=1800;else if(n.contains("GOLD"))base=70;else if(n.contains("IRON"))base=35;else if(n.contains("COPPER"))base=12;else if(n.contains("REDSTONE"))base=8;else if(n.contains("LAPIS"))base=12;else if(n.contains("COAL"))base=10;else if(n.contains("QUARTZ"))base=18;else if(n.contains("AMETHYST"))base=16;else if(n.contains("OBSIDIAN"))base=30;else if(n.contains("CRYING_OBSIDIAN"))base=45;else if(n.contains("PRISMARINE"))base=22;else if(n.contains("GLASS"))base=4;else if(n.contains("WOOD")||n.contains("LOG")||n.contains("PLANKS"))base=6;else if(n.contains("STONE"))base=3;else if(n.contains("DIRT")||n.contains("SAND")||n.contains("GRAVEL"))base=2;else if(n.contains("BOOK"))base=15;else if(n.contains("PAPER"))base=5;else if(n.contains("LEATHER"))base=25;else if(n.contains("BLAZE_ROD"))base=40;else if(n.contains("ENDER_PEARL"))base=55;else if(n.contains("GUNPOWDER"))base=20;else if(m.isEdible())base=8;return base;}
    private static final class ViewState {
        private final String query;
        private final int page;
        private final WorthCategoryFilter filter;
        private final WorthSort sort;
        private final List<WorthEntry> results;

        private ViewState(String query, int page, WorthCategoryFilter filter, WorthSort sort, List<WorthEntry> results){
            this.query = query;
            this.page = page;
            this.filter = filter;
            this.sort = sort;
            this.results = results;
        }
    }
}
