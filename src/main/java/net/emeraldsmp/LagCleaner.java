package net.emeraldsmp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;

public final class LagCleaner {
    private final EmeraldSMP plugin; private BukkitTask task;
    public LagCleaner(EmeraldSMP plugin){this.plugin=plugin;}
    public void start(){stop();if(!plugin.getConfig().getBoolean("lag-cleaner.enabled",true))return;long ticks=Math.max(1,plugin.getConfig().getLong("lag-cleaner.interval-minutes",5))*60L*20L;task=Bukkit.getScheduler().runTaskTimer(plugin,this::clean,ticks,ticks);}
    public void stop(){if(task!=null){task.cancel();task=null;}}
    private void clean(){if(!plugin.getConfig().getBoolean("lag-cleaner.clear-dropped-items",true))return;int removed=0;for(World w:Bukkit.getWorlds())for(Entity e:w.getEntities())if(e instanceof Item item&&!item.isDead()){item.remove();removed++;}if(removed>0&&plugin.getConfig().getBoolean("lag-cleaner.announce",true))Bukkit.broadcastMessage(ChatColor.GREEN+"🧹 Lag Cleaner §8» §fRemoved §a"+removed+" §fdropped item(s).");}
}
