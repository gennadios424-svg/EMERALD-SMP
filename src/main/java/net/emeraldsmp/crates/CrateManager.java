package net.emeraldsmp.crates;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.CommandSender;
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
    private final EmeraldSMP plugin; private final File file;
    private final Map<String,String> crates=new LinkedHashMap<>();
    private final Map<String,List<UUID>> holograms=new HashMap<>();
    private final Map<UUID,Pending> pending=new HashMap<>();
    private final Map<UUID,BukkitTask> animations=new HashMap<>();
    private final Set<UUID> opening=new HashSet<>();
    private final Map<UUID,String> editors=new HashMap<>();
    private final Map<UUID,Map<Integer,Double>> editorChances=new HashMap<>();
    private final Map<UUID,Map<String,Integer>> editorOriginals=new HashMap<>();
    private final Map<UUID,Integer> chancePrompts=new HashMap<>();
    private final Map<UUID,EnumMap<KeyType,Integer>> virtualKeys=new HashMap<>();
    private final Map<UUID,PendingKeyAll> pendingKeyAll=new HashMap<>();
    private boolean keyAllRunning=false;
    private final NamespacedKey keyKey,holoKey;
    private final Random random=new Random();
    private record Pending(String type,String reward){}
    private record PendingKeyAll(String type,int amount){}
    private record Reward(double chance,String raw,ItemStack item){}
    private enum KeyType { COMMON,SPAWNER,GOLD,CRIMSON,EMERALD }

    public CrateManager(EmeraldSMP plugin){
        this.plugin=plugin;file=new File(plugin.getDataFolder(),"crates.yml");
        keyKey=new NamespacedKey(plugin,"emerald-crate-key");holoKey=new NamespacedKey(plugin,"emerald-crate-hologram");
    }
    public void load(){
        crates.clear();holograms.clear();pending.clear();virtualKeys.clear();pendingKeyAll.clear();keyAllRunning=false;
        if(!file.exists())return;
        var y=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        var s=y.getConfigurationSection("crates");
        if(s!=null)for(String k:s.getKeys(false)){if(k.equals("pending"))continue;String t=normalize(y.getString("crates."+k+".type","common"));crates.put(k,t);
            List<UUID> ids=new ArrayList<>();for(String id:y.getStringList("crates."+k+".holograms"))try{ids.add(UUID.fromString(id));}catch(Exception ignored){}holograms.put(k,ids);}
        var p=y.getConfigurationSection("pending");if(p!=null)for(String id:p.getKeys(false))try{UUID u=UUID.fromString(id);String t=normalize(y.getString("pending."+id+".type","common"));String r=y.getString("pending."+id+".reward");if(r!=null&&!r.isBlank())pending.put(u,new Pending(t,r));}catch(Exception ignored){}
        var vk=y.getConfigurationSection("virtual-keys");if(vk!=null)for(String id:vk.getKeys(false))try{UUID u=UUID.fromString(id);EnumMap<KeyType,Integer> map=new EnumMap<>(KeyType.class);for(String t:TYPES){int n=vk.getInt(id+"."+t,0);if(n>0)map.put(KeyType.valueOf(t.toUpperCase(Locale.ROOT)),n);}virtualKeys.put(u,map);}catch(Exception ignored){}
        var pk=y.getConfigurationSection("pending-keyall");if(pk!=null)for(String id:pk.getKeys(false))try{UUID u=UUID.fromString(id);String t=normalize(pk.getString(id+".type","common"));int amount=Math.max(1,pk.getInt(id+".amount",1));pendingKeyAll.put(u,new PendingKeyAll(t,amount));}catch(Exception ignored){}
        for(String k:crates.keySet())ensureHologram(k);
    }
    public void save(){
        var y=new org.bukkit.configuration.file.YamlConfiguration();
        for(var e:crates.entrySet()){y.set("crates."+e.getKey()+".type",e.getValue());List<String> ids=new ArrayList<>();for(UUID id:holograms.getOrDefault(e.getKey(),List.of()))ids.add(id.toString());y.set("crates."+e.getKey()+".holograms",ids);}
        for(var e:pending.entrySet()){y.set("pending."+e.getKey()+".type",e.getValue().type());y.set("pending."+e.getKey()+".reward",e.getValue().reward());}
        for(var e:virtualKeys.entrySet())for(var k:e.getValue().entrySet())y.set("virtual-keys."+e.getKey()+"."+k.getKey().name().toLowerCase(Locale.ROOT),k.getValue());
        for(var e:pendingKeyAll.entrySet()){y.set("pending-keyall."+e.getKey()+".type",e.getValue().type());y.set("pending-keyall."+e.getKey()+".amount",e.getValue().amount());}
        try{y.save(file);}catch(IOException ex){plugin.getLogger().warning("Could not save crates.yml: "+ex.getMessage());}
    }
    public void stop(){for(BukkitTask t:animations.values())t.cancel();save();}
    private String loc(Block b){return b.getWorld().getName()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    private Block block(String key){String[] p=key.split(":");if(p.length!=4)return null;World w=Bukkit.getWorld(p[0]);if(w==null)return null;try{return w.getBlockAt(Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]));}catch(Exception e){return null;}}
    private String type(Block b){return b==null?null:crates.get(loc(b));}
    private String normalize(String t){t=t==null?"common":t.toLowerCase(Locale.ROOT);return TYPES.contains(t)?t:"common";}
    private String cap(String t){return t.substring(0,1).toUpperCase(Locale.ROOT)+t.substring(1);}
    private String icon(String t){return switch(normalize(t)){case"common"->"🟪";case"spawner"->"🟦";case"gold"->"🟨";case"crimson"->"🟥";default->"🟩";};}
    private Material crateMaterial(String t){return switch(normalize(t)){case"common"->Material.PURPLE_SHULKER_BOX;case"spawner"->Material.BLUE_SHULKER_BOX;case"gold"->Material.YELLOW_SHULKER_BOX;case"crimson"->Material.RED_SHULKER_BOX;default->Material.GREEN_SHULKER_BOX;};}

    public ItemStack createKey(String type,int amount){
        type=normalize(type);amount=Math.max(1,Math.min(64,amount));ItemStack i=new ItemStack(Material.TRIPWIRE_HOOK,amount);ItemMeta m=i.getItemMeta();
        m.setDisplayName("§a🔑 "+cap(type)+" Key");m.setLore(List.of("§7Opens a §f"+cap(type)+" Crate","§8Physical key • admin/manual use","§8Emerald SMP"));m.getPersistentDataContainer().set(keyKey,PersistentDataType.STRING,type);i.setItemMeta(m);return i;
    }
    private String keyType(ItemStack i){if(i==null||!i.hasItemMeta())return null;return i.getItemMeta().getPersistentDataContainer().get(keyKey,PersistentDataType.STRING);}
    public int virtualKeyCount(Player p,String type){EnumMap<KeyType,Integer> m=virtualKeys.get(p.getUniqueId());if(m==null)return 0;return m.getOrDefault(keyEnum(type),0);}
    public synchronized void giveVirtualKeys(Player p,String type,int amount){
        type=normalize(type);amount=Math.max(1,Math.min(64,amount));
        EnumMap<KeyType,Integer> m=virtualKeys.computeIfAbsent(p.getUniqueId(),x->new EnumMap<>(KeyType.class));
        KeyType k=keyEnum(type);long total=(long)m.getOrDefault(k,0)+amount;m.put(k,(int)Math.min(Integer.MAX_VALUE,total));save();
    }
    private KeyType keyEnum(String t){return KeyType.valueOf(normalize(t).toUpperCase(Locale.ROOT));}
    private boolean consumeVirtualKey(Player p,String type){UUID u=p.getUniqueId();EnumMap<KeyType,Integer> m=virtualKeys.get(u);if(m==null)return false;KeyType k=keyEnum(type);int n=m.getOrDefault(k,0);if(n<=0)return false;if(n==1)m.remove(k);else m.put(k,n-1);if(m.isEmpty())virtualKeys.remove(u);save();return true;}
    private int keySlot(Player p,String type){ItemStack[] c=p.getInventory().getStorageContents();for(int i=0;i<c.length;i++)if(type.equals(keyType(c[i])))return i;return -1;}
    public boolean isKeyAllRunning(){return keyAllRunning;}

    public synchronized void keyAll(String type,int amount,CommandSender sender){
        type=normalize(type);amount=Math.max(1,Math.min(64,amount));
        if(keyAllRunning){sender.sendMessage(ChatColor.YELLOW+"⏳ A Keyall animation is already running. Please wait for it to finish.");return;}
        keyAllRunning=true;
        for(Player p:Bukkit.getOnlinePlayers()){
            UUID u=p.getUniqueId();PendingKeyAll existing=pendingKeyAll.get(u);
            if(existing==null)pendingKeyAll.put(u,new PendingKeyAll(type,amount));else pendingKeyAll.put(u,new PendingKeyAll(existing.type(),Math.min(64,existing.amount()+amount)));
        }
        save();sender.sendMessage(ChatColor.GREEN+"💚 Keyall started: "+amount+" virtual "+cap(type)+" Key(s) for all online players.");animateKeyAll(type,amount);
    }
    private void animateKeyAll(String type,int amount){
        final String title="§a§l💚 EMERALD SMP",keyall="§f§l🔑 KEYALL",finalTitle="§a§l🎉 KEYALL 🎉";
        final List<String> rolling=List.of("§7     🔑   💎   💚","§7       💚   🔑","§7     💎   💚   🔑","§7       🔑   💎","§7     💚   🔑   💎","§7       💎   💚","§7          🔑");
        Bukkit.getScheduler().runTaskLater(plugin,()->{for(Player p:Bukkit.getOnlinePlayers()){p.sendTitle(title,keyall+"\n§7Preparing...",0,40,0);p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_CHIME,.45f,1.15f);}},0L);
        for(int i=0;i<rolling.size();i++){final int frame=i;Bukkit.getScheduler().runTaskLater(plugin,()->{if(!keyAllRunning)return;for(Player p:Bukkit.getOnlinePlayers()){p.sendTitle(title,keyall+"\n§e§lROLLING...\n"+rolling.get(frame),0,10,0);if(frame==0||frame%2==0)p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,.28f,1.15f);}},40L+i*5L);}
        final int[] slowFrames={0,1,2,3,4,5,6};final long[] slowDelays={80L,87L,95L,104L,114L,125L,135L};
        for(int i=0;i<slowFrames.length;i++){final int frame=i;Bukkit.getScheduler().runTaskLater(plugin,()->{if(!keyAllRunning)return;for(Player p:Bukkit.getOnlinePlayers()){String arrow=frame<6?"§7        ↓":"§a§l        🔑";p.sendTitle(title,keyall+"\n§6§lSLOWING...\n"+rolling.get(slowFrames[frame])+"\n"+arrow,0,12,0);p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,.32f,1.35f-(frame*.1f));}},slowDelays[i]);}
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(!keyAllRunning)return;awardPendingKeyAll(type,amount);for(Player p:Bukkit.getOnlinePlayers()){p.sendTitle(finalTitle,"§f§l"+cap(type)+" KEY §8×§a"+amount+"\n§7Everyone receives "+amount+" key"+(amount==1?"":"s")+"!",0,30,10);p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,1f,1.05f);}keyAllRunning=false;save();},170L);
    }
    private synchronized void awardPendingKeyAll(String type,int amount){Iterator<Map.Entry<UUID,PendingKeyAll>> it=pendingKeyAll.entrySet().iterator();while(it.hasNext()){Map.Entry<UUID,PendingKeyAll> e=it.next();PendingKeyAll q=e.getValue();if(q==null){it.remove();continue;}EnumMap<KeyType,Integer> m=virtualKeys.computeIfAbsent(e.getKey(),x->new EnumMap<>(KeyType.class));KeyType k=keyEnum(q.type());long n=(long)m.getOrDefault(k,0)+Math.max(1,q.amount());m.put(k,(int)Math.min(Integer.MAX_VALUE,n));it.remove();}}
    public void place(Player p,String type){type=normalize(type);Block clicked=p.getTargetBlockExact(6);if(clicked==null||clicked.getType().isAir()){p.sendMessage("§cLook at a solid block within 6 blocks.");return;}BlockFace face=p.getTargetBlockFace(6);if(face==null)face=BlockFace.UP;Block target=clicked.getRelative(face);if(!target.getType().isAir()&&!target.isReplaceable()){p.sendMessage("§cThere is no empty space there.");return;}String k=loc(target);if(crates.containsKey(k)){p.sendMessage("§cA crate is already registered there.");return;}target.setType(crateMaterial(type),false);crates.put(k,type);ensureHologram(k);save();p.sendMessage("§aPlaced "+cap(type)+" Crate.");}
    public void remove(Player p){Block b=p.getTargetBlockExact(6);String t=type(b);if(t==null){p.sendMessage("§cLook at an Emerald SMP crate.");return;}String k=loc(b);removeHologram(k);crates.remove(k);b.setType(Material.AIR,false);save();p.sendMessage("§aRemoved "+cap(t)+" Crate.");}
    private void ensureHologram(String k){Block b=block(k);if(b==null||!b.getType().equals(crateMaterial(crates.get(k))))return;List<UUID> ids=holograms.getOrDefault(k,new ArrayList<>());boolean ok=ids.size()==3;for(UUID id:ids){Entity e=Bukkit.getEntity(id);if(e==null||!e.isValid())ok=false;}if(ok)return;removeHologram(k);String t=crates.get(k);Location base=b.getLocation().add(.5,1.85,.5);String[] lines={"§a§l"+icon(t)+" "+cap(t).toUpperCase()+" CRATE","§7🔑 "+cap(t)+" Key","§fRight-Click Preview • Left-Click Open"};List<UUID> made=new ArrayList<>();for(int i=0;i<3;i++){int n=i;ArmorStand as=b.getWorld().spawn(base.clone().add(0,-n*.28,0),ArmorStand.class,a->{a.setInvisible(true);a.setMarker(true);a.setGravity(false);a.setInvulnerable(true);a.setCustomNameVisible(true);a.setCustomName(lines[n]);a.getPersistentDataContainer().set(holoKey,PersistentDataType.STRING,k);});made.add(as.getUniqueId());}holograms.put(k,made);save();}
    private void removeHologram(String k){for(UUID id:holograms.getOrDefault(k,List.of())){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}holograms.remove(k);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e){if(e.getClickedBlock()==null)return;String t=type(e.getClickedBlock());if(t==null)return;e.setCancelled(true);if(e.getAction()==Action.RIGHT_CLICK_BLOCK)openPreview(e.getPlayer(),t);else if(e.getAction()==Action.LEFT_CLICK_BLOCK)open(e.getPlayer(),t);}
    private void open(Player p,String type){
        UUID u=p.getUniqueId();
        if (opening.contains(u)) { p.sendMessage("§e⏳ Your crate is already opening."); return; }
        if (rewardEntries(type).isEmpty()) { p.sendMessage("§c🎁 This crate has no rewards configured yet."); return; }
        if (!(consumeVirtualKey(p,type)||consumePhysicalKey(p,type))) { p.sendMessage("§c❌ You need a "+cap(type)+" Key to open this crate."); return; }
        if ("spawner".equals(normalize(type))) { openSpawnerChoice(p); return; }
        Reward reward=selectReward(type); pending.put(u,new Pending(type,reward.raw())); opening.add(u); save();
        Inventory inv=Bukkit.createInventory(null,27,"§2§l🎁 "+cap(type)+" CRATE"); p.openInventory(inv); animate(p,type,inv,reward);
    }

    private void openSpawnerChoice(Player p) {
        Inventory inv=Bukkit.createInventory(new SpawnerChoiceHolder(),54,"§2§l💚 CHOOSE SPAWNER");
        int slot=0;
        for (Reward r: rewardEntries("spawner")) {
            if (slot>=45) break;
            ItemStack icon=displayIcon(r,false); ItemMeta m=icon.getItemMeta();
            if(m!=null){List<String> lore=m.hasLore()?new ArrayList<>(m.getLore()):new ArrayList<>();lore.add("§aClick to choose this spawner");m.setLore(lore);icon.setItemMeta(m);}
            inv.setItem(slot++,icon);
        }
        p.openInventory(inv);
    }
    private boolean consumePhysicalKey(Player p,String type){int slot=keySlot(p,type);if(slot<0)return false;ItemStack k=p.getInventory().getItem(slot);if(k==null||!type.equals(keyType(k)))return false;if(k.getAmount()<=1)p.getInventory().setItem(slot,null);else k.setAmount(k.getAmount()-1);return true;}
    private void animate(Player p,String type,Inventory inv,Reward finalReward){List<ItemStack> reel=new ArrayList<>();for(int i=0;i<9;i++)reel.add(displayIcon(selectReward(type),false));animateStep(p,type,inv,finalReward,reel,0);}
    private void animateStep(Player p,String type,Inventory inv,Reward finalReward,List<ItemStack> reel,int step){UUID u=p.getUniqueId();if(!opening.contains(u)||pending.get(u)==null)return;if(step>=36){for(int i=0;i<9;i++)inv.setItem(9+i,reel.get(i));inv.setItem(13,displayIcon(finalReward,true));p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,1f,1.1f);BukkitTask t=Bukkit.getScheduler().runTaskLater(plugin,()->deliverPending(p),15L);animations.put(u,t);return;}for(int i=0;i<8;i++)reel.set(i,reel.get(i+1));reel.set(8,displayIcon(selectReward(type),false));for(int i=0;i<9;i++)inv.setItem(9+i,reel.get(i));if(step%2==0)p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,.2f,1.5f);long delay=step<18?2:step<28?4:6;BukkitTask t=Bukkit.getScheduler().runTaskLater(plugin,()->animateStep(p,type,inv,finalReward,reel,step+1),delay);animations.put(u,t);}
    private void deliverPending(Player p){UUID u=p.getUniqueId();if(!opening.contains(u)&&!pending.containsKey(u))return;BukkitTask old=animations.remove(u);if(old!=null)old.cancel();Pending q=pending.remove(u);opening.remove(u);if(q==null)return;giveReward(p,q.reward());save();p.sendMessage("§a🎉 "+cap(q.type())+" Crate Reward: §f"+prettyReward(q.reward()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void crateAnimationClick(InventoryClickEvent e){
        if (e.getWhoClicked() instanceof Player p && e.getInventory().getHolder() instanceof SpawnerChoiceHolder) {
            e.setCancelled(true);
            int slot=e.getRawSlot();
            if(slot>=0 && slot<45){List<Reward> rewards=rewardEntries("spawner");if(slot<rewards.size()){giveReward(p,rewards.get(slot).raw());p.closeInventory();p.sendMessage("§a💚 You selected §f"+prettyReward(rewards.get(slot).raw())+"§a.");}}
            return;
        }if(!(e.getWhoClicked() instanceof Player p))return;UUID u=p.getUniqueId();if(opening.contains(u)&&e.getView().getTitle().contains("CRATE")){e.setCancelled(true);return;}String title=ChatColor.stripColor(e.getView().getTitle());if(title.equals("🎁 EMERALD SMP CRATES")){e.setCancelled(true);int s=e.getRawSlot();if(s>=10&&s<=14)openPreview(p,TYPES.get(s-10));return;}if(title.contains(" REWARDS")&&title.startsWith("🎁")){e.setCancelled(true);if(e.getRawSlot()==49)openInfo(p);return;}String type=editors.get(u);if(type==null||!title.equals("✏ "+cap(type)+" CRATE EDITOR"))return;int raw=e.getRawSlot();if(raw>=45){e.setCancelled(true);if(raw==49)saveEditor(p);else if(raw==50)p.closeInventory();else if(raw==48){chancePrompts.put(u,-1);p.sendMessage("§eEnter reward slot 1-45, then chance 0-100 (example: 3 5). Type cancel.");}return;}}
    @EventHandler public void crateAnimationDrag(InventoryDragEvent e){if(!(e.getWhoClicked() instanceof Player p))return;UUID u=p.getUniqueId();String title=e.getView().getTitle();if(opening.contains(u)&&title.contains("CRATE")){e.setCancelled(true);return;}if(editors.containsKey(u)&&title.contains("CRATE EDITOR"))e.setCancelled(true);}
    @EventHandler public void chatChance(AsyncPlayerChatEvent e){Player p=e.getPlayer();Integer marker=chancePrompts.get(p.getUniqueId());if(marker==null)return;e.setCancelled(true);String msg=e.getMessage().trim();Bukkit.getScheduler().runTask(plugin,()->{UUID u=p.getUniqueId();if(!editors.containsKey(u)){chancePrompts.remove(u);return;}if(msg.equalsIgnoreCase("cancel")){chancePrompts.remove(u);p.sendMessage("§7Chance edit cancelled.");return;}try{String[] a=msg.split("\\s+");int slot;if(marker==-1){if(a.length<2)throw new NumberFormatException();slot=Integer.parseInt(a[0])-1;double val=Double.parseDouble(a[1]);if(slot<0||slot>=45||val<0||val>100)throw new NumberFormatException();editorChances.computeIfAbsent(u,x->new HashMap<>()).put(slot,val);chancePrompts.remove(u);p.sendMessage("§aChance for slot "+(slot+1)+" set to "+formatChance(val)+"%.");return;}double val=Double.parseDouble(msg);if(val<0||val>100)throw new NumberFormatException();editorChances.computeIfAbsent(u,x->new HashMap<>()).put(marker,val);chancePrompts.remove(u);p.sendMessage("§aChance set to "+formatChance(val)+"%.");}catch(Exception ex){p.sendMessage("§cUse: <slot 1-45> <chance 0-100>");}});}
    public void openInfo(Player p){Inventory inv=Bukkit.createInventory(null,27,"§2§l🎁 EMERALD SMP CRATES");int[] slots={10,11,12,13,14};for(int i=0;i<TYPES.size();i++){String t=TYPES.get(i);ItemStack s=new ItemStack(crateMaterial(t));ItemMeta m=s.getItemMeta();m.setDisplayName("§a§l"+icon(t)+" "+cap(t)+" Crate");m.setLore(List.of("§7🔑 Virtual keys: §f"+virtualKeyCount(p,t),"§7Right-click a crate to preview","§7Preview is read-only"));s.setItemMeta(m);inv.setItem(slots[i],s);}p.openInventory(inv);}
    private void openPreview(Player p,String type){Inventory inv=Bukkit.createInventory(null,54,"§2§l🎁 "+cap(type)+" CRATE PREVIEW");int slot=0;for(Reward r:rewardEntries(type)){if(slot>=45)break;ItemStack i=displayIcon(r,false);ItemMeta m=i.getItemMeta();List<String> l=m!=null&&m.hasLore()?new ArrayList<>(m.getLore()):new ArrayList<>();l.add("§7Reward: §f"+prettyReward(r.raw()));if(m!=null){m.setLore(l);i.setItemMeta(m);}inv.setItem(slot++,i);}inv.setItem(49,button(Material.ARROW,"§a⬅ Back",List.of("§7Return to crate info")));p.openInventory(inv);}
    private List<Reward> rewardEntries(String type){List<Reward> out=new ArrayList<>();for(String raw:plugin.getConfig().getStringList("crates.rewards."+normalize(type))){String line=raw.trim();if(line.isBlank())continue;double chance=1;String reward=line;int pipe=line.indexOf('|');if(pipe>0)try{chance=Double.parseDouble(line.substring(0,pipe).trim());reward=line.substring(pipe+1).trim();}catch(Exception ignored){}if(chance<=0||chance>100)continue;out.add(new Reward(chance,reward,decodeItem(reward)));}return out;}
    private Reward selectReward(String type){List<Reward> r=rewardEntries(type);if(r.isEmpty())return new Reward(1,"$0",null);double total=r.stream().mapToDouble(Reward::chance).sum(),roll=random.nextDouble()*total,cur=0;for(Reward x:r){cur+=x.chance();if(roll<cur)return x;}return r.get(r.size()-1);}
    private ItemStack displayIcon(Reward r,boolean winner){ItemStack i;if(r.item()!=null)i=r.item().clone();else if(r.raw().startsWith("$"))i=new ItemStack(Material.GOLD_INGOT);else if(r.raw().startsWith("shards:"))i=new ItemStack(Material.EMERALD);else{Material m=Material.matchMaterial(r.raw().split(":",2)[0].trim().toUpperCase(Locale.ROOT));i=new ItemStack(m==null?Material.PAPER:m);}ItemMeta m=i.getItemMeta();if(m!=null){List<String> l=m.hasLore()?new ArrayList<>(m.getLore()):new ArrayList<>();l.add("§7Chance: §f"+formatChance(r.chance())+"%");if(winner)l.add("§a§lFINAL REWARD");m.setLore(l);i.setItemMeta(m);}return i;}
    private void giveReward(Player p,String reward){ItemStack custom=decodeItem(reward);if(custom!=null){custom=plugin.getDrillManager().activate(custom);custom=plugin.getEmeraldToolsManager().activateCrateReward(custom);give(p,custom);return;}if(reward.startsWith("$")){try{long a=Long.parseLong(reward.substring(1).trim());if(a>0)plugin.getEconomyManager().depositToUuid(p.getUniqueId(),a,p.getName());}catch(Exception ignored){}return;}if(reward.startsWith("shards:")){try{long a=Long.parseLong(reward.substring(7).trim());long c=plugin.getPlayerDataManager().getEmeraldShards(p.getUniqueId());plugin.getPlayerDataManager().setEmeraldShards(p.getUniqueId(),c+Math.max(0,a));}catch(Exception ignored){}return;}String[] parts=reward.split(":",2);Material m=Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));if(m==null||m.isAir())return;int amount=1;if(parts.length==2)try{amount=Math.max(1,Integer.parseInt(parts[1].trim()));}catch(Exception e){return;}while(amount>0){int n=Math.min(m.getMaxStackSize(),amount);give(p,new ItemStack(m,n));amount-=n;}}
    private void give(Player p,ItemStack i){if(i==null||i.getType().isAir())return;for(ItemStack left:p.getInventory().addItem(i).values())p.getWorld().dropItemNaturally(p.getLocation(),left);}
    private String encodeItem(ItemStack i){return "item64:"+Base64.getEncoder().encodeToString(i.serializeAsBytes());}
    private ItemStack decodeItem(String r){if(r==null||!r.startsWith("item64:"))return null;try{return ItemStack.deserializeBytes(Base64.getDecoder().decode(r.substring(7)));}catch(Exception ex){return null;}}
    private ItemStack button(Material m,String n,List<String> l){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();if(meta!=null){meta.setDisplayName(n);meta.setLore(l);i.setItemMeta(meta);}return i;}
    private String formatChance(double d){return Math.rint(d)==d?Long.toString((long)d):String.format(Locale.US,"%.2f",d);}
    private String prettyReward(String r){if(r.startsWith("item64:")){ItemStack i=decodeItem(r);return pretty(i);}return r;}

    private static final class SpawnerChoiceHolder implements InventoryHolder { public Inventory getInventory(){ return null; } }
    private String pretty(ItemStack i){if(i==null)return"Item";if(i.hasItemMeta()&&i.getItemMeta().hasDisplayName())return ChatColor.stripColor(i.getItemMeta().getDisplayName());return i.getType().name().toLowerCase(Locale.ROOT).replace('_',' ');}
    public void openEditor(Player p,String type){type=normalize(type);UUID u=p.getUniqueId();editors.put(u,type);editorChances.put(u,new HashMap<>());chancePrompts.remove(u);Map<String,Integer> originals=new HashMap<>();Inventory inv=Bukkit.createInventory(null,54,"§2§l✏ "+cap(type)+" CRATE EDITOR");int slot=0;for(Reward r:rewardEntries(type))if(r.item()!=null&&slot<45){ItemStack i=r.item().clone();inv.setItem(slot,i);editorChances.get(u).put(slot,r.chance());originals.merge(identityToken(i),i.getAmount(),Integer::sum);slot++;}editorOriginals.put(u,originals);inv.setItem(48,button(Material.PAPER,"§e✏ SET CHANCE",List.of("§7Type: <slot> <chance>","§7Example: 3 5")));inv.setItem(49,button(Material.EMERALD,"§a§l💾 SAVE",List.of("§7Persist this reward pool")));inv.setItem(50,button(Material.ARROW,"§eCancel / Close",List.of("§7Discard unsaved changes")));p.openInventory(inv);}
    private String identityToken(ItemStack i){ItemStack one=i.clone();one.setAmount(1);return Base64.getEncoder().encodeToString(one.serializeAsBytes());}
    private void saveEditor(Player p){UUID u=p.getUniqueId();String type=editors.get(u);if(type==null)return;Inventory inv=p.getOpenInventory().getTopInventory();List<String> rewards=new ArrayList<>();Map<Integer,Double> chances=editorChances.getOrDefault(u,Map.of());for(int i=0;i<45;i++){ItemStack s=inv.getItem(i);if(s==null||s.getType().isAir())continue;double c=Math.max(0,Math.min(100,chances.getOrDefault(i,1D)));if(c<=0)continue;ItemStack clean=s.clone();rewards.add(formatChance(c)+"|"+encodeItem(clean));}for(String raw:plugin.getConfig().getStringList("crates.rewards."+type)){String r=raw.contains("|")?raw.substring(raw.indexOf('|')+1).trim():raw.trim();if(r.startsWith("$")||r.startsWith("shards:"))rewards.add(raw);}plugin.getConfig().set("crates.rewards."+type,rewards);plugin.saveConfig();editors.remove(u);editorChances.remove(u);editorOriginals.remove(u);chancePrompts.remove(u);p.closeInventory();p.sendMessage("§a💾 Saved "+cap(type)+" crate rewards.");}
    private void discardEditor(Player p){UUID u=p.getUniqueId();String type=editors.remove(u);chancePrompts.remove(u);editorChances.remove(u);Map<String,Integer> originals=editorOriginals.remove(u);if(type==null)return;Inventory inv=p.getOpenInventory().getTopInventory();if(inv==null)return;Map<String,Integer> current=new HashMap<>();List<ItemStack> changed=new ArrayList<>();for(int i=0;i<45;i++){ItemStack s=inv.getItem(i);if(s==null||s.getType().isAir())continue;String id=identityToken(s);current.merge(id,s.getAmount(),Integer::sum);}for(int i=0;i<45;i++){ItemStack s=inv.getItem(i);if(s==null||s.getType().isAir())continue;String id=identityToken(s);int orig=originals.getOrDefault(id,0),cur=current.getOrDefault(id,0);if(cur>orig){int excess=Math.min(s.getAmount(),cur-orig);ItemStack ret=s.clone();ret.setAmount(excess);changed.add(ret);}}for(ItemStack s:changed)give(p,s);for(int i=0;i<45;i++)inv.setItem(i,null);}
    @EventHandler public void inventoryClose(InventoryCloseEvent e){if(!(e.getPlayer() instanceof Player p))return;UUID u=p.getUniqueId();String type=editors.get(u);if(type!=null&&ChatColor.stripColor(e.getView().getTitle()).equals("✏ "+cap(type)+" CRATE EDITOR")){discardEditor(p);p.sendMessage("§eChanges discarded; unsaved items returned.");}}
    @EventHandler public void quit(PlayerQuitEvent e){UUID u=e.getPlayer().getUniqueId();BukkitTask t=animations.remove(u);if(t!=null)t.cancel();opening.remove(u);chancePrompts.remove(u);if(editors.containsKey(u))discardEditor(e.getPlayer());save();}
    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTask(plugin,()->{if(pending.containsKey(e.getPlayer().getUniqueId()))deliverPending(e.getPlayer());for(String k:crates.keySet())ensureHologramIfLoaded(k,e.getPlayer().getWorld());});}
    private void ensureHologramIfLoaded(String k,World w){Block b=block(k);if(b!=null&&b.getWorld().equals(w)&&w.isChunkLoaded(b.getX()>>4,b.getZ()>>4))ensureHologram(k);}
    @EventHandler public void chunkLoad(ChunkLoadEvent e){for(String k:crates.keySet()){Block b=block(k);if(b!=null&&b.getWorld().equals(e.getWorld())&&(b.getX()>>4)==e.getChunk().getX()&&(b.getZ()>>4)==e.getChunk().getZ())ensureHologram(k);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakCrate(BlockBreakEvent e){String t=type(e.getBlock());if(t==null)return;if(!e.getPlayer().hasPermission("emerald.admin")){e.setCancelled(true);return;}String k=loc(e.getBlock());removeHologram(k);crates.remove(k);save();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blockExplosion(BlockExplodeEvent e){e.blockList().removeIf(this::isCrate);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityExplosion(EntityExplodeEvent e){e.blockList().removeIf(this::isCrate);}
    public boolean isCrate(Block b){return type(b)!=null;}
}