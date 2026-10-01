package net.emeraldsmp.anticheat;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MovementAntiCheat implements Listener {
    private final EmeraldSMP plugin;
    private final Map<UUID,Integer> flags=new ConcurrentHashMap<>();
    private final Map<UUID,Integer> airTicks=new ConcurrentHashMap<>();
    private final Map<UUID,Long> lastFlag=new ConcurrentHashMap<>();
    public MovementAntiCheat(EmeraldSMP plugin){this.plugin=plugin;}
    private boolean bypass(Player p){return p.getGameMode()==GameMode.SPECTATOR||p.getGameMode()==GameMode.CREATIVE||plugin.getRoleManager().isStaff(p)||p.hasPermission("emerald.anticheat.bypass");}
    private void flag(Player p,String reason,int suspicion){if(bypass(p))return;long now=System.currentTimeMillis(),last=lastFlag.getOrDefault(p.getUniqueId(),0L);if(now-last<350L)return;lastFlag.put(p.getUniqueId(),now);int n=flags.merge(p.getUniqueId(),1,Integer::sum);plugin.getSusListManager().record(p,p.getLocation(),reason,n,Math.min(100,suspicion));}
    @EventHandler(ignoreCancelled=true) public void move(PlayerMoveEvent e){
        Player p=e.getPlayer();if(bypass(p)||e.getTo()==null)return;Location a=e.getFrom(),b=e.getTo();double horizontal=Math.hypot(b.getX()-a.getX(),b.getZ()-a.getZ());
        if(horizontal>1.35D&&!p.isFlying()&&!p.isGliding()&&!p.isInsideVehicle())flag(p,"Speed",65);
        if(!p.isOnGround()&&!p.isFlying()&&!p.isGliding()&&!p.isInsideVehicle()&&!p.hasPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION)){
            int air=airTicks.merge(p.getUniqueId(),1,Integer::sum);if(air>18&&Math.abs(b.getY()-a.getY())<0.035D)flag(p,"Flight",75);
        }else airTicks.remove(p.getUniqueId());
    }
    @EventHandler(ignoreCancelled=false) public void damage(EntityDamageEvent e){if(!(e.getEntity() instanceof Player p)||bypass(p))return;if(e.getCause()==EntityDamageEvent.DamageCause.FALL&&e.isCancelled()&&p.getFallDistance()>7.0f)flag(p,"NoFall",60);}
    @EventHandler(ignoreCancelled=true) public void combat(EntityDamageByEntityEvent e){if(!(e.getDamager() instanceof Player p)||bypass(p))return;Entity target=e.getEntity();double reach=p.getEyeLocation().distance(target.getLocation().add(0,target.getHeight()*.5,0));if(reach>4.35D)flag(p,"Reach",70);}
    public void disable(){flags.clear();airTicks.clear();lastFlag.clear();}
}
