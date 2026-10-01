package net.emeraldsmp.spawner;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class SpawnerManager implements Listener {
    public static final String TYPE_SKELETON="skeleton";
    private static final String MAIN="§2§l🧟 SKELETON SPAWNER";
    private static final String SETTINGS="§2§l⚙ SPAWNER SETTINGS";
    private final EmeraldSMP plugin;
    private final File file;
    private final Map<String,Data> spawners=new LinkedHashMap<>();
    private final Map<UUID,String> open=new HashMap<>();
    private final NamespacedKey typeKey,stackKey,holoKey;
    private BukkitTask task;

    public static final class Data {
        String world; int x,y,z; UUID owner; int amount=1;
        long bones,arrows,bows,moneyGenerated,lastCycle;
        boolean autoSell=true,bonesEnabled=true,arrowsEnabled=true,bowsEnabled=true;
        Data(String world,int x,int y,int z,UUID owner){this.world=world;this.x=x;this.y=y;this.z=z;this.owner=owner;this.lastCycle=System.currentTimeMillis();}
        String key(){return world+":"+x+":"+y+":"+z;}
        Location loc(){World w=Bukkit.getWorld(world);return w==null?null:new Location(w,x,y,z);}
    }

    public SpawnerManager(EmeraldSMP plugin){
        this.plugin=plugin;file=new File(plugin.getDataFolder(),"spawners.yml");
        typeKey=new NamespacedKey(plugin,"emerald-spawner");stackKey=new NamespacedKey(plugin,"emerald-spawner-stack");holoKey=new NamespacedKey(plugin,"emerald-spawner-hologram");
    }

    public void load(){
        spawners.clear();if(!file.exists())return;
        YamlConfiguration y=YamlConfiguration.loadConfiguration(file);ConfigurationSection root=y.getConfigurationSection("spawners");if(root==null)return;
        for(String k:root.getKeys(false))try{
            String p="spawners."+k;String owner=y.getString(p+".owner");if(owner==null)continue;
            Data d=new Data(y.getString(p+".world","world"),y.getInt(p+".x"),y.getInt(p+".y"),y.getInt(p+".z"),UUID.fromString(owner));
            d.amount=Math.max(1,y.getInt(p+".amount",1));d.bones=Math.max(0,y.getLong(p+".drops.bones",0));d.arrows=Math.max(0,y.getLong(p+".drops.arrows",0));d.bows=Math.max(0,y.getLong(p+".drops.bows",0));d.moneyGenerated=Math.max(0,y.getLong(p+".money-generated",0));d.lastCycle=y.getLong(p+".last-cycle",System.currentTimeMillis());
            d.autoSell=y.getBoolean(p+".auto-sell",true);d.bonesEnabled=y.getBoolean(p+".drops-enabled.bones",true);d.arrowsEnabled=y.getBoolean(p+".drops-enabled.arrows",true);d.bowsEnabled=y.getBoolean(p+".drops-enabled.bows",true);
            spawners.put(d.key(),d);
        }catch(Exception ex){plugin.getLogger().warning("Skipped invalid spawner entry: "+k);}
    }

    public void save(){
        YamlConfiguration y=new YamlConfiguration();
        for(Data d:spawners.values()){
            String p="spawners."+d.key();y.set(p+".world",d.world);y.set(p+".x",d.x);y.set(p+".y",d.y);y.set(p+".z",d.z);y.set(p+".owner",d.owner.toString());y.set(p+".amount",d.amount);
            y.set(p+".drops.bones",d.bones);y.set(p+".drops.arrows",d.arrows);y.set(p+".drops.bows",d.bows);y.set(p+".money-generated",d.moneyGenerated);y.set(p+".last-cycle",d.lastCycle);
            y.set(p+".auto-sell",d.autoSell);y.set(p+".drops-enabled.bones",d.bonesEnabled);y.set(p+".drops-enabled.arrows",d.arrowsEnabled);y.set(p+".drops-enabled.bows",d.bowsEnabled);
        }
        try{y.save(file);}catch(IOException e){plugin.getLogger().warning("Could not save spawners.yml: "+e.getMessage());}
    }

    public void start(){Bukkit.getScheduler().runTask(plugin,this::restoreAll);task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20L,20L);}
    public void stop(){if(task!=null)task.cancel();removeAllHolograms();save();}

    private void tick(){
        long now=System.currentTimeMillis();
        long interval=Math.max(1000L,plugin.getConfig().getLong("spawners.skeleton.interval-seconds",12L)*1000L);
        int base=Math.max(1,plugin.getConfig().getInt("spawners.skeleton.amount",8));
        boolean changed=false;
        for(Data d:new ArrayList<>(spawners.values())){
            long elapsed=Math.max(0,now-d.lastCycle);
            long cycles=Math.min(1000,elapsed/interval);
            if(cycles<=0)continue;
            d.lastCycle+=cycles*interval;produce(d,(long)base*Math.max(1,d.amount),cycles);changed=true;
        }
        if(changed)save();
    }

    private void produce(Data d,long mobs,long cycles){
        long total;
        try{total=Math.multiplyExact(mobs,cycles);}catch(ArithmeticException e){total=Long.MAX_VALUE;}
        if(total<=0)return;
        Random r=new Random();
        long bones=0,arrows=0,bows=0;
        // Minecraft-like skeleton drops, generated virtually rather than spawning entities.
        for(long i=0;i<total;i++){
            bones+=1+r.nextInt(3);
            arrows+=1+r.nextInt(3);
            if(r.nextDouble()<0.085D)bows++;
            if(bones>Long.MAX_VALUE-3||arrows>Long.MAX_VALUE-3||bows>Long.MAX_VALUE-3)break;
        }
        if(d.bonesEnabled){d.bones=safeAdd(d.bones,bones);}
        if(d.arrowsEnabled){d.arrows=safeAdd(d.arrows,arrows);}
        if(d.bowsEnabled){d.bows=safeAdd(d.bows,bows);}
        if(d.autoSell){
            long money=worth(Material.BONE,d.bonesEnabled?bones:0)+worth(Material.ARROW,d.arrowsEnabled?arrows:0)+worth(Material.BOW,d.bowsEnabled?bows:0);
            if(money>0 && plugin.getEconomyManager().depositToUuid(d.owner,money,Optional.ofNullable(Bukkit.getOfflinePlayer(d.owner).getName()).orElse("Unknown"))){
                d.moneyGenerated=safeAdd(d.moneyGenerated,money);
                if(d.bonesEnabled)d.bones-=Math.min(d.bones,bones);
                if(d.arrowsEnabled)d.arrows-=Math.min(d.arrows,arrows);
                if(d.bowsEnabled)d.bows-=Math.min(d.bows,bows);
            }
        }
    }

    private long worth(Material m,long amount){
        long remaining=amount,total=0;
        while(remaining>0){
            int n=(int)Math.min(Integer.MAX_VALUE,remaining);long v=plugin.getWorthManager().sellValue(m,n);
            if(v<=0||Long.MAX_VALUE-total<v)return Long.MAX_VALUE;
            total+=v;remaining-=n;
        }
        return total;
    }
    private long safeAdd(long a,long b){if(b<=0)return a;return Long.MAX_VALUE-a<b?Long.MAX_VALUE:a+b;}

    public ItemStack createItem(int stack){
        stack=Math.max(1,Math.min(64,stack));ItemStack item=new ItemStack(Material.SPAWNER);ItemMeta m=item.getItemMeta();
        int rate=plugin.getConfig().getInt("spawners.skeleton.amount",8);long sec=plugin.getConfig().getLong("spawners.skeleton.interval-seconds",12);
        m.setDisplayName("§a🧟 EMERALD SKELETON SPAWNER");
        m.setLore(List.of("§7Custom Emerald Spawner","§f"+rate*stack+" Skeletons §7/ §f"+sec+"s","§7Stack: §a"+stack+"x","§8Right-click to manage"));
        m.getPersistentDataContainer().set(typeKey,PersistentDataType.STRING,TYPE_SKELETON);m.getPersistentDataContainer().set(stackKey,PersistentDataType.INTEGER,stack);item.setItemMeta(m);item.setAmount(1);return item;
    }

    private boolean isItem(ItemStack i){return i!=null&&i.getType()==Material.SPAWNER&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(typeKey,PersistentDataType.STRING);}
    private int itemStack(ItemStack i){Integer n=isItem(i)?i.getItemMeta().getPersistentDataContainer().get(stackKey,PersistentDataType.INTEGER):1;return Math.max(1,n==null?1:n);}
    private String key(Block b){return b.getWorld().getName()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}

    private void mark(Data d){
        Location l=d.loc();if(l==null||l.getBlock().getType()!=Material.SPAWNER)return;
        BlockState s=l.getBlock().getState();if(s instanceof CreatureSpawner cs){
            cs.setSpawnedType(EntityType.SKELETON);cs.setSpawnCount(0);cs.setMinSpawnDelay(Integer.MAX_VALUE);cs.setMaxSpawnDelay(Integer.MAX_VALUE);cs.setDelay(Integer.MAX_VALUE);
            cs.getPersistentDataContainer().set(typeKey,PersistentDataType.STRING,TYPE_SKELETON);cs.getPersistentDataContainer().set(stackKey,PersistentDataType.INTEGER,d.amount);cs.update(true,false);
        }
        hologram(d);
    }

    private void hologram(Data d){
        Location l=d.loc();if(l==null||!l.getChunk().isLoaded())return;
        removeHologramNear(l);
        ArmorStand a=(ArmorStand)l.getWorld().spawnEntity(l.clone().add(.5,1.9,.5),EntityType.ARMOR_STAND);
        a.setInvisible(true);a.setMarker(true);a.setGravity(false);a.setInvulnerable(true);a.setSilent(true);a.setCustomNameVisible(true);
        long rate=(long)plugin.getConfig().getInt("spawners.skeleton.amount",8)*d.amount;long sec=plugin.getConfig().getLong("spawners.skeleton.interval-seconds",12);
        a.setCustomName("§2🧟 SKELETON SPAWNER §8• §f⚡ "+rate+" Skeletons / "+sec+"s §8• §7Right-Click to Manage");
        a.getPersistentDataContainer().set(holoKey,PersistentDataType.STRING,d.key());
    }

    private void removeHologramNear(Location l){
        if(l==null||!l.getChunk().isLoaded())return;
        for(Entity e:l.getWorld().getNearbyEntities(l.clone().add(.5,2,.5),1.2,2.5,1.2))if(e instanceof ArmorStand a&&a.getPersistentDataContainer().has(holoKey,PersistentDataType.STRING))a.remove();
    }
    private void removeAllHolograms(){for(World w:Bukkit.getWorlds())for(Entity e:w.getEntities())if(e instanceof ArmorStand a&&a.getPersistentDataContainer().has(holoKey,PersistentDataType.STRING))a.remove();}
    private void restoreAll(){for(Data d:spawners.values())mark(d);}

    @EventHandler public void chunkLoad(ChunkLoadEvent e){
        for(Data d:spawners.values())if(d.world.equals(e.getWorld().getName())&&d.x>>4==e.getChunk().getX()&&d.z>>4==e.getChunk().getZ())mark(d);
    }

    @EventHandler(priority=EventPriority.HIGHEST) public void place(BlockPlaceEvent e){
        if(!isItem(e.getItemInHand())||e.getBlockPlaced().getType()!=Material.SPAWNER)return;
        Player p=e.getPlayer();Block b=e.getBlockPlaced();int incoming=itemStack(e.getItemInHand());Data target=null;
        for(BlockFace f:BlockFace.values())if(f.getModX()+f.getModY()+f.getModZ()!=0){
            Data c=spawners.get(key(b.getRelative(f)));if(c!=null&&c.owner.equals(p.getUniqueId())){target=c;break;}
        }
        if(target!=null){
            b.setType(Material.AIR,false);target.amount=Math.min(64,target.amount+incoming);mark(target);save();p.sendMessage("§a🧟 Spawner stack: §f"+target.amount+"x");open(p,target);return;
        }
        Data d=new Data(b.getWorld().getName(),b.getX(),b.getY(),b.getZ(),p.getUniqueId());d.amount=incoming;spawners.put(d.key(),d);mark(d);save();p.sendMessage("§a🧟 Emerald Skeleton Spawner placed.");open(p,d);
    }

    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent e){
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;Block b=e.getClickedBlock();if(b==null||b.getType()!=Material.SPAWNER)return;Data d=spawners.get(key(b));if(d==null)return;
        e.setCancelled(true);Player p=e.getPlayer();
        if(isItem(e.getItem())){
            if(!d.owner.equals(p.getUniqueId())&&!p.isOp()){p.sendMessage("§cOnly the owner can add to this spawner.");return;}
            int n=itemStack(e.getItem());d.amount=Math.min(64,d.amount+n);e.getItem().setAmount(Math.max(0,e.getItem().getAmount()-1));mark(d);save();open(p,d);return;
        }
        if(!d.owner.equals(p.getUniqueId())&&!p.isOp()){p.sendMessage("§cOnly the owner can manage this spawner.");return;}
        open(p,d);
    }

    @EventHandler(priority=EventPriority.HIGHEST) public void breakBlock(BlockBreakEvent e){
        Data d=spawners.get(key(e.getBlock()));if(d==null)return;Player p=e.getPlayer();
        if(!d.owner.equals(p.getUniqueId())&&!p.isOp()){e.setCancelled(true);p.sendMessage("§cOnly the owner can break this spawner.");return;}
        Material tool=p.getInventory().getItemInMainHand().getType();
        if(tool!=Material.WOODEN_PICKAXE&&tool!=Material.STONE_PICKAXE&&tool!=Material.IRON_PICKAXE&&tool!=Material.DIAMOND_PICKAXE&&tool!=Material.NETHERITE_PICKAXE){
            e.setCancelled(true);p.sendMessage("§cUse a pickaxe to break an Emerald Spawner.");return;
        }
        e.setDropItems(false);removeHologramNear(e.getBlock().getLocation());
        ItemStack item=createItem(d.amount);HashMap<Integer,ItemStack> left=p.getInventory().addItem(item);
        for(ItemStack i:left.values())e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(),i);
        spawners.remove(d.key());save();
    }

    private void open(Player p,Data d){
        open.put(p.getUniqueId(),d.key());Inventory inv=Bukkit.createInventory(null,36,MAIN);fill(inv,Material.GRAY_STAINED_GLASS_PANE,"§r");
        long rate=(long)plugin.getConfig().getInt("spawners.skeleton.amount",8)*d.amount;long sec=plugin.getConfig().getLong("spawners.skeleton.interval-seconds",12);
        inv.setItem(10,item(Material.SPAWNER,"§a🧟 SKELETON SPAWNER",List.of("§7Custom Emerald Spawner","§f"+d.amount+"x §7stack","§e⚡ "+rate+" Skeletons / "+sec+"s")));
        inv.setItem(12,item(Material.BONE,"§f📦 STORED DROPS",List.of("§f🦴 Bones: §a"+fmt(d.bones),"§f🏹 Arrows: §a"+fmt(d.arrows),"§f🏹 Bows: §a"+fmt(d.bows))));
        inv.setItem(14,item(Material.GOLD_INGOT,"§6💰 MONEY GENERATED",List.of("§f"+plugin.getEconomyManager().format(d.moneyGenerated),"§7Auto Sell: "+(d.autoSell?"§aON":"§cOFF"))));
        inv.setItem(20,item(Material.CHEST,"§a📦 COLLECT DROPS",List.of("§7Move stored drops into your inventory")));
        inv.setItem(22,item(Material.GOLD_INGOT,"§6💰 COLLECT MONEY",List.of("§7Collect "+plugin.getEconomyManager().format(d.moneyGenerated))));
        inv.setItem(24,item(Material.COMPARATOR,"§b⚙ SETTINGS",List.of("§7Auto Sell and drop toggles")));
        inv.setItem(31,item(Material.BARRIER,"§c✕ CLOSE",List.of("§7Close this menu")));
        p.openInventory(inv);
    }

    private void settings(Player p,Data d){
        Inventory inv=Bukkit.createInventory(null,27,SETTINGS);fill(inv,Material.GRAY_STAINED_GLASS_PANE,"§r");
        inv.setItem(11,item(Material.GOLD_INGOT,"§6💰 Auto Sell: "+(d.autoSell?"§aON":"§cOFF"),List.of("§7Click to toggle")));
        inv.setItem(13,item(Material.BONE,"§f🦴 Bones: "+(d.bonesEnabled?"§aON":"§cOFF"),List.of("§7Click to toggle")));
        inv.setItem(14,item(Material.ARROW,"§f🏹 Arrows: "+(d.arrowsEnabled?"§aON":"§cOFF"),List.of("§7Click to toggle")));
        inv.setItem(15,item(Material.BOW,"§f🏹 Bows: "+(d.bowsEnabled?"§aON":"§cOFF"),List.of("§7Click to toggle")));
        inv.setItem(22,item(Material.ARROW,"§a← BACK",List.of("§7Return to spawner")));
        p.openInventory(inv);
    }

    private ItemStack item(Material m,String name,List<String> lore){ItemStack i=new ItemStack(m);ItemMeta x=i.getItemMeta();x.setDisplayName(name);x.setLore(lore);i.setItemMeta(x);return i;}
    private void fill(Inventory i,Material m,String name){ItemStack x=item(m,name,List.of());for(int s=0;s<i.getSize();s++)if(i.getItem(s)==null)i.setItem(s,x.clone());}
    private String fmt(long n){return String.format(Locale.US,"%,d",n);}

    private void collectDrops(Player p,Data d){
        long beforeB=d.bones,beforeA=d.arrows,beforeBo=d.bows;
        d.bones=transfer(p,Material.BONE,d.bones);d.arrows=transfer(p,Material.ARROW,d.arrows);d.bows=transfer(p,Material.BOW,d.bows);
        save();p.sendMessage("§a📦 Collected stored drops.");
    }
    private long transfer(Player p,Material mat,long amount){
        long left=amount;
        while(left>0){
            int n=(int)Math.min(mat.getMaxStackSize(),left);ItemStack stack=new ItemStack(mat,n);HashMap<Integer,ItemStack> rem=p.getInventory().addItem(stack);
            if(rem.isEmpty()){left-=n;continue;}
            int remaining=0;for(ItemStack i:rem.values())remaining+=i.getAmount();left-=n-remaining;break;
        }
        return left;
    }

    private void collectMoney(Player p,Data d){
        long amount=d.moneyGenerated;if(amount<=0){p.sendMessage("§7No money is stored.");return;}
        if(plugin.getEconomyManager().deposit(p.getUniqueId(),amount)){d.moneyGenerated=0;save();p.sendMessage("§a💰 Collected "+plugin.getEconomyManager().format(amount));}
        else p.sendMessage("§cMoney could not be collected.");
    }

    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;String title=e.getView().getTitle();if(!MAIN.equals(title)&&!SETTINGS.equals(title))return;
        e.setCancelled(true);if(e.getClickedInventory()!=e.getView().getTopInventory())return;
        String k=open.get(p.getUniqueId());Data d=k==null?null:spawners.get(k);if(d==null){p.closeInventory();return;}
        if(MAIN.equals(title)){
            switch(e.getRawSlot()){
                case 20->collectDrops(p,d);
                case 22->collectMoney(p,d);
                case 24->settings(p,d);
                case 31->p.closeInventory();
            }
            if(e.getRawSlot()==20||e.getRawSlot()==22)open(p,d);
        }else{
            switch(e.getRawSlot()){
                case 11->d.autoSell=!d.autoSell;
                case 13->d.bonesEnabled=!d.bonesEnabled;
                case 14->d.arrowsEnabled=!d.arrowsEnabled;
                case 15->d.bowsEnabled=!d.bowsEnabled;
                case 22->open(p,d);
            }
            save();if(e.getRawSlot()!=22)settings(p,d);
        }
    }
    @EventHandler public void drag(InventoryDragEvent e){if(MAIN.equals(e.getView().getTitle())||SETTINGS.equals(e.getView().getTitle()))e.setCancelled(true);}
    @EventHandler public void close(InventoryCloseEvent e){open.remove(e.getPlayer().getUniqueId());}
}
