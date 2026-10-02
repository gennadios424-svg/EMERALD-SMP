package net.emeraldsmp.afk;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AfkManager {
    private final EmeraldSMP plugin;
    private final Map<UUID,Long> lastActivity=new ConcurrentHashMap<>();
    private final Map<UUID,Boolean> afk=new ConcurrentHashMap<>();
    private final Map<UUID,Long> lastReward=new ConcurrentHashMap<>();
    private BukkitTask task;
    private final NamespacedKey hologramKey;
    private Location afkLocation;
    public AfkManager(EmeraldSMP plugin){this.plugin=plugin;this.hologramKey=new NamespacedKey(plugin,"emerald-afk-hologram");loadLocation();}
    public void start(){if(!plugin.getConfig().getBoolean("afk.enabled",true))return;spawnHologram();task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,20L,20L);for(Player p:plugin.getServer().getOnlinePlayers())touch(p);}
    public void stop(){if(task!=null)task.cancel();lastActivity.clear();afk.clear();lastReward.clear();}
    public void join(Player p){lastActivity.put(p.getUniqueId(),System.currentTimeMillis());afk.put(p.getUniqueId(),false);lastReward.remove(p.getUniqueId());updateTab(p);}
    public void quit(Player p){lastActivity.remove(p.getUniqueId());afk.remove(p.getUniqueId());lastReward.remove(p.getUniqueId());}
    public boolean isAfk(UUID id){return afk.getOrDefault(id,false);} public EmeraldSMP getPlugin(){return plugin;}
    public void toggle(Player p){if(isAfk(p.getUniqueId()))setAfk(p,false,true);else{if(afkLocation==null){p.sendMessage("§c❌ No AFK location has been configured. An admin must use /setafk.");return;}lastActivity.put(p.getUniqueId(),System.currentTimeMillis());p.teleport(afkLocation);setAfk(p,true,true);}}
    public void touch(Player p){UUID id=p.getUniqueId();if(!lastActivity.containsKey(id))lastActivity.put(id,System.currentTimeMillis());if(isAfk(id)){setAfk(p,false,true);return;}lastActivity.put(id,System.currentTimeMillis());}
    private void tick(){if(!plugin.getConfig().getBoolean("afk.enabled",true))return;long timeout=Math.max(1L,plugin.getConfig().getLong("afk.auto-afk-minutes",10L))*60000L;long now=System.currentTimeMillis();for(Player p:plugin.getServer().getOnlinePlayers()){UUID id=p.getUniqueId();long last=lastActivity.getOrDefault(id,now);if(!isAfk(id)&&now-last>=timeout&&afkLocation!=null){p.teleport(afkLocation);setAfk(p,true,true);}if(isAfk(id)&&afkLocation!=null){if(!sameArea(p.getLocation(),afkLocation)){setAfk(p,false,true);continue;}long rewardAt=lastReward.getOrDefault(id,now);if(now-rewardAt>=300000L){long shards=plugin.getPlayerDataManager().getEmeraldShards(id);plugin.getPlayerDataManager().setEmeraldShards(id,shards+3L);lastReward.put(id,now);p.sendMessage("§b💎 AFK Reward §f+3 Emerald Shards");p.playSound(p.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,.55f,1.2f);}}updateTab(p);}}
    private boolean sameArea(Location a,Location b){return a.getWorld()!=null&&b.getWorld()!=null&&a.getWorld().getUID().equals(b.getWorld().getUID())&&a.distanceSquared(b)<=6.25D;}
    public void updateTab(Player p){if(!plugin.getConfig().getBoolean("afk.show-in-tab",true))return;String prefix=isAfk(p.getUniqueId())?"§7[AFK] ":"§a💚 ";p.setPlayerListName(prefix+"§f"+p.getName()+" §8• §7"+p.getPing()+"ms");}
    private void setAfk(Player p,boolean value,boolean message){UUID id=p.getUniqueId();if(afk.getOrDefault(id,false)==value)return;afk.put(id,value);if(value)lastReward.put(id,System.currentTimeMillis());else{lastActivity.put(id,System.currentTimeMillis());lastReward.remove(id);}updateTab(p);if(message)p.sendMessage(value?"§e💤 §fYou are now AFK.":"§a👋 §fYou are no longer AFK.");}
    public void setAfkLocation(Location location){if(location==null||location.getWorld()==null)throw new IllegalArgumentException("Invalid AFK location");afkLocation=location.clone();var c=plugin.getConfig();c.set("afk.location.world",location.getWorld().getName());c.set("afk.location.x",location.getX());c.set("afk.location.y",location.getY());c.set("afk.location.z",location.getZ());c.set("afk.location.yaw",location.getYaw());c.set("afk.location.pitch",location.getPitch());plugin.saveConfig();spawnHologram();}
    private void loadLocation(){String world=plugin.getConfig().getString("afk.location.world","");if(world==null||world.isBlank())return;World w=Bukkit.getWorld(world);if(w==null)return;afkLocation=new Location(w,plugin.getConfig().getDouble("afk.location.x"),plugin.getConfig().getDouble("afk.location.y"),plugin.getConfig().getDouble("afk.location.z"),(float)plugin.getConfig().getDouble("afk.location.yaw"),(float)plugin.getConfig().getDouble("afk.location.pitch"));}
    private void spawnHologram(){if(afkLocation==null||afkLocation.getWorld()==null)return;Location base=afkLocation.clone().add(.5,2.2,.5);for(Entity e:base.getWorld().getNearbyEntities(base,1.2,2.5,1.2)){if(e.getPersistentDataContainer().has(hologramKey,PersistentDataType.BYTE)){e.remove();}}String[] lines={"§a§l💚 EMERALD SMP","§e§l💤 AFK REWARDS","§b💎 +3 Emerald Shards","§7⏱ Every 5 Minutes","§fUse /afk"};for(int i=0;i<lines.length;i++){int n=i;ArmorStand as=base.getWorld().spawn(base.clone().add(0,-n*.3,0),ArmorStand.class,a->{a.setInvisible(true);a.setMarker(true);a.setGravity(false);a.setInvulnerable(true);a.setCustomNameVisible(true);a.setCustomName(lines[n]);a.getPersistentDataContainer().set(hologramKey,PersistentDataType.BYTE,(byte)1);});}}
}
