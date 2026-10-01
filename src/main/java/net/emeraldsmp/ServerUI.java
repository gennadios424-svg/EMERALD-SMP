package net.emeraldsmp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import java.util.*;

public final class ServerUI implements Listener{
 private final EmeraldSMP plugin;private final Map<UUID,Scoreboard> boards=new HashMap<>();private final Map<UUID,Set<String>> oldEntries=new HashMap<>();private BukkitTask task;
 public ServerUI(EmeraldSMP plugin){this.plugin=plugin;}
 public void start(){task=Bukkit.getScheduler().runTaskTimer(plugin,this::updateAll,20L,20L);for(Player p:Bukkit.getOnlinePlayers())setupTab(p);updateAll();}
 public void stop(){if(task!=null)task.cancel();for(Player p:Bukkit.getOnlinePlayers()){p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());p.setPlayerListHeaderFooter("","");p.setPlayerListName(p.getName());}boards.clear();oldEntries.clear();}
 @EventHandler public void join(PlayerJoinEvent e){setupTab(e.getPlayer());Bukkit.getScheduler().runTaskLater(plugin,()->update(e.getPlayer()),2L);}
 @EventHandler public void quit(PlayerQuitEvent e){boards.remove(e.getPlayer().getUniqueId());oldEntries.remove(e.getPlayer().getUniqueId());}
 private void setupTab(Player p){int online=Bukkit.getOnlinePlayers().size();p.setPlayerListHeaderFooter("\n§a§l💚 EMERALD SMP §8• §7Survival Economy","§2s1.seranodes.com:25638 §8• §7Online: §f"+online+" §8• §7Ping: §f"+p.getPing()+"ms\n");String role=net.emeraldsmp.roles.RoleUtil.rolePrefix(p),team=plugin.getTeamManager()==null?"":plugin.getTeamManager().tag(p.getUniqueId()),tag=plugin.getTagsManager()==null?"":plugin.getTagsManager().active(p.getUniqueId());String prefix=role+(team.isEmpty()?"":"§a["+team+"] "),suffix=tag.isEmpty()?"":"§2 ["+tag+"]";p.setPlayerListName(prefix+"§f"+p.getName()+suffix+" §8• §7"+p.getPing()+"ms");}
 private void updateAll(){for(Player p:Bukkit.getOnlinePlayers())update(p);}
 private void update(Player p){setupTab(p);updateNametags(p);Scoreboard b=boards.computeIfAbsent(p.getUniqueId(),x->Bukkit.getScoreboardManager().getNewScoreboard());Objective o=b.getObjective("emerald");if(o==null){o=b.registerNewObjective("emerald","dummy","§a§l💚 EMERALD SMP");o.setDisplaySlot(DisplaySlot.SIDEBAR);}Set<String> old=oldEntries.computeIfAbsent(p.getUniqueId(),x->new HashSet<>());for(String s:old)b.resetScores(s);old.clear();long money=plugin.getEconomyManager().getBalance(p.getUniqueId()),shards=plugin.getPlayerDataManager().getEmeraldShards(p.getUniqueId());int kills=p.getStatistic(org.bukkit.Statistic.PLAYER_KILLS),online=Bukkit.getOnlinePlayers().size();line(o,old,"§6💰 Money: §f"+plugin.getEconomyManager().format(money),10);line(o,old,"§a💚 Shards: §f"+String.format(Locale.US,"%,d",shards),9);line(o,old,"§b👥 Online: §f"+online,8);line(o,old,"§c⚔ Kills: §f"+kills,7);line(o,old,"§8 ",6);line(o,old,"§2🌿 Survival",5);line(o,old,"§b💎 Emerald",4);line(o,old,"§6⚡ Economy",3);line(o,old,"§a━━━━━━━━━━━━",2);line(o,old,"§7s1.seranodes.com:25638",1);p.setScoreboard(b);}
 private void updateNametags(Player viewer){Scoreboard b=viewer.getScoreboard();for(Team t:new ArrayList<>(b.getTeams()))if(t.getName().startsWith("emr_"))t.unregister();for(Player target:Bukkit.getOnlinePlayers()){Team t=b.registerNewTeam("emr_"+target.getUniqueId().toString().replace("-","").substring(0,12));String role=net.emeraldsmp.roles.RoleUtil.rolePrefix(target),squad=plugin.getTeamManager()==null?"":plugin.getTeamManager().tag(target.getUniqueId()),tag=plugin.getTagsManager()==null?"":plugin.getTagsManager().active(target.getUniqueId());t.setPrefix(role+(squad.isEmpty()?"":"§a["+squad+"] "));t.setSuffix((tag.isEmpty()?"":"§2 ["+tag+"]")+" §8• $"+plugin.getEconomyManager().formatCompact(plugin.getEconomyManager().getBalance(target.getUniqueId())));t.addEntry(target.getName());}}
 private void line(Objective o,Set<String> set,String s,int score){String x=s;while(set.contains(x))x+="§r";o.getScore(x).setScore(score);set.add(x);}
}
