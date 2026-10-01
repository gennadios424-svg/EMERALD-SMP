package net.emeraldsmp.spawner;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SpawnerManager implements Listener {
    public static final String TYPE_SKELETON="skeleton";
    private static final String MAIN="§2§l🧟 SKELETON SPAWNER";
    private static final String SETTINGS="§2§l⚙ SPAWNER SETTINGS";

    private final EmeraldSMP plugin;
    private final File file;
    private final Map<String,Data> spawners=new ConcurrentHashMap<>();
    private final Map<UUID,String> open=new HashMap<>();
    private final NamespacedKey typeKey,stackKey;
    private BukkitTask task;

    public static final class Data {
        String world; int x,y,z; UUID owner; int amount=1;
        long bones,arrows,bows,lastCycle;
        boolean bonesEnabled=true,arrowsEnabled=true,bowsEnabled=true;
        Data(String world,int x,int y,int z,UUID owner){
            this.world=world;this.x=x;this.y=y;this.z=z;this.owner=owner;this.lastCycle=System.currentTimeMillis();
        }
        String key(){return world+":"+x+":"+y+":"+z;}
        Location loc(){World w=Bukkit.getWorld(world);return w==null?null:new Location(w,x,y,z);}
    }

    public SpawnerManager(EmeraldSMP plugin){
        this.plugin=plugin;
        this.file=new File(plugin.getDataFolder(),"spawners.yml");
        this.typeKey=new NamespacedKey(plugin,"emerald-spawner");
        this.stackKey=new NamespacedKey(plugin,"emerald-spawner-stack");
    }

    public void load(){
        spawners.clear();
        if(!file.exists())return;
        YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root=y.getConfigurationSection("spawners");
        if(root==null)return;
        for(String k:root.getKeys(false)){
            try{
                String p="spawners."+k;
                String owner=y.getString(p+".owner");
                if(owner==null)continue;
                Data d=new Data(
                    y.getString(p+".world","world"),
                    y.getInt(p+".x"),y.getInt(p+".y"),y.getInt(p+".z"),
                    UUID.fromString(owner)
                );
                d.amount=Math.max(1,Math.min(64,y.getInt(p+".amount",1)));
                d.bones=Math.max(0,y.getLong(p+".drops.bones",0));
                d.arrows=Math.max(0,y.getLong(p+".drops.arrows",0));
                d.bows=Math.max(0,y.getLong(p+".drops.bows",0));
                d.lastCycle=y.getLong(p+".last-cycle",System.currentTimeMillis());
                d.bonesEnabled=y.getBoolean(p+".drops-enabled.bones",true);
                d.arrowsEnabled=y.getBoolean(p+".drops-enabled.arrows",true);
                d.bowsEnabled=y.getBoolean(p+".drops-enabled.bows",true);
                spawners.put(d.key(),d);
            }catch(Exception ex){
                plugin.getLogger().warning("Skipped invalid spawner entry: "+k);
            }
        }
    }

    public synchronized void save(){
        YamlConfiguration y=new YamlConfiguration();
        for(Data d:spawners.values()){
            String p="spawners."+d.key();
            y.set(p+".world",d.world);y.set(p+".x",d.x);y.set(p+".y",d.y);y.set(p+".z",d.z);
            y.set(p+".owner",d.owner.toString());y.set(p+".amount",d.amount);
            y.set(p+".drops.bones",d.bones);y.set(p+".drops.arrows",d.arrows);y.set(p+".drops.bows",d.bows);
            y.set(p+".last-cycle",d.lastCycle);
            y.set(p+".drops-enabled.bones",d.bonesEnabled);
            y.set(p+".drops-enabled.arrows",d.arrowsEnabled);
            y.set(p+".drops-enabled.bows",d.bowsEnabled);
        }
        try{y.save(file);}catch(IOException e){plugin.getLogger().warning("Could not save spawners.yml: "+e.getMessage());}
    }

    public void start(){
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20L,20L);
    }

    public void stop(){
        if(task!=null)task.cancel();
        save();
    }

    private void tick(){
        long now=System.currentTimeMillis();
        long interval=Math.max(1000L,plugin.getConfig().getLong("spawners.skeleton.interval-seconds",12L)*1000L);
        int base=Math.max(1,plugin.getConfig().getInt("spawners.skeleton.amount",8));
        boolean changed=false;
        for(Data d:new ArrayList<>(spawners.values())){
            long elapsed=Math.max(0,now-d.lastCycle);
            long cycles=Math.min(100,elapsed/interval);
            if(cycles<=0)continue;
            d.lastCycle+=cycles*interval;
            produce(d,(long)base*Math.max(1,d.amount),cycles);
            changed=true;
        }
        if(changed)save();
    }

    private void produce(Data d,long mobs,long cycles){
        long total;
        try{total=Math.multiplyExact(mobs,cycles);}catch(ArithmeticException ex){total=Long.MAX_VALUE;}
        if(total<=0)return;
        Random random=ThreadLocalRandom.current();
        long bones=0,arrows=0,bows=0;
        for(long i=0;i<total;i++){
            bones+=1+random.nextInt(3);
            arrows+=1+random.nextInt(3);
            if(random.nextDouble()<0.085D)bows++;
            if(bones>Long.MAX_VALUE-3||arrows>Long.MAX_VALUE-3||bows>Long.MAX_VALUE-3)break;
        }
        if(d.bonesEnabled)d.bones=safeAdd(d.bones,bones);
        if(d.arrowsEnabled)d.arrows=safeAdd(d.arrows,arrows);
        if(d.bowsEnabled)d.bows=safeAdd(d.bows,bows);
    }

    private long safeAdd(long a,long b){
        if(b<=0)return a;
        return Long.MAX_VALUE-a<b?Long.MAX_VALUE:a+b;
    }

    public ItemStack createItem(int stack){
        stack=Math.max(1,Math.min(64,stack));
        ItemStack item=new ItemStack(Material.SPAWNER);
        ItemMeta m=item.getItemMeta();
        int rate=plugin.getConfig().getInt("spawners.skeleton.amount",8);
        long sec=plugin.getConfig().getLong("spawners.skeleton.interval-seconds",12);
        m.setDisplayName("§a§l🧟 EMERALD SKELETON SPAWNER");
        m.setLore(List.of(
            "§7Physical Emerald SMP spawner",
            "§e⚡ "+rate*stack+" Skeletons §7/ §f"+sec+"s",
            "§7Stack: §a"+stack+"x",
            "§8Right-click to manage"
        ));
        m.getPersistentDataContainer().set(typeKey,PersistentDataType.STRING,TYPE_SKELETON);
        m.getPersistentDataContainer().set(stackKey,PersistentDataType.INTEGER,stack);
        item.setItemMeta(m);
        item.setAmount(1);
        return item;
    }

    private boolean isItem(ItemStack i){
        return i!=null&&i.getType()==Material.SPAWNER&&i.hasItemMeta()
            &&i.getItemMeta().getPersistentDataContainer().has(typeKey,PersistentDataType.STRING);
    }

    private int itemStack(ItemStack i){
        if(!isItem(i))return 1;
        Integer n=i.getItemMeta().getPersistentDataContainer().get(stackKey,PersistentDataType.INTEGER);
        return Math.max(1,n==null?1:n);
    }

    private String key(Block b){return b.getWorld().getName()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}

    private void markVanillaSpawner(Data d){
        Location l=d.loc();
        if(l==null||l.getBlock().getType()!=Material.SPAWNER)return;
        BlockState state=l.getBlock().getState();
        if(state instanceof CreatureSpawner cs){
            cs.setSpawnedType(EntityType.SKELETON);
            cs.setMinSpawnDelay(Integer.MAX_VALUE);
            cs.setMaxSpawnDelay(Integer.MAX_VALUE);
            cs.setDelay(Integer.MAX_VALUE);
            cs.update(true,false);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void place(BlockPlaceEvent e){
        if(!isItem(e.getItemInHand())||e.getBlockPlaced().getType()!=Material.SPAWNER)return;
        Player p=e.getPlayer();Block b=e.getBlockPlaced();int incoming=itemStack(e.getItemInHand());
        Data existing=null;
        for(BlockFace face:BlockFace.values()){
            if(face==BlockFace.SELF)continue;
            Data candidate=spawners.get(key(b.getRelative(face)));
            if(candidate!=null&&candidate.owner.equals(p.getUniqueId())){existing=candidate;break;}
        }
        if(existing!=null){
            b.setType(Material.AIR,false);
            existing.amount=Math.min(64,existing.amount+incoming);
            markVanillaSpawner(existing);save();open(p,existing);
            p.sendMessage("§a🧟 Spawner stack: §f"+existing.amount+"x");
            return;
        }
        Data d=new Data(b.getWorld().getName(),b.getX(),b.getY(),b.getZ(),p.getUniqueId());
        d.amount=incoming;spawners.put(d.key(),d);markVanillaSpawner(d);save();open(p,d);
        p.sendMessage("§a🧟 Emerald Skeleton Spawner placed.");
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent e){
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        Block b=e.getClickedBlock();
        if(b==null||b.getType()!=Material.SPAWNER)return;
        Data d=spawners.get(key(b));if(d==null)return;
        e.setCancelled(true);
        Player p=e.getPlayer();
        if(isItem(e.getItem())){
            if(!d.owner.equals(p.getUniqueId())&&!p.isOp()){p.sendMessage("§cOnly the owner can add to this spawner.");return;}
            int n=itemStack(e.getItem());
            d.amount=Math.min(64,d.amount+n);
            e.getItem().setAmount(Math.max(0,e.getItem().getAmount()-1));
            markVanillaSpawner(d);save();open(p,d);return;
        }
        if(!d.owner.equals(p.getUniqueId())&&!p.isOp()){p.sendMessage("§cOnly the owner can manage this spawner.");return;}
        open(p,d);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void breakBlock(BlockBreakEvent e){
        Data d=spawners.get(key(e.getBlock()));if(d==null)return;
        Player p=e.getPlayer();
        if(!d.owner.equals(p.getUniqueId())&&!p.isOp()){e.setCancelled(true);p.sendMessage("§cOnly the owner can break this spawner.");return;}
        Material tool=p.getInventory().getItemInMainHand().getType();
        if(!isPickaxe(tool)){e.setCancelled(true);p.sendMessage("§cUse a pickaxe to break an Emerald Spawner.");return;}
        e.setDropItems(false);
        spawners.remove(d.key());save();
        giveOrDrop(p,createItem(d.amount));
        giveOrDrop(p,new ItemStack(Material.BONE,(int)Math.min(64,d.bones)));d.bones=Math.max(0,d.bones-64);
        giveStored(p,Material.BONE,d.bones);
        giveStored(p,Material.ARROW,d.arrows);
        giveStored(p,Material.BOW,d.bows);
        p.sendMessage("§a🧟 Spawner broken. Stored drops returned.");
    }

    private boolean isPickaxe(Material m){
        return m==Material.WOODEN_PICKAXE||m==Material.STONE_PICKAXE||m==Material.IRON_PICKAXE
            ||m==Material.DIAMOND_PICKAXE||m==Material.NETHERITE_PICKAXE;
    }

    private void giveStored(Player p,Material mat,long amount){
        long left=amount;
        while(left>0){
            int n=(int)Math.min(mat.getMaxStackSize(),left);
            ItemStack stack=new ItemStack(mat,n);
            HashMap<Integer,ItemStack> rem=p.getInventory().addItem(stack);
            if(rem.isEmpty()){left-=n;continue;}
            int remaining=0;for(ItemStack x:rem.values())remaining+=x.getAmount();
            left-=n-remaining;
            for(ItemStack x:rem.values())p.getWorld().dropItemNaturally(p.getLocation(),x);
            break;
        }
    }

    private void giveOrDrop(Player p,ItemStack item){
        for(ItemStack x:p.getInventory().addItem(item).values())p.getWorld().dropItemNaturally(p.getLocation(),x);
    }

    private void open(Player p,Data d){
        open.put(p.getUniqueId(),d.key());
        Inventory inv=Bukkit.createInventory(null,36,MAIN);
        fill(inv);
        long rate=(long)plugin.getConfig().getInt("spawners.skeleton.amount",8)*d.amount;
        long sec=plugin.getConfig().getLong("spawners.skeleton.interval-seconds",12);
        inv.setItem(10,item(Material.SPAWNER,"§a§l🧟 SKELETON SPAWNER",List.of(
            "§7Stack: §f"+d.amount+"x","§e⚡ Rate: §f"+rate+" Skeletons / "+sec+"s"
        )));
        inv.setItem(12,item(Material.CHEST,"§a§l📦 STORED DROPS",List.of(
            "§f🦴 Bones: §a"+fmt(d.bones),
            "§f🏹 Arrows: §a"+fmt(d.arrows),
            "§f🏹 Bows: §a"+fmt(d.bows)
        )));
        inv.setItem(20,item(Material.CHEST,"§a§l📦 COLLECT DROPS",List.of("§7Move stored physical drops to inventory","§7Overflow stays as ground items")));
        inv.setItem(24,item(Material.COMPARATOR,"§b§l⚙ SETTINGS",List.of("§7Choose which physical drops are stored")));
        inv.setItem(31,item(Material.BARRIER,"§c✕ CLOSE",List.of()));
        p.openInventory(inv);
    }

    private void settings(Player p,Data d){
        Inventory inv=Bukkit.createInventory(null,27,SETTINGS);
        fill(inv);
        inv.setItem(11,item(Material.BONE,"§f🦴 Bones: "+on(d.bonesEnabled),List.of("§7Click to toggle")));
        inv.setItem(13,item(Material.ARROW,"§f🏹 Arrows: "+on(d.arrowsEnabled),List.of("§7Click to toggle")));
        inv.setItem(15,item(Material.BOW,"§f🏹 Bows: "+on(d.bowsEnabled),List.of("§7Click to toggle")));
        inv.setItem(22,item(Material.ARROW,"§a← BACK",List.of()));
        p.openInventory(inv);
    }

    private String on(boolean b){return b?"§aON":"§cOFF";}

    private ItemStack item(Material mat,String name,List<String> lore){
        ItemStack i=new ItemStack(mat);ItemMeta m=i.getItemMeta();m.setDisplayName(name);m.setLore(lore);i.setItemMeta(m);return i;
    }

    private void fill(Inventory inv){
        ItemStack pane=item(Material.GRAY_STAINED_GLASS_PANE,"§r",List.of());
        for(int i=0;i<inv.getSize();i++)if(inv.getItem(i)==null)inv.setItem(i,pane.clone());
    }

    private String fmt(long n){return String.format(Locale.US,"%,d",n);}

    private void collect(Player p,Data d){
        d.bones=transfer(p,Material.BONE,d.bones);
        d.arrows=transfer(p,Material.ARROW,d.arrows);
        d.bows=transfer(p,Material.BOW,d.bows);
        save();p.sendMessage("§a📦 Stored drops collected.");
    }

    private long transfer(Player p,Material mat,long amount){
        long left=amount;
        while(left>0){
            int n=(int)Math.min(mat.getMaxStackSize(),left);
            ItemStack stack=new ItemStack(mat,n);
            HashMap<Integer,ItemStack> rem=p.getInventory().addItem(stack);
            if(rem.isEmpty()){left-=n;continue;}
            int remaining=0;for(ItemStack x:rem.values())remaining+=x.getAmount();
            left-=n-remaining;
            break;
        }
        return left;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String title=e.getView().getTitle();
        if(!MAIN.equals(title)&&!SETTINGS.equals(title))return;
        e.setCancelled(true);
        if(e.getClickedInventory()!=e.getView().getTopInventory())return;
        Data d=null;String k=open.get(p.getUniqueId());if(k!=null)d=spawners.get(k);
        if(d==null){p.closeInventory();return;}
        if(MAIN.equals(title)){
            switch(e.getRawSlot()){
                case 20 -> {collect(p,d);open(p,d);}
                case 24 -> settings(p,d);
                case 31 -> p.closeInventory();
            }
        }else{
            switch(e.getRawSlot()){
                case 11 -> d.bonesEnabled=!d.bonesEnabled;
                case 13 -> d.arrowsEnabled=!d.arrowsEnabled;
                case 15 -> d.bowsEnabled=!d.bowsEnabled;
                case 22 -> {open(p,d);return;}
            }
            save();settings(p,d);
        }
    }

    @EventHandler public void drag(InventoryDragEvent e){
        String t=e.getView().getTitle();
        if(MAIN.equals(t)||SETTINGS.equals(t))e.setCancelled(true);
    }

    @EventHandler public void close(InventoryCloseEvent e){open.remove(e.getPlayer().getUniqueId());}
}
