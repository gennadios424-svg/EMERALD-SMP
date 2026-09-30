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
        int tier=1; long level=1,xp=0,totalBlocks=0,originalBlocks=0,gainerBlocks=0,shardsEarned=0,shardTriggers=0,gainerProgress=0,gainerTarget=0;
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
            s.gainerBlocks=Math.max(0,y.getLong(p+".gainer-blocks",0)); s.gainerProgress=Math.max(0,y.getLong(p+".gainer-progress",0)); s.gainerTarget=Math.max(0,y.getLong(p+".gainer-target",0)); s.shardsEarned=Math.max(0,y.getLong(p+".shards-earned",0)); s.shardTriggers=Math.max(0,y.getLong(p+".shard-triggers",0));
            stats.put(u,s);
        }catch(Exception ignored){}
    }
    public void save(){
        YamlConfiguration y=new YamlConfiguration();
        for(var e:stats.entrySet()){String p="players."+e.getKey();Stats s=e.getValue();
            y.set(p+".tier",s.tier);y.set(p+".level",s.level);y.set(p+".xp",s.xp);y.set(p+".total-blocks",s.totalBlocks);
            y.set(p+".original-blocks",s.originalBlocks);y.set(p+".gainer-blocks",s.gainerBlocks);y.set(p+".gainer-progress",s.gainerProgress);y.set(p+".gainer-target",s.gainerTarget);y.set(p+".shards-earned",s.shardsEarned);y.set(p+".shard-triggers",s.shardTriggers);
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
    private int weightedShardReward(Random rng){
        int roll=rng.nextInt(100);
        if(roll<40)return 1;       // 40%
        if(roll<60)return 2;       // 20%
        if(roll<75)return 3;       // 15%
        if(roll<85)return 4;       // 10%
        return 5;                  // 15%?\n    }
    private void gainerReward(Player p,Stats s){
        long min=Math.max(1,plugin.getConfig().getLong("emerald-miner.gainer.min-blocks",1));
        long max=Math.max(min,plugin.getConfig().getLong("emerald-miner.gainer.max-blocks",128));
        if(s.gainerTarget<=0)s.gainerTarget=min+random.nextLong(max-min+1);
        s.gainerProgress++;
        if(s.gainerProgress>=s.gainerTarget){
            int lo=(int)Math.max(1,plugin.getConfig().getLong("emerald-miner.gainer.min-shards",1));
            int hi=(int)Math.max(lo,plugin.getConfig().getLong("emerald-miner.gainer.max-shards",5));
            int reward=weightedShardReward(random);
            s.shardsEarned+=reward;s.shardTriggers++; s.gainerProgress=0;s.gainerTarget=min+random.nextLong(max-min+1);
            UUID u=p.getUniqueId();long cur=plugin.getPlayerDataManager().getEmeraldShards(u);
            plugin.getPlayerDataManager().setEmeraldShards(u,cur+reward);
            p.sendMessage(ChatColor.GREEN+"💚 +"+reward+" Emerald Shards");
        }
    }}