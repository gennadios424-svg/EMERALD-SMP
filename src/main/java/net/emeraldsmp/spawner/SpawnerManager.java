package net.emeraldsmp.spawner;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class SpawnerManager implements Listener {
    public static final String TYPE_SKELETON = "skeleton";
    private static final String TITLE = "§2§l🧟 SKELETON SPAWNER";
    private final EmeraldSMP plugin;
    private final File file;
    private final Map<String, Data> spawners = new LinkedHashMap<>();
    private final Map<UUID, String> openSpawners = new HashMap<>();
    private final NamespacedKey typeKey, stackKey;
    private int taskId = -1;

    public static final class Data {
        String world; int x,y,z; UUID owner; int amount=1;
        long bones,arrows,bows,xp,totalSpawned,activeMillis,moneyGenerated,lastCycle;
        long generatedBones,generatedArrows,generatedBows,cycles;
        boolean autoSell=true,collectXp=true,bonesEnabled=true,arrowsEnabled=true,bowsEnabled=true;
        Data(String world,int x,int y,int z,UUID owner){this.world=world;this.x=x;this.y=y;this.z=z;this.owner=owner;lastCycle=System.currentTimeMillis();}
        String key(){return world+":"+x+":"+y+":"+z;}
        Location location(){World w=Bukkit.getWorld(world);return w==null?null:new Location(w,x,y,z);}
    }

    public SpawnerManager(EmeraldSMP plugin){
        this.plugin=plugin; file=new File(plugin.getDataFolder(),"spawners.yml");
        typeKey=new NamespacedKey(plugin,"emerald-spawner"); stackKey=new NamespacedKey(plugin,"emerald-spawner-stack");
    }

    public void load(){
        spawners.clear(); if(!file.exists())return;
        YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root=y.getConfigurationSection("spawners"); if(root==null)return;
        for(String key:root.getKeys(false))try{
            String p="spawners."+key, owner=y.getString(p+".owner"); if(owner==null)continue;
            Data d=new Data(y.getString(p+".world","world"),y.getInt(p+".x"),y.getInt(p+".y"),y.getInt(p+".z"),UUID.fromString(owner));
            d.amount=Math.max(1,y.getInt(p+".amount",1)); d.bones=y.getLong(p+".drops.bones"); d.arrows=y.getLong(p+".drops.arrows"); d.bows=y.getLong(p+".drops.bows"); d.xp=y.getLong(p+".stored-xp");
            d.totalSpawned=y.getLong(p+".statistics.total-spawned"); d.activeMillis=y.getLong(p+".statistics.active-millis"); d.moneyGenerated=y.getLong(p+".statistics.money-generated");
            d.generatedBones=y.getLong(p+".statistics.generated-bones"); d.generatedArrows=y.getLong(p+".statistics.generated-arrows"); d.generatedBows=y.getLong(p+".statistics.generated-bows"); d.cycles=y.getLong(p+".statistics.cycles");
            d.autoSell=y.getBoolean(p+".auto-sell",true); d.collectXp=y.getBoolean(p+".collect-xp",true);
            d.bonesEnabled=y.getBoolean(p+".preferences.bones",true); d.arrowsEnabled=y.getBoolean(p+".preferences.arrows",true); d.bowsEnabled=y.getBoolean(p+".preferences.bows",true);
            spawners.put(d.key(),d);
        }catch(Exception ex){plugin.getLogger().warning("Skipped invalid spawner entry: "+key);}
    }

    public void save(){
        YamlConfiguration y=new YamlConfiguration();
        for(Data d:spawners.values()){
            String p="spawners."+d.key();
            y.set(p+".world",d.world);y.set(p+".x",d.x);y.set(p+".y",d.y);y.set(p+".z",d.z);y.set(p+".owner",d.owner.toString());y.set(p+".amount",d.amount);
            y.set(p+".drops.bones",d.bones);y.set(p+".drops.arrows",d.arrows);y.set(p+".drops.bows",d.bows);y.set(p+".stored-xp",d.xp);
            y.set(p+".auto-sell",d.autoSell);y.set(p+".collect-xp",d.collectXp);y.set(p+".preferences.bones",d.bonesEnabled);y.set(p+".preferences.arrows",d.arrowsEnabled);y.set(p+".preferences.bows",d.bowsEnabled);
            y.set(p+".statistics.total-spawned",d.totalSpawned);y.set(p+".statistics.active-millis",d.activeMillis);y.set(p+".statistics.money-generated",d.moneyGenerated);
            y.set(p+".statistics.generated-bones",d.generatedBones);y.set(p+".statistics.generated-arrows",d.generatedArrows);y.set(p+".statistics.generated-bows",d.generatedBows);y.set(p+".statistics.cycles",d.cycles);
        }
        try{y.save(file);}catch(IOException ex){plugin.getLogger().warning("Could not save spawners.yml: "+ex.getMessage());}
    }

    public void start(){Bukkit.getScheduler().runTask(plugin,this::restoreMarkers);taskId=Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,this::tick,20L,20L);}
    public void stop(){if(taskId!=-1)Bukkit.getScheduler().cancelTask(taskId);save();}

    private void tick(){
        long now=System.currentTimeMillis(), interval=Math.max(1000L,plugin.getConfig().getLong("spawners.skeleton.interval-seconds",12L)*1000L);
        int base=Math.max(1,plugin.getConfig().getInt("spawners.skeleton.amount",8));
        for(Data d:new ArrayList<>(spawners.values())){
            Location loc=d.location(); if(loc==null||!loc.getChunk().isLoaded()){d.lastCycle=now;continue;}
            long elapsed=Math.max(0L,now-d.lastCycle); long cycles=Math.min(100L,elapsed/interval); if(cycles<=0)continue;
            d.lastCycle+=cycles*interval; d.activeMillis+=cycles*interval; produce(d,base*Math.max(1,d.amount),cycles);
        }
        save();
    }

    private void produce(Data d,int perCycle,long cycles){
        Random random=new Random(); long bones=0,arrows=0,bows=0,produced=(long)perCycle*cycles;
        for(long i=0;i<produced;i++){bones+=1+random.nextInt(3);arrows+=1+random.nextInt(3);if(random.nextDouble()<0.085D)bows++;}
        d.totalSpawned+=produced; d.cycles+=cycles;
        if(d.bonesEnabled){d.generatedBones+=bones;d.bones+=bones;}
        if(d.arrowsEnabled){d.generatedArrows+=arrows;d.arrows+=arrows;}
        if(d.bowsEnabled){d.generatedBows+=bows;d.bows+=bows;}
        if(d.collectXp)d.xp+=produced*5L;
        if(d.autoSell){
            long sellBones=d.bonesEnabled?bones:0L;
            long sellArrows=d.arrowsEnabled?arrows:0L;
            long sellBows=d.bowsEnabled?bows:0L;
            long money=plugin.getWorthManager().value(Material.BONE,(int)Math.min(Integer.MAX_VALUE,sellBones))
                    +plugin.getWorthManager().value(Material.ARROW,(int)Math.min(Integer.MAX_VALUE,sellArrows))
                    +plugin.getWorthManager().value(Material.BOW,(int)Math.min(Integer.MAX_VALUE,sellBows));
            if(money>0){String username=Bukkit.getOfflinePlayer(d.owner).getName();if(plugin.getEconomyManager().depositToUuid(d.owner,money,username==null?"Unknown":username)){d.moneyGenerated+=money;d.bones-=sellBones;d.arrows-=sellArrows;d.bows-=sellBows;}}
        }
    }

    public ItemStack createItem(int amount){
        amount=Math.max(1,Math.min(64,amount)); ItemStack item=new ItemStack(Material.SPAWNER); ItemMeta meta=item.getItemMeta();
        meta.setDisplayName("§a🧟 Skeleton Spawner");meta.setLore(List.of("§7Produces §f"+(plugin.getConfig().getInt("spawners.skeleton.amount",8)*amount)+" Skeletons §7every §f"+plugin.getConfig().getLong("spawners.skeleton.interval-seconds",12)+"s","§7Stack Amount: §f"+amount+"x","§7Right-click to configure","§8Emerald SMP"));
        meta.getPersistentDataContainer().set(typeKey,PersistentDataType.STRING,TYPE_SKELETON);meta.getPersistentDataContainer().set(stackKey,PersistentDataType.INTEGER,amount);item.setItemMeta(meta);item.setAmount(1);return item;
    }
    private boolean isSpawnerItem(ItemStack item){return item!=null&&item.getType()==Material.SPAWNER&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(typeKey,PersistentDataType.STRING);}
    private int itemStackAmount(ItemStack item){if(!isSpawnerItem(item))return 1;Integer v=item.getItemMeta().getPersistentDataContainer().get(stackKey,PersistentDataType.INTEGER);return v==null?1:Math.max(1,v);}

    private void markBlock(Data d){
        Location loc=d.location();if(loc==null||loc.getBlock().getType()!=Material.SPAWNER)return;CreatureSpawner state=(CreatureSpawner)loc.getBlock().getState();
        state.setSpawnedType(org.bukkit.entity.EntityType.SKELETON);state.setSpawnCount(0);state.setMinSpawnDelay(Integer.MAX_VALUE);state.setMaxSpawnDelay(Integer.MAX_VALUE);state.setDelay(Integer.MAX_VALUE);
        state.getPersistentDataContainer().set(typeKey,PersistentDataType.STRING,TYPE_SKELETON);state.getPersistentDataContainer().set(stackKey,PersistentDataType.INTEGER,d.amount);state.update(true,false);
    }
    private void restoreMarkers(){for(Data d:spawners.values())markBlock(d);}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void place(BlockPlaceEvent e){
        if(!isSpawnerItem(e.getItemInHand())||e.getBlockPlaced().getType()!=Material.SPAWNER)return;
        Block placed=e.getBlockPlaced();Player p=e.getPlayer();int incoming=itemStackAmount(e.getItemInHand());Data target=null;
        for(BlockFace face:new BlockFace[]{BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.UP,BlockFace.DOWN}){
            Data candidate=spawners.get(key(placed.getRelative(face)));if(candidate!=null&&candidate.owner.equals(p.getUniqueId())){target=candidate;break;}
        }
        if(target!=null){
            placed.setType(Material.AIR,false);target.amount+=incoming;markBlock(target);save();p.sendMessage(ChatColor.GREEN+"Spawner stack increased to "+target.amount+"x.");open(p,target);return;
        }
        Data d=new Data(placed.getWorld().getName(),placed.getX(),placed.getY(),placed.getZ(),p.getUniqueId());d.amount=incoming;spawners.put(d.key(),d);markBlock(d);save();p.sendMessage(ChatColor.GREEN+"Skeleton Spawner placed.");
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void interact(PlayerInteractEvent e){
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;Block clicked=e.getClickedBlock();if(clicked==null||clicked.getType()!=Material.SPAWNER)return;Data d=spawners.get(key(clicked));if(d==null)return;
        e.setCancelled(true);Player p=e.getPlayer();
        if(isSpawnerItem(e.getItem())){
            if(!d.owner.equals(p.getUniqueId())&&!p.hasPermission("emerald.admin")){p.sendMessage(ChatColor.RED+"Only the owner can add to this spawner stack.");return;}
            int incoming=itemStackAmount(e.getItem());e.getItem().setAmount(Math.max(0,e.getItem().getAmount()-1));d.amount+=incoming;markBlock(d);save();p.sendMessage(ChatColor.GREEN+"Spawner stack increased to "+d.amount+"x.");open(p,d);return;
        }
        if(!d.owner.equals(p.getUniqueId())&&!p.hasPermission("emerald.admin")){p.sendMessage(ChatColor.RED+"Only the owner can manage this spawner.");return;}open(p,d);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void breakBlock(BlockBreakEvent e){
        Data d=spawners.get(key(e.getBlock()));if(d==null)return;Player p=e.getPlayer();
        if(!d.owner.equals(p.getUniqueId())&&!p.hasPermission("emerald.admin")){e.setCancelled(true);p.sendMessage(ChatColor.RED+"Only the owner can break this spawner.");return;}
        e.setCancelled(true);e.getBlock().setType(Material.AIR,false);spawners.remove(d.key());openSpawners.values().removeIf(v->v.equals(d.key()));
        giveSpawnerItems(p,d.amount);give(p,Material.BONE,d.bones);give(p,Material.ARROW,d.arrows);give(p,Material.BOW,d.bows);if(d.collectXp&&d.xp>0)p.giveExp((int)Math.min(Integer.MAX_VALUE,d.xp));save();
        p.sendMessage(ChatColor.GREEN+"Skeleton Spawner x"+d.amount+" broken and returned.");
    }

    private void open(Player p,Data d){
        Inventory inv=Bukkit.createInventory(null,54,TITLE);
        inv.setItem(4,item(Material.SPAWNER,"§a🧟 Skeleton Spawner",List.of("§7Stack: §f"+d.amount+"x","§7⚡ Production: §f"+(plugin.getConfig().getInt("spawners.skeleton.amount",8)*d.amount)+" Skeletons / "+plugin.getConfig().getLong("spawners.skeleton.interval-seconds",12)+" Seconds","§7Owner: §f"+Optional.ofNullable(Bukkit.getOfflinePlayer(d.owner).getName()).orElse("Unknown"))));
        inv.setItem(19,item(Material.BONE,"§f🦴 Bones §a"+d.bones,List.of("§7Stored drops","§7Generated total: §f"+d.generatedBones)));
        inv.setItem(21,item(Material.ARROW,"§f🏹 Arrows §a"+d.arrows,List.of("§7Stored drops","§7Generated total: §f"+d.generatedArrows)));
        inv.setItem(23,item(Material.BOW,"§f🏹 Bows §a"+d.bows,List.of("§7Stored drops","§7Generated total: §f"+d.generatedBows)));
        inv.setItem(25,item(Material.EXPERIENCE_BOTTLE,"§d✨ Stored XP §a"+d.xp,List.of("§7Real stored experience")));
        inv.setItem(27,item(Material.DROPPER,"§e📤 DROP ITEMS",List.of("§7Drop all stored Bones, Arrows and Bows","§7Directly in front of you")));
        inv.setItem(29,item(Material.CHEST,"§a📦 COLLECT DROPS",List.of("§7Collect all stored Bones, Arrows and Bows")));
        inv.setItem(31,item(d.autoSell?Material.EMERALD:Material.REDSTONE,"§f💰 AUTO SELL: "+(d.autoSell?"§aON":"§cOFF"),List.of("§7Generated drops are sold instantly","§7Uses existing /worth values")));
        inv.setItem(33,item(Material.EXPERIENCE_BOTTLE,"§d✨ COLLECT XP",List.of("§7Stored XP: §f"+d.xp,"§7Click to receive it")));
        inv.setItem(35,item(Material.BOOK,"§b📊 STATISTICS",List.of("§7Cycles: §f"+d.cycles,"§7Skeletons Produced: §f"+d.totalSpawned,"§7Bones Generated: §f"+d.generatedBones,"§7Arrows Generated: §f"+d.generatedArrows,"§7Bows Generated: §f"+d.generatedBows,"§7Money Generated: §a"+plugin.getEconomyManager().format(d.moneyGenerated),"§7Time Active: §f"+formatTime(d.activeMillis))));
        inv.setItem(43,item(Material.COMPARATOR,"§e⚙ PREFERENCES",List.of("§7Configure generated drops","§7Preferences are kept at the bottom")));
        inv.setItem(49,item(Material.HOPPER,"§e⚙ Drop Preferences",List.of("§fBones: "+(d.bonesEnabled?"§a✓ ON":"§c✗ OFF"),"§fArrows: "+(d.arrowsEnabled?"§a✓ ON":"§c✗ OFF"),"§fBows: "+(d.bowsEnabled?"§a✓ ON":"§c✗ OFF"),"§7Click to configure")));
        openSpawners.put(p.getUniqueId(),d.key());p.openInventory(inv);
    }
    private void openPreferences(Player p,Data d){
        Inventory inv=Bukkit.createInventory(null,27,"§2§l⚙ SPAWNER PREFERENCES");
        inv.setItem(11,item(d.bonesEnabled?Material.LIME_DYE:Material.GRAY_DYE,"§f🦴 Bones: "+(d.bonesEnabled?"§aON":"§cOFF"),List.of("§7Toggle bone generation")));
        inv.setItem(13,item(d.arrowsEnabled?Material.LIME_DYE:Material.GRAY_DYE,"§f🏹 Arrows: "+(d.arrowsEnabled?"§aON":"§cOFF"),List.of("§7Toggle arrow generation")));
        inv.setItem(15,item(d.bowsEnabled?Material.LIME_DYE:Material.GRAY_DYE,"§f🏹 Bows: "+(d.bowsEnabled?"§aON":"§cOFF"),List.of("§7Toggle bow generation")));
        inv.setItem(22,item(Material.ARROW,"§a← BACK",List.of("§7Return to spawner")));
        p.openInventory(inv);
    }
    private ItemStack item(Material m,String name,List<String> lore){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.setDisplayName(name);meta.setLore(lore);i.setItemMeta(meta);return i;}

    @EventHandler
    public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String title=e.getView().getTitle();
        if(title.equals(TITLE)){
            e.setCancelled(true); Data d=getOpen(p); if(d==null)return;
            switch(e.getRawSlot()){
                case 27 -> dropItems(p,d);
                case 29 -> collect(p,d);
                case 31 -> {d.autoSell=!d.autoSell;open(p,d);}
                case 33 -> collectXp(p,d);
                case 43,49 -> openPreferences(p,d);
                default -> {}
            }
            save(); return;
        }
        if(title.equals("§2§l⚙ SPAWNER PREFERENCES")){
            e.setCancelled(true); Data d=getOpen(p); if(d==null)return;
            switch(e.getRawSlot()){
                case 11 -> {d.bonesEnabled=!d.bonesEnabled;openPreferences(p,d);}
                case 13 -> {d.arrowsEnabled=!d.arrowsEnabled;openPreferences(p,d);}
                case 15 -> {d.bowsEnabled=!d.bowsEnabled;openPreferences(p,d);}
                case 22 -> open(p,d);
                default -> {}
            }
            save();
        }
    }
    private Data getOpen(Player p){String k=openSpawners.get(p.getUniqueId());return k==null?null:spawners.get(k);}
    private void collect(Player p,Data d){give(p,Material.BONE,d.bones);give(p,Material.ARROW,d.arrows);give(p,Material.BOW,d.bows);d.bones=d.arrows=d.bows=0;p.sendMessage(ChatColor.GREEN+"Collected all stored spawner drops.");}
    private void dropItems(Player p,Data d){
        Location dropLocation=p.getLocation().clone().add(p.getLocation().getDirection().normalize().multiply(1.5));
        dropStored(p.getWorld(),dropLocation,Material.BONE,d.bones);
        dropStored(p.getWorld(),dropLocation,Material.ARROW,d.arrows);
        dropStored(p.getWorld(),dropLocation,Material.BOW,d.bows);
        d.bones=d.arrows=d.bows=0;
        p.sendMessage(ChatColor.GREEN+"Dropped all stored spawner items in front of you.");
    }
    private void dropStored(World world,Location location,Material material,long amount){
        while(amount>0){int n=(int)Math.min(64,amount);world.dropItem(location,new ItemStack(material,n));amount-=n;}
    }

    private void collectXp(Player p,Data d){if(d.xp<=0){p.sendMessage(ChatColor.YELLOW+"This spawner has no stored XP.");return;}p.giveExp((int)Math.min(Integer.MAX_VALUE,d.xp));d.xp=0;p.sendMessage(ChatColor.GREEN+"Collected stored XP.");}
    private void giveSpawnerItems(Player p,int amount){int remaining=Math.max(0,amount);while(remaining>0){int n=Math.min(64,remaining);giveItem(p,createItem(n));remaining-=n;}}
    private void giveItem(Player p,ItemStack item){Map<Integer,ItemStack> left=p.getInventory().addItem(item);for(ItemStack stack:left.values())p.getWorld().dropItemNaturally(p.getLocation(),stack);}
    private void pickup(Player p,Data d){collect(p,d);Location l=d.location();if(l!=null)l.getBlock().setType(Material.AIR,false);spawners.remove(d.key());openSpawners.remove(p.getUniqueId());giveSpawnerItems(p,d.amount);p.closeInventory();save();p.sendMessage(ChatColor.GREEN+"Spawner picked up.");}
    private void give(Player p,Material material,long amount){while(amount>0){int n=(int)Math.min(64,amount);giveItem(p,new ItemStack(material,n));amount-=n;}}
    private String key(Block b){return b.getWorld().getName()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    private String formatTime(long ms){long sec=ms/1000,h=sec/3600,m=(sec%3600)/60,s=sec%60;return h+"h "+m+"m "+s+"s";}
}