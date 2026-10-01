package net.emeraldsmp.features;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.spawner.SpawnerManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class EmeraldFeatureBridge implements Listener {
    private final EmeraldSMP plugin;
    private final NamespacedKey shopSpawnerKey;
    private final Map<UUID,Integer> shopSlots=new ConcurrentHashMap<>();
    public EmeraldFeatureBridge(EmeraldSMP plugin){this.plugin=plugin;shopSpawnerKey=new NamespacedKey(plugin,"emerald-shop-skeleton-spawner");}

    @EventHandler public void shopOpen(InventoryOpenEvent e){
        if(!(e.getPlayer() instanceof Player p))return;
        String title=ChatColor.stripColor(e.getView().getTitle());
        if(!title.startsWith("💚 SHOP")||!title.contains("CPVP"))return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!p.isOnline()||p.getOpenInventory().getTopInventory()!=e.getInventory())return;
            Inventory inv=e.getInventory();int slot=-1;for(int i=0;i<Math.min(45,inv.getSize());i++)if(inv.getItem(i)==null){slot=i;break;}if(slot<0)return;
            ItemStack item=new ItemStack(Material.SPAWNER);ItemMeta meta=item.getItemMeta();meta.setDisplayName("§a§l🧟 Skeleton Spawner");meta.setLore(List.of("§7Price: §f1,500 Emerald Shards","§7Currency: §aEmerald Shards","§7Click to purchase 1 spawner"));meta.getPersistentDataContainer().set(shopSpawnerKey,PersistentDataType.BYTE,(byte)1);item.setItemMeta(meta);inv.setItem(slot,item);shopSlots.put(p.getUniqueId(),slot);
        });
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void shopClick(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;ItemStack clicked=e.getCurrentItem();if(!isShopSpawner(clicked))return;e.setCancelled(true);
        if(!p.getOpenInventory().getTopInventory().equals(e.getView().getTopInventory()))return;
        long price=1500L;UUID u=p.getUniqueId();
        if(!hasSpace(p,1)){p.sendMessage("§cYou need inventory space for the spawner.");p.playSound(p.getLocation(),Sound.ENTITY_VILLAGER_NO,.4f,.9f);return;}
        if(!plugin.getPlayerDataManager().withdrawEmeraldShards(u,price)){p.sendMessage("§cYou need §f1,500 Emerald Shards§c to buy this spawner.");p.playSound(p.getLocation(),Sound.ENTITY_VILLAGER_NO,.4f,.9f);return;}
        Map<Integer,ItemStack> left=p.getInventory().addItem(plugin.getSpawnerManager().createItem(SpawnerManager.TYPE_SKELETON,1));
        if(!left.isEmpty()){plugin.getPlayerDataManager().setEmeraldShards(u,plugin.getPlayerDataManager().getEmeraldShards(u)+price);p.sendMessage("§cPurchase failed; your shards were refunded.");return;}
        p.sendMessage("§a💚 Purchased §fSkeleton Spawner §afor §f1,500 Emerald Shards§a.");p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.55f,1.2f);
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(isSpawnerItem(e.getItemInHand()))e.getPlayer().playSound(e.getBlockPlaced().getLocation(),Sound.BLOCK_BEACON_ACTIVATE,.3f,1.25f);}

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=false) public void breakSpawner(BlockBreakEvent e){
        if(!e.isCancelled()||e.getBlock().getType()!=Material.SPAWNER)return;
        Object data=dataAt(e.getBlock());if(data==null)return;
        try{
            String type=(String)field(data,"type").get(data);int amount=((Number)field(data,"amount").get(data)).intValue();
            Map<?,?> map=spawnerMap();((Map<?,?>)map).remove(key(e.getBlock()));e.setCancelled(false);e.getBlock().setType(Material.AIR,false);
            ItemStack drop=plugin.getSpawnerManager().createItem(type,Math.max(1,amount));Map<Integer,ItemStack> left=e.getPlayer().getInventory().addItem(drop);for(ItemStack x:left.values())e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(),x);
            plugin.getSpawnerManager().save();e.getPlayer().sendMessage("§a💚 Spawner picked up. It is not owned by anyone.");e.getPlayer().playSound(e.getPlayer().getLocation(),Sound.BLOCK_BEACON_DEACTIVATE,.45f,1.1f);
        }catch(Exception ex){plugin.getLogger().warning("Spawner steal bridge failed: "+ex.getMessage());}
    }

    private boolean isShopSpawner(ItemStack i){return i!=null&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(shopSpawnerKey,PersistentDataType.BYTE);}
    private boolean isSpawnerItem(ItemStack i){return i!=null&&i.getType()==Material.SPAWNER&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin,"emerald-spawner"),PersistentDataType.STRING);}
    private boolean hasSpace(Player p,int qty){int r=qty;for(ItemStack s:p.getInventory().getStorageContents()){if(r<=0)return true;if(s==null||s.getType().isAir())r-=64;else if(s.getType()==Material.SPAWNER)r-=Math.max(0,64-s.getAmount());}return r<=0;}
    private String key(Block b){return b.getWorld().getName()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    @SuppressWarnings("unchecked") private Map<?,?> spawnerMap()throws Exception{Field f=SpawnerManager.class.getDeclaredField("spawners");f.setAccessible(true);return (Map<?,?>)f.get(plugin.getSpawnerManager());}
    private Object dataAt(Block b)throws Exception{return spawnerMap().get(key(b));}
    private Field field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f;}
}
