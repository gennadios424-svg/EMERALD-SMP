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
import java.util.Base64;

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
    private final Map<UUID,Map<Integer,Double>> editorChances=new HashMap<>();
    private final Map<UUID,Map<String,Integer>> editorOriginals=new HashMap<>();
    private final Set<UUID> editorSaved=new HashSet<>();
    private final Map<UUID,Integer> chancePrompts=new HashMap<>();
    private final NamespacedKey keyKey,holoKey;
    private final Random random=new Random();

    private record Pending(String type,String reward){}
    private record Reward(double chance,String raw,ItemStack item){}

    public CrateManager(EmeraldSMP plugin){
        this.plugin=plugin; file=new File(plugin.getDataFolder(),"crates.yml");
        keyKey=new NamespacedKey(plugin,"emerald-crate-key");
        holoKey=new NamespacedKey(plugin,"emerald-crate-hologram");
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
            String reward=y.getString("pending."+id+".reward");
            if(reward!=null&&!reward.isBlank())pending.put(u,new Pending(type,reward));
        }catch(Exception ignored){}
        for(String loc:crates.keySet())ensureHologram(loc);
    }

    public void save(){
        var y=new org.bukkit.configuration.file.YamlConfiguration();
        for(var e:crates.entrySet()){
            y.set("crates."+e.getKey()+".type",e.getValue());
            List<String> ids=new ArrayList<>();
            for(UUID id:holograms.getOrDefault(e.getKey(),List.of()))ids.add(id.toString());
            y.set("crates."+e.getKey()+".holograms",ids);
        }
        for(var e:pending.entrySet()){
            y.set("pending."+e.getKey()+".type",e.getValue().type());
            y.set("pending."+e.getKey()+".reward",e.getValue().reward());
        }
        try{y.save(file);}catch(IOException ex){plugin.getLogger().warning("Could not save crates.yml: "+ex.getMessage());}
    }
    public void stop(){
        for(BukkitTask t:animations.values())t.cancel();
        save();
    }

    private String loc(Block b){return b.getWorld().getName()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    private Block block(String key){
        String[] p=key.split(":"); if(p.length!=4)return null;
        World w=Bukkit.getWorld(p[0]); if(w==null)return null;
        try{return w.getBlockAt(Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]));}
        catch(Exception e){return null;}
    }
    private String type(Block b){return b==null?null:crates.get(loc(b));}
    private String cap(String type){return type.substring(0,1).toUpperCase(Locale.ROOT)+type.substring(1);}
    private String icon(String type){return switch(normalize(type)){case "common"->"🟪";case "spawner"->"🟦";case "gold"->"🟨";case "crimson"->"🟥";default->"🟩";};}
    private Material crateMaterial(String type){
        return switch(normalize(type)){
            case "common"->Material.PURPLE_SHULKER_BOX;
            case "spawner"->Material.BLUE_SHULKER_BOX;
            case "gold"->Material.YELLOW_SHULKER_BOX;
            case "crimson"->Material.RED_SHULKER_BOX;
            default->Material.GREEN_SHULKER_BOX;
        };
    }

    public ItemStack createKey(String type,int amount){
        type=normalize(type); amount=Math.max(1,Math.min(64,amount));
        ItemStack item=new ItemStack(Material.TRIPWIRE_HOOK,amount);
        ItemMeta m=item.getItemMeta();
        m.setDisplayName("§a🔑 "+cap(type)+" Key");
        m.setLore(List.of("§7Opens a §f"+cap(type)+" Crate","§8Digital inventory key","§8Emerald SMP"));
        m.getPersistentDataContainer().set(keyKey,PersistentDataType.STRING,type);
        item.setItemMeta(m);
        return item;
    }
    private String keyType(ItemStack i){
        if(i==null||!i.hasItemMeta())return null;
        return i.getItemMeta().getPersistentDataContainer().get(keyKey,PersistentDataType.STRING);
    }
    public void keyAll(String type,int amount,CommandSender sender){
        type=normalize(type);
        for(Player p:Bukkit.getOnlinePlayers())give(p,createKey(type,amount));
        sender.sendMessage(ChatColor.GREEN+"💚 Everyone online received "+amount+" "+cap(type)+" Key(s)!");
    }
    private void give(Player p,ItemStack i){
        if(i==null||i.getType().isAir())return;
        for(ItemStack left:p.getInventory().addItem(i).values())p.getWorld().dropItemNaturally(p.getLocation(),left);
    }

    public void place(Player p,String type){
        type=normalize(type);
        Block clicked=p.getTargetBlockExact(6);
        if(clicked==null||clicked.getType().isAir()){p.sendMessage(ChatColor.RED+"Look at a solid block within 6 blocks.");return;}
        BlockFace face=p.getTargetBlockFace(6); if(face==null)face=BlockFace.UP;
        Block target=clicked.getRelative(face);
        if(!target.getType().isAir()&&!target.isReplaceable()){p.sendMessage(ChatColor.RED+"There is no empty space there.");return;}
        String key=loc(target);
        if(crates.containsKey(key)){p.sendMessage(ChatColor.RED+"A crate is already registered there.");return;}
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
        boolean valid=true;
        for(UUID id:ids){Entity e=Bukkit.getEntity(id);if(e==null||!e.isValid()){valid=false;break;}}
        if(valid&&ids.size()==3)return;
        removeHologram(key);
        String type=crates.get(key); Location base=b.getLocation().add(.5,1.85,.5);
        String[] lines={"§a§l"+icon(type)+" "+cap(type).toUpperCase()+" CRATE","§7🔑 "+cap(type)+" Key","§fRight-Click to Open"};
        List<UUID> created=new ArrayList<>();
        for(int i=0;i<3;i++){
            int lineIndex=i;
            ArmorStand as=b.getWorld().spawn(base.clone().add(0,-lineIndex*.28,0),ArmorStand.class,a->{
                a.setInvisible(true);a.setMarker(true);a.setGravity(false);a.setInvulnerable(true);
                a.setCustomNameVisible(true);a.setCustomName(lines[lineIndex]);
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

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEvent e){
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        Block b=e.getClickedBlock(); String type=type(b); if(type==null)return;
        e.setCancelled(true); open(e.getPlayer(),type);
    }

    private int keySlot(Player p,String type){
        ItemStack[] c=p.getInventory().getStorageContents();
        for(int i=0;i<c.length;i++)if(type.equals(keyType(c[i])))return i;
        return -1;
    }
    private void open(Player p,String type){
        UUID u=p.getUniqueId(); if(opening.contains(u))return;
        int slot=keySlot(p,type);
        if(slot<0){p.sendMessage(ChatColor.RED+"❌ You need a "+cap(type)+" Key to open this crate!");return;}
        ItemStack key=p.getInventory().getItem(slot); if(key==null||!type.equals(keyType(key)))return;
        if(key.getAmount()==1)p.getInventory().setItem(slot,null);else key.setAmount(key.getAmount()-1);
        Reward reward=selectReward(type);
        pending.put(u,new Pending(type,reward.raw()));
        opening.add(u); save();
        Inventory inv=Bukkit.createInventory(null,27,"§2§l🎁 "+cap(type)+" CRATE");
        p.openInventory(inv); animate(p,type,inv,reward);
    }

    private void animate(Player p,String type,Inventory inv,Reward finalReward){
        List<ItemStack> reel=new ArrayList<>();
        for(int i=0;i<9;i++)reel.add(displayIcon(selectReward(type),false));
        animateStep(p,type,inv,finalReward,reel,0);
    }
    private void animateStep(Player p,String type,Inventory inv,Reward finalReward,List<ItemStack> reel,int step){
        if(!opening.contains(p.getUniqueId()))return;
        if(step>=36){
            for(int i=0;i<9;i++)inv.setItem(9+i,reel.get(i));
            inv.setItem(13,displayIcon(finalReward,true));
            p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,1f,1.1f);
            BukkitTask t=Bukkit.getScheduler().runTaskLater(plugin,()->deliverPending(p),15L);
            animations.put(p.getUniqueId(),t); return;
        }
        for(int i=0;i<8;i++)reel.set(i,reel.get(i+1));
        reel.set(8,displayIcon(selectReward(type),false));
        for(int i=0;i<9;i++)inv.setItem(9+i,reel.get(i));
        if(step%2==0)p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,.2f,1.5f);
        long delay=step<18?2:step<28?4:6;
        BukkitTask t=Bukkit.getScheduler().runTaskLater(plugin,()->animateStep(p,type,inv,finalReward,reel,step+1),delay);
        animations.put(p.getUniqueId(),t);
    }

    private void deliverPending(Player p){
        animations.remove(p.getUniqueId()); opening.remove(p.getUniqueId());
        Pending reward=pending.remove(p.getUniqueId()); if(reward==null)return;
        giveReward(p,reward.reward());
        p.sendMessage(ChatColor.GREEN+"🎉 "+cap(reward.type())+" Crate Reward!");
        save();
    }
    @EventHandler public void quit(PlayerQuitEvent e){
        UUID u=e.getPlayer().getUniqueId();
        BukkitTask t=animations.remove(u); if(t!=null)t.cancel();
        opening.remove(u);
        chancePrompts.remove(u);
        closeEditorWithoutSave(e.getPlayer());
        save();
    }
    @EventHandler public void join(PlayerJoinEvent e){
        Bukkit.getScheduler().runTask(plugin,()->{
            deliverPending(e.getPlayer());
            for(String k:crates.keySet())ensureHologramIfLoaded(k,e.getPlayer().getWorld());
        });
    }
    private void ensureHologramIfLoaded(String key,World world){
        Block b=block(key);
        if(b!=null&&b.getWorld().equals(world)&&world.isChunkLoaded(b.getX()>>4,b.getZ()>>4))ensureHologram(key);
    }
    @EventHandler public void chunkLoad(ChunkLoadEvent e){
        for(String k:crates.keySet()){
            Block b=block(k);
            if(b!=null&&b.getWorld().equals(e.getWorld())&&(b.getX()>>4)==e.getChunk().getX()&&(b.getZ()>>4)==e.getChunk().getZ())ensureHologram(k);
        }
    }

    private List<Reward> rewardEntries(String type){
        List<Reward> out=new ArrayList<>();
        for(String raw:plugin.getConfig().getStringList("crates.rewards."+type)){
            String line=raw.trim(); if(line.isBlank())continue;
            double chance=1D; String reward=line;
            int pipe=line.indexOf('|');
            if(pipe>0){
                try{chance=Double.parseDouble(line.substring(0,pipe).trim());reward=line.substring(pipe+1).trim();}
                catch(Exception ignored){}
            }
            if(chance<=0||chance>100)continue;
            ItemStack item=decodeItem(reward);
            out.add(new Reward(chance,reward,item));
        }
        return out;
    }

    private Reward selectReward(String type){
        List<Reward> entries=rewardEntries(type);
        if(entries.isEmpty())return new Reward(1,"$0",null);
        double total=entries.stream().mapToDouble(Reward::chance).sum();
        double roll=random.nextDouble()*total,cur=0;
        for(Reward r:entries){cur+=r.chance();if(roll<cur)return r;}
        return entries.get(entries.size()-1);
    }

    private ItemStack displayIcon(Reward reward,boolean winner){
        ItemStack i;
        if(reward.item()!=null)i=reward.item().clone();
        else if(reward.raw().startsWith("$"))i=new ItemStack(Material.GOLD_INGOT);
        else if(reward.raw().startsWith("shards:"))i=new ItemStack(Material.EMERALD);
        else{
            Material m=Material.matchMaterial(reward.raw().split(":",2)[0].trim().toUpperCase(Locale.ROOT));
            if(m==null||m.isAir())m=Material.PAPER;
            i=new ItemStack(m);
        }
        ItemMeta meta=i.getItemMeta();
        if(meta!=null){
            List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>();
            lore.add("§7Chance: §f"+formatChance(reward.chance())+"%");
            if(winner)lore.add("§a§lFINAL REWARD");
            meta.setLore(lore);
            i.setItemMeta(meta);
        }
        return i;
    }

    private void giveReward(Player p,String reward){
        ItemStack custom=decodeItem(reward);
        if(custom!=null){
            custom=plugin.getDrillManager().activate(custom);
            give(p,custom);
            return;
        }
        if(reward.startsWith("$")){
            try{
                long a=Long.parseLong(reward.substring(1).trim());
                if(a>0)plugin.getEconomyManager().depositToUuid(p.getUniqueId(),a,p.getName());
            }catch(Exception ignored){}
            return;
        }
        if(reward.startsWith("shards:")){
            try{
                long a=Long.parseLong(reward.substring(7).trim());
                long c=plugin.getPlayerDataManager().getEmeraldShards(p.getUniqueId());
                plugin.getPlayerDataManager().setEmeraldShards(p.getUniqueId(),c+Math.max(0,a));
            }catch(Exception ignored){}
            return;
        }
        String[] parts=reward.split(":",2);
        Material m=Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));
        if(m==null||m.isAir())return;
        int amount=1;
        if(parts.length==2)try{amount=Math.max(1,Integer.parseInt(parts[1].trim()));}catch(Exception e){return;}
        while(amount>0){int n=Math.min(m.getMaxStackSize(),amount);give(p,new ItemStack(m,n));amount-=n;}
    }

    private String encodeItem(ItemStack item){
        return "item64:"+Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }
    private ItemStack decodeItem(String raw){
        if(raw==null||!raw.startsWith("item64:"))return null;
        try{return ItemStack.deserializeBytes(Base64.getDecoder().decode(raw.substring(7)));}
        catch(Exception ex){plugin.getLogger().warning("Invalid crate ItemStack reward: "+ex.getMessage());return null;}
    }

    public void openInfo(Player p){
        Inventory inv=Bukkit.createInventory(null,27,"§2§l🎁 EMERALD SMP CRATES");
        int[] slots={10,11,12,13,14};
        for(int i=0;i<TYPES.size();i++){
            String t=TYPES.get(i); ItemStack s=new ItemStack(crateMaterial(t)); ItemMeta m=s.getItemMeta();
            m.setDisplayName("§a§l"+icon(t)+" "+cap(t)+" Crate");
            m.setLore(List.of("§7🔑 Required: §f"+cap(t)+" Key","§7Click to view rewards"));
            s.setItemMeta(m); inv.setItem(slots[i],s);
        }
        p.openInventory(inv);
    }

    private void openRewards(Player p,String type){
        Inventory inv=Bukkit.createInventory(null,54,"§2§l🎁 "+cap(type)+" REWARDS");
        int slot=0;
        for(Reward reward:rewardEntries(type)){
            if(slot>=45)break;
            ItemStack icon=displayIcon(reward,false);
            ItemMeta m=icon.getItemMeta();
            if(m!=null){
                List<String> lore=m.hasLore()?new ArrayList<>(m.getLore()):new ArrayList<>();
                lore.add("§7Reward: §f"+prettyReward(reward.raw()));
                m.setLore(lore);icon.setItemMeta(m);
            }
            inv.setItem(slot++,icon);
        }
        inv.setItem(49,new ItemStack(Material.ARROW));
        p.openInventory(inv);
    }

    public void openEditor(Player p,String type){
        type=normalize(type);
        UUID u=p.getUniqueId();
        editors.put(u,type);
        editorSaved.remove(u);
        chancePrompts.remove(u);
        Map<Integer,Double> chances=new HashMap<>();
        Map<String,Integer> originals=new HashMap<>();
        int slot=0;
        Inventory inv=Bukkit.createInventory(null,54,"§2§l✏ "+cap(type)+" CRATE EDITOR");
        for(Reward reward:rewardEntries(type)){
            if(slot>=45)break;
            if(reward.item()!=null){
                ItemStack item=reward.item().clone();
                inv.setItem(slot,item);
                chances.put(slot,reward.chance());
                String id=identityToken(item);
                originals.merge(id,item.getAmount(),Integer::sum);
                slot++;
            }
        }
        editorChances.put(u,chances);
        editorOriginals.put(u,originals);
        inv.setItem(48,button(Material.PAPER,"§e✏ SET CHANCE",List.of("§7Right-click a reward first","§7Then enter a number from 0 to 100 in chat")));
        inv.setItem(49,button(Material.EMERALD,"§a§l💾 SAVE","§7Save the reward pool","§7Changes are not saved on close"));
        inv.setItem(50,button(Material.ARROW,"§eCancel / Close","§7Close without saving","§7New items are returned"));
        p.openInventory(inv);
    }

    private ItemStack button(Material m,String name,String... lore){
        ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();
        meta.setDisplayName(name);meta.setLore(Arrays.asList(lore));i.setItemMeta(meta);return i;
    }
    private String identityToken(ItemStack item){
        ItemStack one=item.clone();one.setAmount(1);
        return Base64.getEncoder().encodeToString(one.serializeAsBytes());
    }
    private String formatChance(double d){
        if(Math.rint(d)==d)return Long.toString((long)d);
        return String.format(Locale.US,"%.2f",d);
    }
    private String prettyReward(String raw){
        if(raw.startsWith("item64:")){
            ItemStack i=decodeItem(raw); return i==null?"Invalid Item":pretty(i);
        }
        return raw;
    }
    private String pretty(ItemStack i){
        if(i==null)return "Item";
        if(i.hasItemMeta()&&i.getItemMeta().hasDisplayName())return ChatColor.stripColor(i.getItemMeta().getDisplayName());
        return i.getType().name().toLowerCase(Locale.ROOT).replace('_',' ');
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void inventoryClick(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String title=ChatColor.stripColor(e.getView().getTitle());
        if(title.equals("🎁 EMERALD SMP CRATES")){
            e.setCancelled(true);
            int s=e.getRawSlot();
            if(s>=10&&s<=14)openRewards(p,TYPES.get(s-10));
            return;
        }
        if(title.contains(" REWARDS")&&title.startsWith("🎁")){
            if(e.getRawSlot()==49){e.setCancelled(true);openInfo(p);}
            else e.setCancelled(true);
            return;
        }

        String type=editors.get(p.getUniqueId());
        if(type==null||!title.equals("✏ "+cap(type)+" CRATE EDITOR"))return;

        int raw=e.getRawSlot();
        if(raw>=45){
            e.setCancelled(true);
            if(raw==49){saveEditor(p);return;}
            if(raw==50){p.closeInventory();return;}
            return;
        }

        // Right-clicking an existing reward edits its chance instead of taking the item.
        if(raw>=0&&raw<45&&e.isRightClick()&&e.getCurrentItem()!=null&&!e.getCurrentItem().getType().isAir()){
            e.setCancelled(true);
            chancePrompts.put(p.getUniqueId(),raw);
            double chance=editorChances.getOrDefault(p.getUniqueId(),Map.of()).getOrDefault(raw,1D);
            p.sendMessage(ChatColor.YELLOW+"Enter the chance for slot "+(raw+1)+" (0-100). Current: "+formatChance(chance)+"%. Type 'cancel' to abort.");
            return;
        }
    }

    @EventHandler public void inventoryDrag(InventoryDragEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String type=editors.get(p.getUniqueId());
        if(type!=null&&e.getView().getTitle().equals("§2§l✏ "+cap(type)+" CRATE EDITOR")){
            if(e.getRawSlots().stream().anyMatch(s->s>=45))e.setCancelled(true);
        }
    }

    @EventHandler public void chatChance(AsyncPlayerChatEvent e){
        Player p=e.getPlayer(); Integer slot=chancePrompts.get(p.getUniqueId());
        if(slot==null)return;
        e.setCancelled(true);
        String msg=e.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!editors.containsKey(p.getUniqueId())){chancePrompts.remove(p.getUniqueId());return;}
            if(msg.equalsIgnoreCase("cancel")){chancePrompts.remove(p.getUniqueId());p.sendMessage(ChatColor.GRAY+"Chance edit cancelled.");return;}
            try{
                double value=Double.parseDouble(msg);
                if(value<0||value>100||Double.isNaN(value)){p.sendMessage(ChatColor.RED+"Chance must be between 0 and 100.");return;}
                editorChances.computeIfAbsent(p.getUniqueId(),k->new HashMap<>()).put(slot,value);
                Inventory inv=p.getOpenInventory().getTopInventory();
                ItemStack current=inv.getItem(slot);
                if(current!=null){
                    ItemStack shown=current.clone();ItemMeta m=shown.getItemMeta();
                    List<String> lore=m!=null&&m.hasLore()?new ArrayList<>(m.getLore()):new ArrayList<>();
                    lore.removeIf(s->ChatColor.stripColor(s).startsWith("Chance:"));
                    lore.add("§6Chance: §f"+formatChance(value)+"%");
                    if(m!=null){m.setLore(lore);shown.setItemMeta(m);}
                    inv.setItem(slot,shown);
                }
                chancePrompts.remove(p.getUniqueId());
                p.sendMessage(ChatColor.GREEN+"Chance set to "+formatChance(value)+"%.");
            }catch(NumberFormatException ex){p.sendMessage(ChatColor.RED+"Enter a number from 0 to 100.");}
        });
    }

    private void saveEditor(Player p){
        UUID u=p.getUniqueId(); String type=editors.get(u);
        if(type==null)return;
        Inventory inv=p.getOpenInventory().getTopInventory();
        List<String> rewards=new ArrayList<>();
        Map<Integer,Double> chances=editorChances.getOrDefault(u,Map.of());
        for(int i=0;i<45;i++){
            ItemStack s=inv.getItem(i);
            if(s==null||s.getType().isAir())continue;
            double chance=Math.max(0D,Math.min(100D,chances.getOrDefault(i,1D)));
            if(chance<=0)continue;
            rewards.add(formatChance(chance)+"|"+encodeItem(s));
        }
        // Preserve configured money/shard rewards that the physical editor cannot represent.
        for(String raw:plugin.getConfig().getStringList("crates.rewards."+type)){
            String reward=raw.contains("|")?raw.substring(raw.indexOf('|')+1).trim():raw.trim();
            if(reward.startsWith("$")||reward.startsWith("shards:"))rewards.add(raw);
        }
        plugin.getConfig().set("crates.rewards."+type,rewards);
        plugin.saveConfig();
        editorSaved.add(u);
        editors.remove(u);editorChances.remove(u);editorOriginals.remove(u);chancePrompts.remove(u);
        inv.clear();
        p.closeInventory();
        p.sendMessage(ChatColor.GREEN+"💾 Saved "+cap(type)+" crate rewards.");
    }

    private void closeEditorWithoutSave(Player p){
        UUID u=p.getUniqueId(); String type=editors.remove(u);
        chancePrompts.remove(u);editorChances.remove(u);
        if(type==null)return;
        Inventory inv=p.getOpenInventory().getTopInventory();
        if(inv==null||!ChatColor.stripColor(p.getOpenInventory().getTitle()).equals("✏ "+cap(type)+" CRATE EDITOR")){editorOriginals.remove(u);return;}
        Map<String,Integer> originals=new HashMap<>(editorOriginals.getOrDefault(u,Map.of()));
        editorOriginals.remove(u);
        for(int i=0;i<45;i++){
            ItemStack s=inv.getItem(i);
            if(s==null||s.getType().isAir())continue;
            String id=identityToken(s);
            int keep=originals.getOrDefault(id,0);
            int consumed=Math.min(keep,s.getAmount());
            if(consumed>0){
                int left=keep-consumed;
                if(left==0)originals.remove(id);else originals.put(id,left);
                continue;
            }
            inv.setItem(i,null);
            give(p,s.clone());
        }
        for(int i=0;i<45;i++)inv.setItem(i,null);
    }

    @EventHandler public void inventoryClose(InventoryCloseEvent e){
        if(!(e.getPlayer() instanceof Player p))return;
        String type=editors.get(p.getUniqueId());
        if(type==null)return;
        if(!ChatColor.stripColor(e.getView().getTitle()).equals("✏ "+cap(type)+" CRATE EDITOR"))return;
        closeEditorWithoutSave(p);
        p.sendMessage(ChatColor.YELLOW+"Changes discarded. Existing rewards were kept and new items were returned.");
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakCrate(BlockBreakEvent e){
        String t=type(e.getBlock());if(t==null)return;
        if(!e.getPlayer().hasPermission("emerald.admin")){
            e.setCancelled(true);e.getPlayer().sendMessage(ChatColor.RED+"Only an admin can remove a physical crate.");return;
        }
        String k=loc(e.getBlock());removeHologram(k);crates.remove(k);save();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blockExplosion(BlockExplodeEvent e){e.blockList().removeIf(this::isCrate);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityExplosion(EntityExplodeEvent e){e.blockList().removeIf(this::isCrate);}
    public boolean isCrate(Block b){return type(b)!=null;}
    private String normalize(String t){t=t==null?"common":t.toLowerCase(Locale.ROOT);return TYPES.contains(t)?t:"common";}
}