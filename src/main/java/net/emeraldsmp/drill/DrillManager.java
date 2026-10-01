package net.emeraldsmp.drill;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class DrillManager implements Listener {
    private final EmeraldSMP plugin;
    private final NamespacedKey toolKey,expiresKey,progressKey,targetKey;
    private final Random random=new Random();
    private static final DateTimeFormatter EXPIRY=DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());
    public enum Tool { ORIGINAL_DRILL, EMERALD_GAINER }

    public DrillManager(EmeraldSMP plugin){
        this.plugin=plugin;
        toolKey=new NamespacedKey(plugin,"emerald-miner-tool");
        expiresKey=new NamespacedKey(plugin,"emerald-miner-expires");
        progressKey=new NamespacedKey(plugin,"emerald-gainer-progress");
        targetKey=new NamespacedKey(plugin,"emerald-gainer-target");
    }
    public void load(){}
    public void stop(){}

    public ItemStack createItem(Tool tool,int tier){
        ItemStack item=new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta=item.getItemMeta();
        long exp=System.currentTimeMillis()+7L*24L*60L*60L*1000L;
        meta.setDisplayName(tool==Tool.ORIGINAL_DRILL?"§a⛏️ ORIGINAL DRILL":"§a💚 EMERALD GAINER");
        List<String> lore=new ArrayList<>();
        if(tool==Tool.ORIGINAL_DRILL){
            lore.add("§f3×3 Digout Pickaxe");
            lore.add("§7⛏ Mines a 3×3 area");
            lore.add("§7🏗 Designed for Digouts");
        }else{
            lore.add("§fNormal Mining Pickaxe");
            lore.add("§7💚 Mines blocks for Emerald Shards");
            lore.add("§7🎲 Random Trigger: §f1–32 Blocks");
            lore.add("§7💎 Reward: §f1–5 Emerald Shards");
            int target=random.nextInt(32)+1;
            meta.getPersistentDataContainer().set(progressKey,PersistentDataType.INTEGER,0);
            meta.getPersistentDataContainer().set(targetKey,PersistentDataType.INTEGER,target);
            lore.add("§7🎯 Current Progress: §f0/"+target);
        }
        lore.add("§7⏳ Expires: §f"+EXPIRY.format(Instant.ofEpochMilli(exp)));
        lore.add("§7⏱ Remaining: §f7d 0h");
        lore.add("§8Emerald SMP");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(toolKey,PersistentDataType.STRING,tool.name());
        meta.getPersistentDataContainer().set(expiresKey,PersistentDataType.LONG,exp);
        item.setItemMeta(meta);
        return item;
    }
    public ItemStack createItem(int tier){return createItem(Tool.ORIGINAL_DRILL,tier);}

    private Tool tool(ItemStack item){
        if(item==null||item.getType()!=Material.DIAMOND_PICKAXE||!item.hasItemMeta())return null;
        String v=item.getItemMeta().getPersistentDataContainer().get(toolKey,PersistentDataType.STRING);
        if(v==null)return null;
        try{return Tool.valueOf(v);}catch(Exception e){return null;}
    }
    private long expires(ItemStack item){
        if(item==null||!item.hasItemMeta())return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(expiresKey,PersistentDataType.LONG,0L);
    }
    private boolean expired(ItemStack item){return expires(item)>0&&System.currentTimeMillis()>=expires(item);}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e){
        Player p=e.getPlayer(); ItemStack held=p.getInventory().getItemInMainHand(); Tool t=tool(held);
        if(t==null)return;
        if(expired(held)){
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED+"❌ Your "+(t==Tool.ORIGINAL_DRILL?"Original Drill":"Emerald Gainer")+" has expired!");
            return;
        }
        if(t==Tool.EMERALD_GAINER) mineGainer(p,e.getBlock(),held);
        else {e.setCancelled(true);mineDrill(p,e.getBlock(),held);}
    }

    private void mineDrill(Player p,Block origin,ItemStack tool){
        if(!mineable(origin))return;
        Basis b=Basis.forPlayer(p);
        List<Block> blocks=new ArrayList<>();
        for(int a=-1;a<=1;a++)for(int c=-1;c<=1;c++){
            Block target=origin.getRelative(b.u[0]*a+b.v[0]*c,b.u[1]*a+b.v[1]*c,b.u[2]*a+b.v[2]*c);
            if(mineable(target))blocks.add(target);
        }
        for(Block block:blocks)block.breakNaturally(tool);
    }

    private void mineGainer(Player p,Block block,ItemStack tool){
        if(!mineable(block))return;
        block.breakNaturally(tool);
        ItemMeta meta=tool.getItemMeta(); var pdc=meta.getPersistentDataContainer();
        int progress=pdc.getOrDefault(progressKey,PersistentDataType.INTEGER,0)+1;
        int target=pdc.getOrDefault(targetKey,PersistentDataType.INTEGER,random.nextInt(32)+1);
        if(progress>=target){
            int roll=random.nextInt(100);
            int reward=roll<40?1:roll<60?2:roll<75?3:roll<85?4:5;
            long cur=plugin.getPlayerDataManager().getEmeraldShards(p.getUniqueId());
            plugin.getPlayerDataManager().setEmeraldShards(p.getUniqueId(),cur+reward);
            p.sendMessage(ChatColor.GREEN+"💚 +"+reward+" Emerald Shards");
            progress=0; target=random.nextInt(32)+1;
        }
        pdc.set(progressKey,PersistentDataType.INTEGER,progress);
        pdc.set(targetKey,PersistentDataType.INTEGER,target);
        updateLore(meta,target,progress);
        tool.setItemMeta(meta);
    }

    private void updateLore(ItemMeta meta,int target,int progress){
        long exp=meta.getPersistentDataContainer().getOrDefault(expiresKey,PersistentDataType.LONG,0L);
        meta.setLore(List.of(
            "§fNormal Mining Pickaxe",
            "§7💚 Mines blocks for Emerald Shards",
            "§7🎲 Random Trigger: §f1–32 Blocks",
            "§7💎 Reward: §f1–5 Emerald Shards",
            "§7🎯 Current Progress: §f"+progress+"/"+target,
            "§7⏳ Expires: §f"+EXPIRY.format(Instant.ofEpochMilli(exp)),
            "§7⏱ Remaining: §f"+remaining(exp),
            "§8Emerald SMP"
        ));
    }
    private String remaining(long exp){
        long s=Math.max(0,(exp-System.currentTimeMillis())/1000); long d=s/86400; s%=86400; long h=s/3600;
        return d+"d "+h+"h";
    }
    private boolean mineable(Block b){
        Material m=b.getType();
        return !m.isAir()&&m!=Material.WATER&&m!=Material.LAVA&&m!=Material.BEDROCK&&m!=Material.BARRIER&&m!=Material.END_PORTAL&&m!=Material.END_GATEWAY;
    }
    private static final class Basis{
        final int[] u,v; Basis(int[]u,int[]v){this.u=u;this.v=v;}
        static Basis forPlayer(Player p){
            float pitch=p.getLocation().getPitch(),yaw=p.getLocation().getYaw();
            if(Math.abs(pitch)>60)return new Basis(new int[]{1,0,0},new int[]{0,0,1});
            int dir=Math.floorMod((int)Math.floor((yaw+45)/90),4);
            return switch(dir){case 0,2->new Basis(new int[]{0,1,0},new int[]{1,0,0});default->new Basis(new int[]{0,1,0},new int[]{0,0,1});};
        }
    }
}