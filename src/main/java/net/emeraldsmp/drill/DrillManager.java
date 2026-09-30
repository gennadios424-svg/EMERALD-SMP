package net.emeraldsmp.drill;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;\nimport org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class DrillManager implements Listener {
    private static final String TITLE="§2§l💚 EMERALD MINER";
    private final EmeraldSMP plugin;
    private final File file;
    private final Map<UUID,Stats> stats=new HashMap<>();
    private final NamespacedKey toolKey,expiresKey;
    private final Random random=new Random();

    public enum Tool { ORIGINAL_DRILL, EMERALD_GAINER }
    public static final class Stats {
        int tier=1; long level=1,xp=0,totalBlocks=0,originalBlocks=0,gainerBlocks=0,shardsEarned=0,shardTriggers=0;
    }
    public DrillManager(EmeraldSMP plugin){
        this.plugin=plugin; file=new File(plugin.getDataFolder(),"drills.yml");
        toolKey=new NamespacedKey(plugin,"emerald-miner-tool"); expiresKey=new NamespacedKey(plugin,"emerald-miner-expires");
    }
    public void load(){
        stats.clear(); if(!file.exists()) return;
        YamlConfiguration y=YamlConfiguration.loadConfiguration(file); ConfigurationSection root=y.getConfigurationSection("players");
        if(root==null)return;
        for(String id:root.getKeys(false))try{
            UUID u=UUID.fromString(id); Stats s=new Stats(); String p="players."+id;
            s.tier=Math.max(1,y.getInt(p+".tier",1)); s.level=Math.max(1,y.getLong(p+".level",1)); s.xp=Math.max(0,y.getLong(p+".xp",0));
            s.totalBlocks=Math.max(0,y.getLong(p+".total-blocks",0)); s.originalBlocks=Math.max(0,y.getLong(p+".original-blocks",0));
            s.gainerBlocks=Math.max(0,y.getLong(p+".gainer-blocks",0)); s.shardsEarned=Math.max(0,y.getLong(p+".shards-earned",0)); s.shardTriggers=Math.max(0,y.getLong(p+".shard-triggers",0));
            stats.put(u,s);
        }catch(Exception ignored){}
    }
    public void save(){
        YamlConfiguration y=new YamlConfiguration();
        for(var e:stats.entrySet()){String p="players."+e.getKey();Stats s=e.getValue();
            y.set(p+".tier",s.tier);y.set(p+".level",s.level);y.set(p+".xp",s.xp);y.set(p+".total-blocks",s.totalBlocks);
            y.set(p+".original-blocks",s.originalBlocks);y.set(p+".gainer-blocks",s.gainerBlocks);y.set(p+".shards-earned",s.shardsEarned);y.set(p+".shard-triggers",s.shardTriggers);
        }
        try{y.save(file);}catch(IOException ex){plugin.getLogger().warning("Could not save drills.yml: "+ex.getMessage());}
    }
    public void stop(){save();}

    public ItemStack createItem(Tool tool,int tier){
        tier=Math.max(1,Math.min(5,tier)); ItemStack i=new ItemStack(Material.DIAMOND_PICKAXE); ItemMeta m=i.getItemMeta();
        String name=tool==Tool.ORIGINAL_DRILL?"§a⛏️ ORIGINAL DRILL":"§a💚 EMERALD GAINER";
        m.setDisplayName(name+" §fTier "+roman(tier));
        long exp=System.currentTimeMillis()+Math.max(1,plugin.getConfig().getLong("emerald-miner.expiration-days",7))*86400000L;
        List<String> lore=new ArrayList<>();
        if(tool==Tool.ORIGINAL_DRILL){lore.add("§7💚 EMERALD MINER");lore.add("§f3×3 Mining Tool");lore.add("§7⛏ Mines a 3×3 area");lore.add("§7🏗 Designed for Digouts");}
        else {lore.add("§7💚 EMERALD MINER");lore.add("§fShard-farming pickaxe");lore.add("§7💚 Earn Emerald Shards while mining");lore.add("§7🎲 Random trigger: §f1–128 blocks");lore.add("§7💎 Reward: §f1–5 Emerald Shards");}
        lore.add("§7⏳ Expires In: §f"+remaining(exp)); lore.add("§8Emerald SMP");
        m.setLore(lore); m.getPersistentDataContainer().set(toolKey,PersistentDataType.STRING,tool.name()); m.getPersistentDataContainer().set(expiresKey,PersistentDataType.LONG,exp); i.setItemMeta(m); return i;
    }
    public ItemStack createItem(int tier){return createItem(Tool.ORIGINAL_DRILL,tier);}
    private Tool tool(ItemStack i){
        if(i==null||i.getType()!=Material.DIAMOND_PICKAXE||!i.hasItemMeta())return null;
        String v=i.getItemMeta().getPersistentDataContainer().get(toolKey,PersistentDataType.STRING); if(v==null)return null;
        try{return Tool.valueOf(v);}catch(Exception e){return null;}
    }
    private long expires(ItemStack i){return i==null||!i.hasItemMeta()?0:i.getItemMeta().getPersistentDataContainer().getOrDefault(expiresKey,PersistentDataType.LONG,0L);}
    private boolean expired(ItemStack i){return expires(i)>0&&System.currentTimeMillis()>=expires(i);}
    private void expiredMessage(Player p,Tool t){p.sendMessage(ChatColor.RED+"❌ Your "+(t==Tool.ORIGINAL_DRILL?"Original Drill":"Emerald Gainer")+" has expired!");}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e){
        Player p=e.getPlayer(); ItemStack held=p.getInventory().getItemInMainHand(); Tool t=tool(held); if(t==null)return;
        if(expired(held)){e.setCancelled(true);expiredMessage(p,t);return;}
        if(t==Tool.EMERALD_GAINER){e.setCancelled(true);mineGainer(p,e.getBlock(),held);return;}
        e.setCancelled(true);mineDrill(p,e.getBlock(),held);
    }
    private void mineDrill(Player p,Block origin,ItemStack tool){
        if(!isMineable(origin.getType()))return;
        BlockFaceBasis basis=BlockFaceBasis.of(p);
        List<Block> blocks=new ArrayList<>();
        for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++){
            Block target=origin.getRelative(basis.u[0]*a+basis.u[1]*a+basis.u[2]*a).getRelative(basis.v[0]*b+basis.v[1]*b+basis.v[2]*b);
            if(isMineable(target.getType()))blocks.add(target);
        }
        for(Block b:blocks)b.breakNaturally(tool);
        recordMine(p,Tool.ORIGINAL_DRILL,blocks.size());
    }
    private void mineGainer(Player p,Block origin,ItemStack tool){
        if(!isMineable(origin.getType()))return;
        origin.breakNaturally(tool); recordMine(p,Tool.EMERALD_GAINER,1);
    }
    private static final class BlockFaceBasis{
        int[] u,v; BlockFaceBasis(int[]u,int[]v){this.u=u;this.v=v;}
        static BlockFaceBasis of(Player p){
            float pitch=p.getLocation().getPitch(); float yaw=p.getLocation().getYaw();
            if(Math.abs(pitch)>60) return new BlockFaceBasis(new int[]{1,0,0},new int[]{0,0,1});
            int dir=Math.floorMod((int)Math.floor((yaw+45)/90),4);
            return switch(dir){case 0,2->new BlockFaceBasis(new int[]{0,1,0},new int[]{1,0,0});default->new BlockFaceBasis(new int[]{0,1,0},new int[]{0,0,1});};
        }
    }
    private void recordMine(Player p,Tool t,int blocks){
        if(blocks<=0)return; Stats s=stats.computeIfAbsent(p.getUniqueId(),k->new Stats()); s.totalBlocks+=blocks;
        if(t==Tool.ORIGINAL_DRILL)s.originalBlocks+=blocks; else {s.gainerBlocks+=blocks; gainerReward(p,s);}
        long xp=plugin.getConfig().getLong("emerald-miner.xp-per-block",1); s.xp+=xp*blocks; long req=xpRequired(s.level,s.tier);
        while(s.xp>=req){s.xp-=req;s.level++;if(s.level>plugin.getConfig().getLong("emerald-miner.levels-per-tier",10)&&s.tier<5){s.tier++;s.level=1;}}
        save();
    }
    private void gainerReward(Player p,Stats s){
        long max=Math.max(1,plugin.getConfig().getLong("emerald-miner.gainer.max-blocks",128));
        // A target is rolled after each trigger; accumulated mining blocks make the trigger genuinely random.
        int target=1+random.nextInt((int)Math.min(Integer.MAX_VALUE,max));
        // Deterministic accumulation is handled by a persisted counter encoded in gainerBlocks modulo a random target is not possible.
        // Use a per-player target map so the target persists for the online session; stats remain UUID persisted.
        UUID u=p.getUniqueId(); int progress=gainerProgress.getOrDefault(u,0)+1; int next=gainerTarget.getOrDefault(u,1);
        if(progress>=next){int reward=1+random.nextInt(5);s.shardsEarned+=reward;s.shardTriggers++;
            long cur=plugin.getPlayerDataManager().getEmeraldShards(u);plugin.getPlayerDataManager().setEmeraldShards(u,cur+reward);
            p.sendMessage(ChatColor.GREEN+"💚 +"+reward+" Emerald Shards"); progress=0; next=1+random.nextInt((int)max);
        }
        gainerProgress.put(u,progress);gainerTarget.put(u,next);
    }
    private final Map<UUID,Integer> gainerProgress=new HashMap<>(),gainerTarget=new HashMap<>();

    private long xpRequired(long level,int tier){long base=Math.max(1,plugin.getConfig().getLong("emerald-miner.blocks-per-level",100));return base+Math.max(0,level-1)*base/10;}
    private boolean isMineable(Material m){return m.isBlock()&&m.isSolid()&&m!=Material.BEDROCK&&m!=Material.BARRIER&&m!=Material.SPAWNER;}

    @EventHandler public void interact(PlayerInteractEvent e){
        if(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        Tool t=tool(e.getItem()); if(t==null)return;
        if(expired(e.getItem())){e.setCancelled(true);expiredMessage(e.getPlayer(),t);return;}
        e.setCancelled(true);
    }
    public void open(Player p){
        Stats s=stats.computeIfAbsent(p.getUniqueId(),k->new Stats()); Inventory inv=Bukkit.createInventory(null,45,TITLE);
        inv.setItem(4,item(Material.DIAMOND_PICKAXE,"§a⛏️ ORIGINAL DRILL",List.of("§73×3 Digout Mining","§7Tier: §a"+s.tier,"§7Use the drill to mine a 3×3 plane")));
        inv.setItem(13,item(Material.EMERALD,"§a💚 EMERALD GAINER",List.of("§7Random shard mining","§7Trigger: §f1–128 blocks","§7Reward: §f1–5 Emerald Shards")));
        long req=xpRequired(s.level,s.tier); inv.setItem(22,item(Material.EXPERIENCE_BOTTLE,"§b📈 PROGRESSION",List.of("§7Tier: §f"+roman(s.tier),"§7Level: §f"+s.level,"§7XP: §a"+s.xp+"§7/§f"+req)));
        inv.setItem(29,item(Material.BOOK,"§f📊 STATISTICS",List.of("§7⛏ Total Blocks: §f"+s.totalBlocks,"§7⛏ Original Drill: §f"+s.originalBlocks,"§7💚 Gainer Blocks: §f"+s.gainerBlocks,"§7💚 Shards Earned: §f"+s.shardsEarned,"§7🎯 Shard Triggers: §f"+s.shardTriggers)));
        inv.setItem(33,item(Material.CLOCK,"§e⏳ ACTIVE TOOLS",List.of(activeLine(p,Tool.ORIGINAL_DRILL),activeLine(p,Tool.EMERALD_GAINER))));
        inv.setItem(40,item(Material.LIME_DYE,"§a⬆️ UPGRADES",List.of("§7Tier progression is active","§7Future abilities can be added here")));
        p.openInventory(inv);
    }
    private String activeLine(Player p,Tool t){ItemStack i=t==Tool.ORIGINAL_DRILL?find(p,Tool.ORIGINAL_DRILL):find(p,Tool.EMERALD_GAINER);if(i==null)return "§7"+label(t)+": §cNot held";return expired(i)?"§7"+label(t)+": §cExpired":"§7"+label(t)+": §a"+remaining(expires(i));}
    private ItemStack find(Player p,Tool t){for(ItemStack i:p.getInventory().getContents())if(tool(i)==t)return i;return null;}
    private String label(Tool t){return t==Tool.ORIGINAL_DRILL?"Original Drill":"Emerald Gainer";}
    private String remaining(long end){if(end<=0)return "Unknown";long sec=Math.max(0,(end-System.currentTimeMillis())/1000);long d=sec/86400,h=(sec%86400)/3600; if(d>0)return d+"d "+h+"h";if(h>0)return h+"h";return Math.max(0,sec/60)+"m";}
    private ItemStack item(Material m,String n,List<String>l){ItemStack i=new ItemStack(m);ItemMeta x=i.getItemMeta();x.setDisplayName(n);x.setLore(l);i.setItemMeta(x);return i;}
    private String roman(int n){return switch(Math.max(1,Math.min(5,n))){case 1->"I";case 2->"II";case 3->"III";case 4->"IV";default->"V";};}
    @EventHandler public void click(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p&&e.getView().getTitle().equals(TITLE))e.setCancelled(true);}
}