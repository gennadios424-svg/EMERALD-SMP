package net.emeraldsmp.drill;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class DrillManager implements Listener {
    private final EmeraldSMP plugin;
    private final NamespacedKey toolKey, expiresKey, progressKey, targetKey;
    private final Random random=new Random();
    private static final long DURATION=7L*24L*60L*60L*1000L;
    private static final DateTimeFormatter EXPIRY=DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());
    public enum Tool { ORIGINAL_DRILL, EMERALD_GAINER }

    public DrillManager(EmeraldSMP plugin){
        this.plugin=plugin;
        toolKey=new NamespacedKey(plugin,"emerald-miner-tool");
        expiresKey=new NamespacedKey(plugin,"emerald-miner-expires");
        progressKey=new NamespacedKey(plugin,"emerald-gainer-progress");
        targetKey=new NamespacedKey(plugin,"emerald-gainer-target");
    }

    public void load(){
        plugin.getServer().getScheduler().runTaskTimer(plugin,()->{
            for(Player p:Bukkit.getOnlinePlayers()){
                updateInventory(p);
            }
        },20L,20L);
    }
    public void stop(){}

    /**
     * Creates an INACTIVE tool. No expiration timestamp is written.
     * Only crate delivery calls activate().
     */
    public ItemStack createItem(Tool tool,int tier){
        ItemStack item=new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(tool==Tool.ORIGINAL_DRILL?"§a⛏️ ORIGINAL DRILL":"§a💚 EMERALD GAINER");
        List<String> lore=new ArrayList<>();
        if(tool==Tool.ORIGINAL_DRILL){
            lore.add("§f3×3 Digout Pickaxe");
            lore.add("§7⛏ Mines a 3×3 area");
            lore.add("§7🏗 Designed for Digouts");
            lore.add("§7⏳ 7 Days");
        }else{
            int target=random.nextInt(92)+1;
            lore.add("§fNormal Mining Pickaxe");
            lore.add("§7💚 Mines blocks for Emerald Shards");
            lore.add("§7🎲 Random Trigger: §f1–92 Blocks");
            lore.add("§7💎 Reward: §f1–5 Emerald Shards");
            lore.add("§7🎯 Current Progress: §f0/"+target);
            meta.getPersistentDataContainer().set(progressKey,PersistentDataType.INTEGER,0);
            meta.getPersistentDataContainer().set(targetKey,PersistentDataType.INTEGER,target);
            lore.add("§7⏳ 7 Days");
        }
        lore.add("§8Inactive • Timer starts when won from a crate");
        lore.add("§8Emerald SMP");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(toolKey,PersistentDataType.STRING,tool.name());
        meta.getPersistentDataContainer().remove(expiresKey);
        item.setItemMeta(meta);
        return item;
    }
    public ItemStack createItem(int tier){return createItem(Tool.ORIGINAL_DRILL,tier);}

    public ItemStack activate(ItemStack source){
        ItemStack item=source==null?null:source.clone();
        Tool t=tool(item);
        if(t==null)return item;
        ItemMeta meta=item.getItemMeta();
        var pdc=meta.getPersistentDataContainer();
        long existing=pdc.getOrDefault(expiresKey,PersistentDataType.LONG,0L);
        if(existing<=0L) pdc.set(expiresKey,PersistentDataType.LONG,System.currentTimeMillis()+DURATION);
        if(t==Tool.EMERALD_GAINER){
            if(!pdc.has(progressKey,PersistentDataType.INTEGER)) pdc.set(progressKey,PersistentDataType.INTEGER,0);
            if(!pdc.has(targetKey,PersistentDataType.INTEGER)) pdc.set(targetKey,PersistentDataType.INTEGER,random.nextInt(92)+1);
        }
        updateLore(meta,t);
        item.setItemMeta(meta);
        return item;
    }

    private Tool tool(ItemStack item){
        if(item==null||item.getType()!=Material.DIAMOND_PICKAXE||!item.hasItemMeta())return null;
        String v=item.getItemMeta().getPersistentDataContainer().get(toolKey,PersistentDataType.STRING);
        if(v==null)return null;
        try{return Tool.valueOf(v);}catch(Exception e){return null;}
    }
    private long expires(ItemStack item){
        if(item==null||!item.hasItemMeta())return 0L;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(expiresKey,PersistentDataType.LONG,0L);
    }
    private boolean expired(ItemStack item){long e=expires(item);return e>0L&&System.currentTimeMillis()>=e;}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e){
        Player p=e.getPlayer();
        ItemStack held=p.getInventory().getItemInMainHand();
        Tool t=tool(held);
        if(t==null)return;
        long exp=expires(held);
        if(exp<=0L){
            e.setCancelled(true);
            p.sendMessage(ChatColor.YELLOW+"⚠ This "+(t==Tool.ORIGINAL_DRILL?"Original Drill":"Emerald Gainer")+" is inactive. It can only be activated by winning it from an Emerald Crate.");
            return;
        }
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
        int target=pdc.getOrDefault(targetKey,PersistentDataType.INTEGER,random.nextInt(92)+1);
        if(progress>=target){
            int roll=random.nextInt(100);
            int reward=roll<40?1:roll<60?2:roll<75?3:roll<85?4:5;
            long cur=plugin.getPlayerDataManager().getEmeraldShards(p.getUniqueId());
            plugin.getPlayerDataManager().setEmeraldShards(p.getUniqueId(),cur+reward);
            p.sendMessage(ChatColor.GREEN+"💚 +"+reward+" Emerald Shards");
            progress=0; target=random.nextInt(92)+1;
        }
        pdc.set(progressKey,PersistentDataType.INTEGER,progress);
        pdc.set(targetKey,PersistentDataType.INTEGER,target);
        updateLore(meta,Tool.EMERALD_GAINER);
        tool.setItemMeta(meta);
    }

    private void updateLore(ItemMeta meta,Tool t){
        var pdc=meta.getPersistentDataContainer();
        long exp=pdc.getOrDefault(expiresKey,PersistentDataType.LONG,0L);
        List<String> lore=new ArrayList<>();
        if(t==Tool.ORIGINAL_DRILL){
            lore.add("§f3×3 Digout Pickaxe");
            lore.add("§7⛏ Mines a 3×3 area");
            lore.add("§7🏗 Designed for Digouts");
        }else{
            int progress=pdc.getOrDefault(progressKey,PersistentDataType.INTEGER,0);
            int target=pdc.getOrDefault(targetKey,PersistentDataType.INTEGER,1);
            lore.add("§fNormal Mining Pickaxe");
            lore.add("§7💚 Mines blocks for Emerald Shards");
            lore.add("§7🎲 Random Trigger: §f1–32 Blocks");
            lore.add("§7💎 Reward: §f1–5 Emerald Shards");
            lore.add("§7🎯 Current Progress: §f"+progress+"/"+target);
        }
        if(exp>0L){
            lore.add("§7⏳ Expires: §f"+EXPIRY.format(Instant.ofEpochMilli(exp)));
            lore.add("§7⏱ Remaining: §f"+remaining(exp));
        }else{
            lore.add("§7⏳ 7 Days");
            lore.add("§8Inactive • Timer starts when won from a crate");
        }
        lore.add("§8Emerald SMP");
        meta.setLore(lore);
    }

    private void updateInventory(Player p){
        ItemStack[] contents=p.getInventory().getStorageContents();
        boolean changed=false;
        for(int i=0;i<contents.length;i++){
            ItemStack item=contents[i];
            if(tool(item)==null)continue;
            ItemStack before=item.clone();
            ItemMeta meta=item.getItemMeta();
            Tool t=tool(item);
            long exp=expires(item);
            if(exp>0L && !expired(item)){
                updateLore(meta,t);
                item.setItemMeta(meta);
            }else if(exp>0L && expired(item)){
                updateLore(meta,t);
                item.setItemMeta(meta);
            }
            if(!before.equals(item)) changed=true;
        }
        ItemStack off=p.getInventory().getItemInOffHand();
        if(tool(off)!=null){
            ItemStack before=off.clone();
            ItemMeta meta=off.getItemMeta();
            updateLore(meta,tool(off));
            off.setItemMeta(meta);
            if(!before.equals(off)) p.getInventory().setItemInOffHand(off);
        }
        if(changed)p.getInventory().setStorageContents(contents);
    }

    @EventHandler public void itemHeld(PlayerItemHeldEvent e){
        Bukkit.getScheduler().runTask(plugin,()->updateInventory(e.getPlayer()));
    }
    @EventHandler public void join(PlayerJoinEvent e){
        Bukkit.getScheduler().runTask(plugin,()->updateInventory(e.getPlayer()));
    }

    private String remaining(long exp){
        long s=Math.max(0,(exp-System.currentTimeMillis())/1000);
        long d=s/86400; s%=86400; long h=s/3600; s%=3600; long m=s/60;
        return d+"d "+h+"h "+m+"m";
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