package net.emeraldsmp.crates;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.io.*;
import java.util.*;

public final class CrateManager implements Listener {
    public static final List<String> TYPES=List.of("common","spawner","gold","crimson","emerald");
    private final EmeraldSMP plugin;
    private final File file;
    private final Map<String,String> crates=new LinkedHashMap<>();
    private final Map<String,List<UUID>> holograms=new HashMap<>();
    private final Map<UUID,Pending> pending=new HashMap<>();
    private final Map<UUID,BukkitTask> animations=new HashMap<>();
    private final Set<UUID> opening=new HashSet<>();
    private final Map<UUID,String> editors=new HashMap<>();
    private final NamespacedKey keyKey,holoKey;
    private final Random random=new Random();
    private record Pending(String type,String reward){}
    public CrateManager(EmeraldSMP plugin){
        this.plugin=plugin; file=new File(plugin.getDataFolder(),"crates.yml");
        keyKey=new NamespacedKey(plugin,"emerald-crate-key"); holoKey=new NamespacedKey(plugin,"emerald-crate-hologram");
    }
    public void load(){
        crates.clear(); holograms.clear(); pending.clear();
        if(!file.exists())return;
        var y=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        var s=y.getConfigurationSection("crates");
        if(s!=null)for(String k:s.getKeys(false)){
            if(k.equals("pending"))continue;
            String type=normalize(y.getString("crates."+k+".type","common"));
            crates.put(k,type);
            List<UUID> ids=new ArrayList<>();
            for(String id:y.getStringList("crates."+k+".holograms"))try{ids.add(UUID.fromString(id));}catch(Exception ignored){}
            holograms.put(k,ids);
        }
        var p=y.getConfigurationSection("pending");
        if(p!=null)for(String id:p.getKeys(false))try{
            UUID u=UUID.fromString(id); String type=normalize(y.getString("pending."+id+".type","common"));
            String reward=y.getString("pending."+id+".reward"); if(reward!=null&&!reward.isBlank())pending.put(u,new Pending(type,reward));
        }catch(Exception ignored){}
        for(String loc:crates.keySet())ensureHologram(loc);
    }
    public void save(){
        var y=new org.bukkit.configuration.file.YamlConfiguration();
        for(var e:crates.entrySet()){
            y.set("crates."+e.getKey()+".type",e.getValue());
            List<String> ids=new ArrayList<>(); for(UUID id:holograms.getOrDefault(e.getKey(),List.of()))ids.add(id.toString());
            y.set("crates."+e.getKey()+".holograms",ids);
        }
        for(var e:pending.entrySet()){
            y.set("pending."+e.getKey()+".type",e.getValue().type());
            y.set("pending."+e.getKey()+".reward",e.getValue().reward());
        }
        try{y.save(file);}catch(IOException ex){plugin.getLogger().warning("Could not save crates.yml: "+ex.getMessage());}
    }
    public void stop(){for(BukkitTask t:animations.values())t.cancel();save();}

    private String loc(Block b){return b.getWorld().getName()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    private Block block(String key){
        String[] p=key.split(":"); if(p.length!=4)return null; World w=Bukkit.getWorld(p[0]); if(w==null)return null;
        try{return w.getBlockAt(Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]));}catch(Exception e){return null;}
    }
    private String type(Block b){return b==null?null:crates.get(loc(b));}
    private Material crateMaterial(String type){return switch(normalize(type)){case "common"->Material.PURPLE_SHULKER_BOX;case "spawner"->Material.BLUE_SHULKER_BOX;case "gold"->Material.YELLOW_SHULKER_BOX;case "crimson"->Material.RED_SHULKER_BOX;default->Material.GREEN_SHULKER_BOX;};}
    private String cap(String type){return type.substring(0,1).toUpperCase(Locale.ROOT)+type.substring(1);}

    public ItemStack createKey(String type,int amount){
        type=normalize(type); amount=Math.max(1,Math.min(64,amount));
        ItemStack item=new ItemStack(Material.TRIPWIRE_HOOK,amount); ItemMeta m=item.getItemMeta();
        m.setDisplayName("§a🔑 "+cap(type)+" Key");
        m.setLore(List.of("§7Opens a §f"+cap(type)+" Crate","§8Digital inventory key","§8Emerald SMP"));
        m.getPersistentDataContainer().set(keyKey,PersistentDataType.STRING,type); item.setItemMeta(m); return item;
    }
    private String keyType(ItemStack i){
        if(i==null||!i.hasItemMeta())return null;
        return i.getItemMeta().getPersistentDataContainer().get(keyKey,PersistentDataType.STRING);
    }
    public void keyAll(String type,int amount,CommandSender sender){
        type=normalize(type); int count=0;
        for(Player p:Bukkit.getOnlinePlayers()){give(p,createKey(type,amount));count++;}
        sender.sendMessage(ChatColor.GREEN+"💚 Everyone online received "+amount+" "+cap(type)+" Key(s)!");
    }
    private void give(Player p,ItemStack i){for(ItemStack left:p.getInventory().addItem(i).values())p.getWorld().dropItemNaturally(p.getLocation(),left);}

    public void place(Player p,String type){
        type=normalize(type); Block clicked=p.getTargetBlockExact(6);
        if(clicked==null||clicked.getType().isAir()){p.sendMessage(ChatColor.RED+"Look at a solid block within 6 blocks.");return;}
        BlockFace face=p.getTargetBlockFace(6); if(face==null)face=BlockFace.UP;
        Block target=clicked.getRelative(face);
        if(!target.getType().isAir()&&!target.isReplaceable()){p.sendMessage(ChatColor.RED+"There is no empty space there.");return;}
        String key=loc(target); if(crates.containsKey(key)){p.sendMessage(ChatColor.RED+"A crate is already registered there.");return;}
        target.setType(crateMaterial(type),false); crates.put(key,type); ensureHologram(key); save();
        p.sendMessage(ChatColor.GREEN+"Placed "+cap(type)+" Crate.");
    }
    public void remove(Player p){
        Block b=p.getTargetBlockExact(6); String type=type(b);
        if(type==null){p.sendMessage(ChatColor.RED+"Look at an Emerald SMP crate within 6 blocks.");return;}
        String key=loc(b); removeHologram(key); crates.remove(key); b.setType(Material.AIR,false); save();
        p.sendMessage(ChatColor.GREEN+"Removed the "+cap(type)+" Crate.");
    }

    private void ensureHologram(String key){
        Block b=block(key); if(b==null||b.getType()!=crateMaterial(crates.get(key)))return;
        List<UUID> ids=holograms.getOrDefault(key,new ArrayList<>());
        boolean valid=true; for(UUID id:ids){Entity e=Bukkit.getEntity(id); if(e==null||!e.isValid()){valid=false;break;}}
        if(valid&&ids.size()==3)return;
        removeHologram(key);
        String type=crates.get(key); Location base=b.getLocation().add(.5,1.85,.5);
        String[] lines={"§a§l"+icon(type)+" "+cap(type).toUpperCase()+" CRATE","§7🔑 "+cap(type)+" Key","§fRight-Click to Open"};
        List<UUID> created=new ArrayList<>();
        for(int i=0;i<3;i++){
            int lineIndex=i;
            ArmorStand as=b.getWorld().spawn(base.clone().add(0,-lineIndex*.28,0),ArmorStand.class,a->{
                a.setInvisible(true);a.setMarker(true);a.setGravity(false);a.setInvulnerable(true);a.setCustomNameVisible(true);a.setCustomName(lines[lineIndex]);
                a.getPersistentDataContainer().set(holoKey,PersistentDataType.STRING,key);
            });
            created.add(as.getUniqueId());
        }
        holograms.put(key,created); save();
    }
    private void removeHologram(String key){
        for(UUID id:holograms.getOrDefault(key,List.of())){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
        holograms.remove(key);
    }
    private String icon(String type){return switch(normalize(type)){case "common"->"🟪";case "spawner"->"🟦";case "gold"->"🟨";case "crimson"->"🟥";default->"🟩";};}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEvent e){
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        Block b=e.getClickedBlock(); String type=type(b); if(type==null)return;
        e.setCancelled(true); open(e.getPlayer(),type);
    }
    private int keySlot(Player p,String type){
        ItemStack[] c=p.getInventory().getStorageContents();
        for(int i=0;i<c.length;i++)if(type.equals(keyType(c[i])))return i; return -1;
    }
    private void open(Player p,String type){
        UUID u=p.getUniqueId(); if(opening.contains(u))return;
        int slot=keySlot(p,type); if(slot<0){p.sendMessage(ChatColor.RED+"❌ You need a "+cap(type)+" Key to open this crate!");return;}
        ItemStack key=p.getInventory().getItem(slot); if(key==null||!type.equals(keyType(key)))return;
        if(key.getAmount()==1)p.getInventory().setItem(slot,null);else key.setAmount(key.getAmount()-1);
        String reward=selectReward(type); pending.put(u,new Pending(type,reward)); opening.add(u); save();
        Inventory inv=Bukkit.createInventory(null,27,"§2§l🎁 "+cap(type)+" CRATE"); p.openInventory(inv); animate(p,type,inv,reward);
    }
    private void animate(Player p,String type,Inventory inv,String finalReward){
        List<ItemStack> reel=new ArrayList<>();
        for(int i=0;i<9;i++)reel.add(displayIcon(randomDisplayReward(type),false));
        animateStep(p,type,inv,finalReward,reel,0);
    }
    private void animateStep(Player p,String type,Inventory inv,String finalReward,List<ItemStack> reel,int step){
        if(!opening.contains(p.getUniqueId()))return;
        if(step>=36){
            for(int i=0;i<9;i++)inv.setItem(9+i,reel.get(i));
            inv.setItem(13,displayIcon(finalReward,true));
            p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,1f,1.1f);
            BukkitTask t=Bukkit.getScheduler().runTaskLater(plugin,()->deliverPending(p),15L);
            animations.put(p.getUniqueId(),t); return;
        }
        for(int i=0;i<8;i++)reel.set(i,reel.get(i+1));
        reel.set(8,displayIcon(randomDisplayReward(type),false));
        for(int i=0;i<9;i++)inv.setItem(9+i,reel.get(i));
        if(step%2==0)p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,.2f,1.5f);
        long delay=step<18?2:step<28?4:6;
        BukkitTask t=Bukkit.getScheduler().runTaskLater(plugin,()->animateStep(p,type,inv,finalReward,reel,step+1),delay);
        animations.put(p.getUniqueId(),t);
    }
    private void deliverPending(Player p){
        animations.remove(p.getUniqueId()); opening.remove(p.getUniqueId());
        Pending reward=pending.remove(p.getUniqueId()); if(reward==null)return;
        giveReward(p,reward.reward()); p.sendMessage(ChatColor.GREEN+"🎉 "+cap(reward.type())+" Crate Reward: §f"+reward.reward()); save();
    }
    @EventHandler public void quit(PlayerQuitEvent e){
        UUID u=e.getPlayer().getUniqueId(); BukkitTask t=animations.remove(u); if(t!=null)t.cancel(); opening.remove(u); save();
    }
    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTask(plugin,()->{deliverPending(e.getPlayer()); for(String k:crates.keySet())ensureHologramIfLoaded(k,e.getPlayer().getWorld());});}
    private void ensureHologramIfLoaded(String key,World world){Block b=block(key);if(b!=null&&b.getWorld().equals(world)&&world.isChunkLoaded(b.getX()>>4,b.getZ()>>4))ensureHologram(key);}
    @EventHandler public void chunkLoad(ChunkLoadEvent e){for(String k:crates.keySet()){Block b=block(k);if(b!=null&&b.getWorld().equals(e.getWorld())&&(b.getX()>>4)==e.getChunk().getX()&&(b.getZ()>>4)==e.getChunk().getZ())ensureHologram(k);}}

    private String randomDisplayReward(String type){
        String r=selectReward(type); if(r.startsWith("$"))return "Money"; if(r.startsWith("shards:"))return "Emerald Shards";
        String[] p=r.split(":",2); return p[0].toUpperCase(Locale.ROOT);
    }
    private String selectReward(String type){
        List<String> entries=plugin.getConfig().getStringList("crates.rewards."+type); if(entries.isEmpty())return "$0";
        double total=0; List<Weighted> parsed=new ArrayList<>();
        for(String raw:entries){String[] p=raw.split("\\|",2);double chance=1;String reward=raw.trim();
            if(p.length==2)try{chance=Double.parseDouble(p[0]);reward=p[1].trim();}catch(Exception ignored){}
            if(chance>0&&!reward.isBlank()){parsed.add(new Weighted(reward,chance));total+=chance;}
        }
        if(parsed.isEmpty())return "$0"; double roll=random.nextDouble()*total,cur=0;
        for(Weighted w:parsed){cur+=w.chance;if(roll<cur)return w.reward;} return parsed.getLast().reward;
    }
    private record Weighted(String reward,double chance){}
    private ItemStack displayIcon(String reward,boolean winner){
        Material m;
        if(reward.startsWith("$"))m=Material.GOLD_INGOT;else if(reward.startsWith("shards:"))m=Material.EMERALD;else m=Material.matchMaterial(reward.split(":",2)[0].trim().toUpperCase(Locale.ROOT));
        if(m==null||m.isAir())m=Material.PAPER; ItemStack i=new ItemStack(m); ItemMeta meta=i.getItemMeta();
        meta.setDisplayName(winner?"§a§l🎉 "+reward:"§f"+reward); if(winner)meta.setLore(List.of("§aFINAL REWARD")); i.setItemMeta(meta); return i;
    }
    private void giveReward(Player p,String reward){
        if(reward.startsWith("$")){try{long a=Long.parseLong(reward.substring(1));if(a>0)plugin.getEconomyManager().depositToUuid(p.getUniqueId(),a,p.getName());}catch(Exception ignored){}return;}
        if(reward.startsWith("shards:")){try{long a=Long.parseLong(reward.substring(7));long c=plugin.getPlayerDataManager().getEmeraldShards(p.getUniqueId());plugin.getPlayerDataManager().setEmeraldShards(p.getUniqueId(),c+Math.max(0,a));}catch(Exception ignored){}return;}
        String[] parts=reward.split(":",2); Material m=Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));if(m==null||m.isAir())return;
        int amount=1; if(parts.length==2)try{amount=Math.max(1,Integer.parseInt(parts[1].trim()));}catch(Exception e){return;}
        while(amount>0){int n=Math.min(m.getMaxStackSize(),amount);give(p,new ItemStack(m,n));amount-=n;}
    }

    public void openInfo(Player p){
        Inventory inv=Bukkit.createInventory(null,27,"§2§l🎁 EMERALD SMP CRATES");
        int[] slots={10,11,12,13,14};
        for(int i=0;i<TYPES.size();i++){String t=TYPES.get(i);ItemStack s=new ItemStack(crateMaterial(t));ItemMeta m=s.getItemMeta();m.setDisplayName("§a§l"+icon(t)+" "+cap(t)+" Crate");m.setLore(List.of("§7🔑 Required: §f"+cap(t)+" Key","§7Click to view rewards"));s.setItemMeta(m);inv.setItem(slots[i],s);}
        p.openInventory(inv);
    }
    private void openRewards(Player p,String type){
        Inventory inv=Bukkit.createInventory(null,54,"§2§l🎁 "+cap(type)+" REWARDS");
        List<String> list=plugin.getConfig().getStringList("crates.rewards."+type);int slot=0;
        for(String raw:list){if(slot>=45)break;String[] parts=raw.split("\\|",2);String reward=parts.length==2?parts[1].trim():raw.trim();ItemStack icon=displayIcon(reward,false);ItemMeta m=icon.getItemMeta();m.setLore(List.of("§7Chance: §f"+(parts.length==2?parts[0]+"%":"Equal"),"§7Reward: §f"+reward));icon.setItemMeta(m);inv.setItem(slot++,icon);}
        inv.setItem(49,new ItemStack(Material.ARROW));p.openInventory(inv);
    }

    public void openEditor(Player p,String type){
        type=normalize(type); editors.put(p.getUniqueId(),type);
        Inventory inv=Bukkit.createInventory(null,54,"§2§l✏ "+cap(type)+" CRATE EDITOR");
        List<String> list=plugin.getConfig().getStringList("crates.rewards."+type);int slot=0;
        for(String raw:list){if(slot>=45)break;String reward=raw.contains("|")?raw.substring(raw.indexOf('|')+1).trim():raw.trim();if(reward.startsWith("$")||reward.startsWith("shards:")){slot++;continue;}String[] p2=reward.split(":",2);Material m=Material.matchMaterial(p2[0].trim().toUpperCase(Locale.ROOT));if(m==null)continue;int a=1;try{if(p2.length==2)a=Math.max(1,Integer.parseInt(p2[1]));}catch(Exception ignored){}inv.setItem(slot++,new ItemStack(m,Math.min(a,m.getMaxStackSize())));}
        inv.setItem(49,new ItemStack(Material.EMERALD));ItemMeta meta=inv.getItem(49).getItemMeta();meta.setDisplayName("§a§lSAVE & CLOSE");meta.setLore(List.of("§7Put the item rewards you want in slots 0–44.","§7Close the GUI to save automatically.","§8Money/shard rewards remain unchanged."));inv.getItem(49).setItemMeta(meta);p.openInventory(inv);
    }
    @EventHandler public void inventoryClick(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String title=ChatColor.stripColor(e.getView().getTitle());
        if(title.equals("🎁 EMERALD SMP CRATES")){e.setCancelled(true);int s=e.getRawSlot();int[] slots={10,11,12,13,14};if(s>=10&&s<=14)openRewards(p,TYPES.get(s-10));return;}
        if(title.contains(" REWARDS")&&title.startsWith("🎁")){if(e.getRawSlot()==49){e.setCancelled(true);openInfo(p);}return;}
        String type=editors.get(p.getUniqueId());
        if(type!=null&&title.equals("✏ "+cap(type)+" CRATE EDITOR")){if(e.getRawSlot()>=45)e.setCancelled(true);return;}
    }
    @EventHandler public void inventoryDrag(InventoryDragEvent e){if(!(e.getWhoClicked() instanceof Player p))return;String type=editors.get(p.getUniqueId());if(type!=null&&e.getView().getTitle().equals("§2§l✏ "+cap(type)+" CRATE EDITOR")&&e.getRawSlots().stream().anyMatch(s->s>=45))e.setCancelled(true);}
    @EventHandler public void inventoryClose(InventoryCloseEvent e){
        if(!(e.getPlayer() instanceof Player p))return;String type=editors.remove(p.getUniqueId());if(type==null)return;
        String title=ChatColor.stripColor(e.getView().getTitle());if(!title.equals("✏ "+cap(type)+" CRATE EDITOR"))return;
        List<String> rewards=new ArrayList<>();
        for(int i=0;i<45;i++){ItemStack s=e.getInventory().getItem(i);if(s==null||s.getType().isAir())continue;rewards.add("1|"+s.getType().name()+":"+s.getAmount());}
        for(String raw:plugin.getConfig().getStringList("crates.rewards."+type))if(raw.contains("|")?raw.substring(raw.indexOf('|')+1).trim().startsWith("$")||raw.substring(raw.indexOf('|')+1).trim().startsWith("shards:"):raw.startsWith("$")||raw.startsWith("shards:"))rewards.add(raw);
        plugin.getConfig().set("crates.rewards."+type,rewards);plugin.saveConfig();p.sendMessage(ChatColor.GREEN+"Saved "+cap(type)+" crate rewards.");
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakCrate(BlockBreakEvent e){
        String t=type(e.getBlock());if(t==null)return;
        if(!e.getPlayer().hasPermission("emerald.admin")){e.setCancelled(true);e.getPlayer().sendMessage(ChatColor.RED+"Only an admin can remove a physical crate.");return;}
        String k=loc(e.getBlock());removeHologram(k);crates.remove(k);save();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blockExplosion(BlockExplodeEvent e){e.blockList().removeIf(this::isCrate);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityExplosion(EntityExplodeEvent e){e.blockList().removeIf(this::isCrate);}
    public boolean isCrate(Block b){return type(b)!=null;}
    private String normalize(String t){t=t==null?"common":t.toLowerCase(Locale.ROOT);return TYPES.contains(t)?t:"common";}
}