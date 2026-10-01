package net.emeraldsmp.tpa;

import net.emeraldsmp.EmeraldSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import java.util.*;

public final class TpaUpgradeManager implements Listener {
    private static final long REQUEST_TICKS=20L*60L;
    private final EmeraldSMP plugin;
    private final Map<UUID,Request> outgoing=new HashMap<>(),incoming=new HashMap<>();
    private final Map<UUID,Countdown> countdowns=new HashMap<>();
    public TpaUpgradeManager(EmeraldSMP plugin){this.plugin=plugin;}
    public CommandExecutor command(Mode mode){return (sender,cmd,label,args)->{
        if(!(sender instanceof Player p)){sender.sendMessage("§cOnly players can use this command.");return true;}
        if(mode==Mode.TPA||mode==Mode.TPAHERE){if(args.length!=1){p.sendMessage("§cUsage: /"+cmd.getName()+" <player>");return true;}Player t=Bukkit.getPlayerExact(args[0]);if(t==null||!t.isOnline()){p.sendMessage("§cThat player is not online.");return true;}request(p,t,mode==Mode.TPAHERE);}
        else if(mode==Mode.ACCEPT)accept(p);else deny(p);return true;};}
    private boolean staff(Player p){return plugin.getRoleManager().isStaff(p);}
    private void request(Player s,Player t,boolean here){
        if(!staff(s)&&(plugin.getCombatManager().isInCombat(s)||plugin.getCombatManager().isInCombat(t))){s.sendMessage("§c⚔ You cannot use that command while in combat!");return;}
        if(s.getUniqueId().equals(t.getUniqueId())){s.sendMessage("§cYou cannot send a teleport request to yourself.");return;}
        if(outgoing.containsKey(s.getUniqueId())){s.sendMessage("§cYou already have a pending teleport request.");return;}
        if(incoming.containsKey(t.getUniqueId())){s.sendMessage("§cThat player already has a pending teleport request.");return;}
        Request r=new Request(s.getUniqueId(),t.getUniqueId(),s.getName(),here);outgoing.put(s.getUniqueId(),r);incoming.put(t.getUniqueId(),r);
        t.sendMessage("§8╔════════════════════════════╗");t.sendMessage(here?"§8║ §a§l💚 TELEPORT-HERE":"§8║ §a§l💚 TELEPORT REQUEST");t.sendMessage("§8╠════════════════════════════╣");t.sendMessage(here?"§8║ §f"+s.getName()+" §7wants you to teleport to them.":"§8║ §f"+s.getName()+" §7wants to teleport to you.");t.sendMessage("§8║ §7⏱ Expires in §f60s");t.sendMessage("§8╚════════════════════════════╝");
        t.sendMessage(Component.text("   ✔ ACCEPT   ").color(NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/tpaccept")).append(Component.text("   ✖ DENY").color(NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/tpdeny"))));
        t.playSound(t.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,.55f,1.15f);s.sendMessage("§a💚 Teleport request sent to §f"+t.getName()+"§a.");s.playSound(s.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.4f,1.1f);Bukkit.getScheduler().runTaskLater(plugin,()->expire(r),REQUEST_TICKS);
    }
    private void accept(Player target){
        if(!staff(target)&&plugin.getCombatManager().isInCombat(target)){target.sendMessage("§c⚔ You cannot use that command while in combat!");return;}Request r=incoming.get(target.getUniqueId());if(r==null){target.sendMessage("§cYou have no pending teleport request.");return;}if(!valid(r)){remove(r);target.sendMessage("§cThat teleport request is no longer valid.");return;}remove(r);Player s=Bukkit.getPlayer(r.sender);if(s==null||!s.isOnline()){target.sendMessage("§cThe requester is no longer online.");return;}Player tele=r.here?target:s,dest=r.here?s:target;start(tele,dest,r);target.playSound(target.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.5f,1.1f);
    }
    private void deny(Player target){
        if(!staff(target)&&plugin.getCombatManager().isInCombat(target)){target.sendMessage("§c⚔ You cannot use that command while in combat!");return;}Request r=incoming.get(target.getUniqueId());if(r==null){target.sendMessage("§cYou have no pending teleport request.");return;}remove(r);Player s=Bukkit.getPlayer(r.sender);if(s!=null&&s.isOnline()){s.sendMessage("§c❌ Your teleport request was denied.");s.playSound(s.getLocation(),Sound.ENTITY_VILLAGER_NO,.45f,.9f);}target.sendMessage("§cTeleport request denied.");target.playSound(target.getLocation(),Sound.ENTITY_VILLAGER_NO,.35f,.9f);
    }
    private void start(Player p,Player dest,Request r){UUID id=p.getUniqueId();Countdown old=countdowns.remove(id);if(old!=null)Bukkit.getScheduler().cancelTask(old.task);Countdown c=new Countdown(p.getLocation().clone());countdowns.put(id,c);c.task=Bukkit.getScheduler().runTaskTimer(plugin,()->{
        Player now=Bukkit.getPlayer(id),d=Bukkit.getPlayer(r.here?r.sender:r.target());if(countdowns.get(id)!=c||now==null||d==null||!now.isOnline()||!d.isOnline()){cancel(id,false);return;}if(moved(c.start,now.getLocation())){cancel(id,true);return;}now.sendMessage("§a💚 Teleporting in §f"+c.seconds+"§a...");now.playSound(now.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.28f,.85f+c.seconds*.07f);if(c.seconds<=1){countdowns.remove(id);Bukkit.getScheduler().cancelTask(c.task);if(now.teleport(d.getLocation())){now.sendMessage("§a✔ Teleported successfully.");now.playSound(now.getLocation(),Sound.ENTITY_ENDERMAN_TELEPORT,.7f,1f);}return;}c.seconds--;},0L,20L).getTaskId();}
    private boolean moved(Location a,Location b){return a.getWorld()==null||b.getWorld()==null||!a.getWorld().equals(b.getWorld())||a.distanceSquared(b)>0.000001D;}
    private void cancel(UUID id,boolean moved){Countdown c=countdowns.remove(id);if(c==null)return;if(c.task!=-1)Bukkit.getScheduler().cancelTask(c.task);Player p=Bukkit.getPlayer(id);if(p!=null&&p.isOnline()&&moved){p.sendMessage("§c⚠ Teleport cancelled because you moved.");p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.45f,.8f);}}
    private boolean valid(Request r){Player s=Bukkit.getPlayer(r.sender),t=Bukkit.getPlayer(r.target());return s!=null&&s.isOnline()&&t!=null&&t.isOnline();}
    private void expire(Request r){if(outgoing.get(r.sender)!=r)return;remove(r);Player s=Bukkit.getPlayer(r.sender),t=Bukkit.getPlayer(r.target());if(s!=null&&s.isOnline())s.sendMessage("§cTeleport request expired.");if(t!=null&&t.isOnline())t.sendMessage("§cTeleport request expired.");}
    private void remove(Request r){outgoing.remove(r.sender,r);incoming.remove(r.target,r);}
    @EventHandler public void move(PlayerMoveEvent e){if(!countdowns.containsKey(e.getPlayer().getUniqueId()))return;if(e.getTo()==null||moved(e.getFrom(),e.getTo()))cancel(e.getPlayer().getUniqueId(),true);}
    @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();Request a=outgoing.remove(id);if(a!=null)incoming.remove(a.target(),a);Request b=incoming.remove(id);if(b!=null)outgoing.remove(b.sender(),b);cancel(id,false);}
    @EventHandler public void disable(PluginDisableEvent e){if(!e.getPlugin().equals(plugin))return;for(Countdown c:countdowns.values())if(c.task!=-1)Bukkit.getScheduler().cancelTask(c.task);countdowns.clear();outgoing.clear();incoming.clear();}
    public void shutdown(){for(Countdown c:countdowns.values())if(c.task!=-1)Bukkit.getScheduler().cancelTask(c.task);countdowns.clear();outgoing.clear();incoming.clear();}
    public enum Mode{TPA,TPAHERE,ACCEPT,DENY}
    private record Request(UUID sender,UUID target,String senderName,boolean here){}
    private static final class Countdown{final Location start;int seconds=5,task=-1;Countdown(Location l){start=l;}}
}
