package net.emeraldsmp.spawner;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.managers.EconomyManager;
import org.bukkit.*;
import org.bukkit.block.Block;
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
    private static final String TITLE_PREFIX = "§2§l🧟 SKELETON SPAWNER";
    private final EmeraldSMP plugin;
    private final File file;
    private final Map<String, Data> spawners = new LinkedHashMap<>();
    private final Map<UUID, String> openSpawners = new HashMap<>();
    private final NamespacedKeyHolder keys;
    private int taskId = -1;

    private static final class NamespacedKeyHolder {
        final NamespacedKey type;
        final NamespacedKey stack;
        NamespacedKeyHolder(EmeraldSMP plugin) {
            type = new NamespacedKey(plugin, "emerald-spawner");
            stack = new NamespacedKey(plugin, "emerald-spawner-stack");
        }
    }

    public static final class Data {
        String world; int x,y,z; UUID owner;
        int amount = 1;
        long bones, arrows, bows, xp, totalSpawned, activeMillis, moneyGenerated, lastCycle;
        boolean autoSell=true, collectXp=true, bonesEnabled=true, arrowsEnabled=true, bowsEnabled=true;
        Data(String world,int x,int y,int z,UUID owner) { this.world=world;this.x=x;this.y=y;this.z=z;this.owner=owner;this.lastCycle=System.currentTimeMillis(); }
        String key(){return world+":"+x+":"+y+":"+z;}
        Location location(){World w=Bukkit.getWorld(world);return w==null?null:new Location(w,x,y,z);}
    }

    public SpawnerManager(EmeraldSMP plugin) {
        this.plugin=plugin; this.file=new File(plugin.getDataFolder(),"spawners.yml"); this.keys=new NamespacedKeyHolder(plugin);
    }

    public void load() {
        spawners.clear();
        if (!file.exists()) return;
        YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root=y.getConfigurationSection("spawners"); if(root==null)return;
        for(String key:root.getKeys(false)){
            try{
                String path="spawners."+key;
                Data d=new Data(y.getString(path+".world","world"),y.getInt(path+".x"),y.getInt(path+".y"),y.getInt(path+".z"),UUID.fromString(y.getString(path+".owner")));
                d.amount=Math.max(1,y.getInt(path+".amount",1)); d.bones=y.getLong(path+".drops.bones"); d.arrows=y.getLong(path+".drops.arrows"); d.bows=y.getLong(path+".drops.bows"); d.xp=y.getLong(path+".stored-xp");
                d.totalSpawned=y.getLong(path+".statistics.total-spawned"); d.activeMillis=y.getLong(path+".statistics.active-millis"); d.moneyGenerated=y.getLong(path+".statistics.money-generated");
                d.autoSell=y.getBoolean(path+".auto-sell",true); d.collectXp=y.getBoolean(path+".collect-xp",true);
                d.bonesEnabled=y.getBoolean(path+".preferences.bones",true); d.arrowsEnabled=y.getBoolean(path+".preferences.arrows",true); d.bowsEnabled=y.getBoolean(path+".preferences.bows",true);
                d.lastCycle=System.currentTimeMillis(); spawners.put(d.key(),d);
            }catch(Exception ignored){}
        }
    }

    public void save() {
        YamlConfiguration y=new YamlConfiguration();
        for(Data d:spawners.values()){
            String p="spawners."+d.key();
            y.set(p+".world",d.world); y.set(p+".x",d.x); y.set(p+".y",d.y); y.set(p+".z",d.z); y.set(p+".owner",d.owner.toString()); y.set(p+".amount",d.amount);
            y.set(p+".drops.bones",d.bones); y.set(p+".drops.arrows",d.arrows); y.set(p+".drops.bows",d.bows); y.set(p+".stored-xp",d.xp);
            y.set(p+".auto-sell",d.autoSell); y.set(p+".collect-xp",d.collectXp);
            y.set(p+".preferences.bones",d.bonesEnabled); y.set(p+".preferences.arrows",d.arrowsEnabled); y.set(p+".preferences.bows",d.bowsEnabled);
            y.set(p+".statistics.total-spawned",d.totalSpawned); y.set(p+".statistics.active-millis",d.activeMillis); y.set(p+".statistics.money-generated",d.moneyGenerated);
        }
        try{y.save(file);}catch(IOException ex){plugin.getLogger().warning("Could not save spawners.yml: "+ex.getMessage());}
    }

    public void start() {
        Bukkit.getScheduler().runTask(plugin,this::restoreMarkers);
        taskId=Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,this::tick,20L,20L);
    }
    public void stop(){if(taskId!=-1)Bukkit.getScheduler().cancelTask(taskId);save();}

    private void tick(){
        long now=System.currentTimeMillis();
        for(Data d:new ArrayList<>(spawners.values())){
            Location loc=d.location(); if(loc==null || !loc.getChunk().isLoaded()) continue;
            long elapsed=now-d.lastCycle;
            d.activeMillis+=Math.max(0,elapsed); d.lastCycle=now;
            long interval=plugin.getConfig().getLong("spawners.skeleton.interval-seconds",12L)*1000L;
            int amount=plugin.getConfig().getInt("spawners.skeleton.amount",8) * Math.max(1,d.amount);
            if(interval<1000)interval=1000; if(amount<1)amount=1;
            int cycles=(int)Math.min(10,elapsed/interval);
            if(cycles<=0)continue;
            for(int i=0;i<cycles;i++) produce(d,amount);
        }
        save();
    }

    private void produce(Data d,int amount){
        d.totalSpawned+=amount;
        Random random=new Random();
        long bones=0,arrows=0,bows=0;
        for(int i=0;i<amount;i++){
            bones+=random.nextInt(3); arrows+=random.nextInt(3);
            if(random.nextDouble()<0.085) bows++;
        }
        if(d.bonesEnabled) d.bones+=bones; else bones=0;
        if(d.arrowsEnabled) d.arrows+=arrows; else arrows=0;
        if(d.bowsEnabled) d.bows+=bows; else bows=0;
        if(d.collectXp) d.xp+=amount*5L;
        if(d.autoSell){
            long money=plugin.getWorthManager().value(Material.BONE,(int)Math.min(Integer.MAX_VALUE,bones))
                    +plugin.getWorthManager().value(Material.ARROW,(int)Math.min(Integer.MAX_VALUE,arrows))
                    +plugin.getWorthManager().value(Material.BOW,(int)Math.min(Integer.MAX_VALUE,bows));
            if(money>0){
                String username=Bukkit.getOfflinePlayer(d.owner).getName();
                boolean ok=plugin.getEconomyManager().depositToUuid(d.owner,money,username==null?"Unknown":username);
                if(ok){d.moneyGenerated+=money;d.bones-=bones;d.arrows-=arrows;d.bows-=bows;}
            }
        }
    }

    public ItemStack createItem(int amount){
        ItemStack item=new ItemStack(Material.SPAWNER,Math.max(1,Math.min(64,amount)));
        ItemMeta meta=item.getItemMeta(); meta.setDisplayName("§a🧟 Skeleton Spawner");
        meta.setLore(List.of("§7Produces §f" + (plugin.getConfig().getInt("spawners.skeleton.amount", 8)*Math.max(1,amount)) + " Skeletons §7every §f" + plugin.getConfig().getLong("spawners.skeleton.interval-seconds", 12) + "s","§7Stack Amount: §f"+amount+"x","§7Right-click to configure","§8Emerald SMP"));
        meta.getPersistentDataContainer().set(keys.type,PersistentDataType.STRING,TYPE_SKELETON);
        meta.getPersistentDataContainer().set(keys.stack,PersistentDataType.INTEGER,Math.max(1,amount));
        item.setItemMeta(meta); return item;
    }

    private boolean isSpawnerItem(ItemStack item){return item!=null&&item.getType()==Material.SPAWNER&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(keys.type,PersistentDataType.STRING);}
    private int getItemStackAmount(ItemStack item){
        if(!isSpawnerItem(item)) return 1;
        Integer v=item.getItemMeta().getPersistentDataContainer().get(keys.stack,PersistentDataType.INTEGER);
        return v==null?1:Math.max(1,v);
    }
    private void markBlock(Data d){
        Location loc=d.location(); if(loc==null||loc.getBlock().getType()!=Material.SPAWNER)return;
        CreatureSpawner state=(CreatureSpawner)loc.getBlock().getState();
        state.setSpawnedType(org.bukkit.entity.EntityType.SKELETON);
        state.setSpawnCount(0);
        state.setMinSpawnDelay(Integer.MAX_VALUE); state.setMaxSpawnDelay(Integer.MAX_VALUE); state.setDelay(Integer.MAX_VALUE);
        state.getPersistentDataContainer().set(keys.type,PersistentDataType.STRING,TYPE_SKELETON);
        state.getPersistentDataContainer().set(keys.stack,PersistentDataType.INTEGER,d.amount);
        state.update(true,false);
    }
    private void restoreMarkers(){for(Data d:spawners.values())markBlock(d);}

    @EventHandler public void place(BlockPlaceEvent e){
        if(!isSpawnerItem(e.getItemInHand()) || e.getBlockPlaced().getType()!=Material.SPAWNER)return;
        Block b=e.getBlockPlaced();
        Data d=new Data(b.getWorld().getName(),b.getX(),b.getY(),b.getZ(),e.getPlayer().getUniqueId());
        d.amount=Math.max(1,getItemStackAmount(e.getItemInHand()));
        CreatureSpawner state=(CreatureSpawner)b.getState();
        state.setSpawnedType(org.bukkit.entity.EntityType.SKELETON); state.setSpawnCount(0);
        state.setMinSpawnDelay(Integer.MAX_VALUE); state.setMaxSpawnDelay(Integer.MAX_VALUE); state.setDelay(Integer.MAX_VALUE);
        state.getPersistentDataContainer().set(keys.type,PersistentDataType.STRING,TYPE_SKELETON);
        state.getPersistentDataContainer().set(keys.stack,PersistentDataType.INTEGER,d.amount);
        state.update(true,false);
        spawners.put(d.key(),d); markBlock(d); save();
        e.getPlayer().sendMessage(ChatColor.GREEN+"Skeleton Spawner placed.");
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false) public void interact(PlayerInteractEvent e){
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        Block clicked=e.getClickedBlock(); if(clicked==null||clicked.getType()!=Material.SPAWNER)return;
        Data d=spawners.get(key(clicked)); if(d==null)return;
        Player p=e.getPlayer();
        if(isSpawnerItem(e.getItem())){
            if(!d.owner.equals(p.getUniqueId())&&!p.hasPermission("emerald.admin")){
                e.setCancelled(true); p.sendMessage(ChatColor.RED+"Only the owner can add to this spawner stack."); return;
            }
            e.setCancelled(true);
            ItemStack hand=e.getItem();
            if(hand.getAmount()>1) hand.setAmount(hand.getAmount()-1);
            else hand.setAmount(0);
            d.amount++;
            markBlock(d); save(); open(p,d);
            p.sendMessage(ChatColor.GREEN+"Spawner stack increased to "+d.amount+"x.");
            return;
        }
        e.setCancelled(true);
        if(!d.owner.equals(p.getUniqueId())&&!p.hasPermission("emerald.admin")){
            p.sendMessage(ChatColor.RED+"Only the owner can manage this spawner."); return;
        }
        open(p,d);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false) public void breakBlock(BlockBreakEvent e){
        Data d=spawners.get(key(e.getBlock())); if(d==null)return;
        Player p=e.getPlayer();
        if(!d.owner.equals(p.getUniqueId())&&!p.hasPermission("emerald.admin")){
            e.setCancelled(true); p.sendMessage(ChatColor.RED+"Only the owner can break this spawner."); return;
        }
        e.setCancelled(true);
        e.getBlock().setType(Material.AIR,false);
        spawners.remove(d.key());
        giveSpawnerItems(p,d.amount);
        give(p,Material.BONE,d.bones); give(p,Material.ARROW,d.arrows); give(p,Material.BOW,d.bows);
        if(d.collectXp&&d.xp>0)p.giveExp((int)Math.min(Integer.MAX_VALUE,d.xp));
        d.bones=d.arrows=d.bows=d.xp=0;
        openSpawners.values().removeIf(v->v.equals(d.key()));
        save();
        p.sendMessage(ChatColor.GREEN+"Skeleton Spawner x"+d.amount+" broken and returned.");
    }

    private String key(Block b){return b.getWorld().getName()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}

    private void open(Player p,Data d){
        Inventory inv=Bukkit.createInventory(null,54,TITLE_PREFIX);
        inv.setItem(4,item(Material.SPAWNER,"§a🧟 Skeleton Spawner",List.of("§7Amount: §f"+d.amount+"x","§7Production: §f"+(plugin.getConfig().getInt("spawners.skeleton.amount",8)*d.amount)+" Skeletons / "+plugin.getConfig().getLong("spawners.skeleton.interval-seconds",12)+" Seconds","§7Owner: §f"+Bukkit.getOfflinePlayer(d.owner).getName())));
        inv.setItem(10,item(Material.BONE,"§f🦴 Bones: §a"+d.bones,List.of(d.bonesEnabled?"§a✓ Collecting":"§c✗ Disabled")));
        inv.setItem(12,item(Material.ARROW,"§f🏹 Arrows: §a"+d.arrows,List.of(d.arrowsEnabled?"§a✓ Collecting":"§c✗ Disabled")));
        inv.setItem(14,item(Material.BOW,"§f🏹 Bows: §a"+d.bows,List.of(d.bowsEnabled?"§a✓ Collecting":"§c✗ Disabled")));
        inv.setItem(28,item(d.bonesEnabled?Material.LIME_DYE:Material.GRAY_DYE,"§fBones: "+(d.bonesEnabled?"§aON":"§cOFF"),List.of("§7Click to toggle")));
        inv.setItem(30,item(d.arrowsEnabled?Material.LIME_DYE:Material.GRAY_DYE,"§fArrows: "+(d.arrowsEnabled?"§aON":"§cOFF"),List.of("§7Click to toggle")));
        inv.setItem(32,item(d.bowsEnabled?Material.LIME_DYE:Material.GRAY_DYE,"§fBows: "+(d.bowsEnabled?"§aON":"§cOFF"),List.of("§7Click to toggle")));
        inv.setItem(37,item(d.autoSell?Material.EMERALD:Material.REDSTONE,"§f💰 Auto Sell: "+(d.autoSell?"§aON":"§cOFF"),List.of("§7Uses existing /worth values")));
        inv.setItem(39,item(d.collectXp?Material.EXPERIENCE_BOTTLE:Material.GLASS_BOTTLE,"§f✨ XP Collection: "+(d.collectXp?"§aON":"§cOFF"),List.of("§7Stored XP: §f"+d.xp)));
        inv.setItem(41,item(Material.CHEST,"§b📦 Collect Drops",List.of("§7Collect all stored item drops")));
        inv.setItem(43,item(Material.BOOK,"§f📊 Statistics",List.of("§7Stack Amount: §f"+d.amount+"x","§7Total Spawned: §f"+d.totalSpawned,"§7Time Active: §f"+formatTime(d.activeMillis),"§7Money Generated: §f"+plugin.getEconomyManager().format(d.moneyGenerated))));
        inv.setItem(49,item(Material.BARRIER,"§cPickup Spawner",List.of("§7Stored drops and XP will be given to you")));
        openSpawners.put(p.getUniqueId(), d.key());
        p.openInventory(inv);
    }

    private ItemStack item(Material m,String name,List<String> lore){
        ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.setDisplayName(name);meta.setLore(lore);i.setItemMeta(meta);return i;
    }

    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||!e.getView().getTitle().equals(TITLE_PREFIX))return;
        e.setCancelled(true);
        String openKey = openSpawners.get(p.getUniqueId());
        Data d = openKey == null ? null : spawners.get(openKey);
        if(d==null)return;
        switch(e.getRawSlot()){
            case 28 -> {d.bonesEnabled=!d.bonesEnabled;open(p,d);}
            case 30 -> {d.arrowsEnabled=!d.arrowsEnabled;open(p,d);}
            case 32 -> {d.bowsEnabled=!d.bowsEnabled;open(p,d);}
            case 37 -> {d.autoSell=!d.autoSell;open(p,d);}
            case 39 -> {d.collectXp=!d.collectXp;open(p,d);}
            case 41 -> collect(p,d);
            case 49 -> pickup(p,d);
            default -> {}
        }
        save();
    }

    private void collect(Player p,Data d){
        give(p,Material.BONE,d.bones);give(p,Material.ARROW,d.arrows);give(p,Material.BOW,d.bows);
        if(d.collectXp&&d.xp>0){p.giveExp((int)Math.min(Integer.MAX_VALUE,d.xp));d.xp=0;}
        d.bones=d.arrows=d.bows=0;p.sendMessage(ChatColor.GREEN+"Collected spawner drops.");
    }

    private void giveSpawnerItems(Player p,int amount){
        int remaining=Math.max(0,amount); while(remaining>0){int n=Math.min(64,remaining); giveItem(p,createItem(n)); remaining-=n;}
    }
    private void giveItem(Player p,ItemStack item){Map<Integer,ItemStack> left=p.getInventory().addItem(item);for(ItemStack stack:left.values())p.getWorld().dropItemNaturally(p.getLocation(),stack);}

    private void pickup(Player p,Data d){
        collect(p,d); Location l=d.location(); if(l!=null)l.getBlock().setType(Material.AIR);
        spawners.remove(d.key()); openSpawners.remove(p.getUniqueId()); giveSpawnerItems(p,d.amount); p.closeInventory(); save();
        p.sendMessage(ChatColor.GREEN+"Spawner picked up.");
    }

    private void give(Player p,Material material,long amount){while(amount>0){int n=(int)Math.min(64,amount);giveItem(p,new ItemStack(material,n));amount-=n;}}

    private String formatTime(long ms){long sec=ms/1000;long h=sec/3600;long m=(sec%3600)/60;long s=sec%60;return h+"h "+m+"m "+s+"s";}
}
