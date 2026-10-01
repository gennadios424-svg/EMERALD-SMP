package net.emeraldsmp;

import net.emeraldsmp.roles.RoleManager;
import net.emeraldsmp.roles.RoleUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;

public final class ServerUI implements Listener {
    private final EmeraldSMP plugin;
    private final Map<UUID,Scoreboard> boards=new HashMap<>();
    private final Map<UUID,Set<String>> oldEntries=new HashMap<>();
    private BukkitTask task;

    public ServerUI(EmeraldSMP plugin){this.plugin=plugin;}

    public void start(){
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::updateAll,20L,10L);
        for(Player p:Bukkit.getOnlinePlayers())update(p);
    }

    public void stop(){
        if(task!=null)task.cancel();
        for(Player p:Bukkit.getOnlinePlayers()){
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            p.setPlayerListHeaderFooter("","");
            p.setPlayerListName(p.getName());
        }
        boards.clear();oldEntries.clear();
    }

    @EventHandler public void join(PlayerJoinEvent e){
        Bukkit.getScheduler().runTaskLater(plugin,()->update(e.getPlayer()),2L);
    }
    @EventHandler public void quit(PlayerQuitEvent e){
        boards.remove(e.getPlayer().getUniqueId());oldEntries.remove(e.getPlayer().getUniqueId());
    }
    public void refresh(Player p){if(p!=null&&p.isOnline())update(p);}

    private void updateAll(){for(Player p:Bukkit.getOnlinePlayers())update(p);}

    private void update(Player p){
        RoleManager.Role r=plugin.getRoleManager().get(p);
        String badge=RoleUtil.roleBadge(p,plugin.getRoleManager());
        String tag=plugin.getTagsManager()==null?"":plugin.getTagsManager().active(p.getUniqueId());
        String team=plugin.getTeamManager()==null?"":plugin.getTeamManager().tag(p.getUniqueId());

        p.setPlayerListHeaderFooter(
            "\n§a§l💚 EMERALD SMP §8• §7Network",
            "§7s1.seranodes.com:25638 §8• §7MS: §b"+p.getPing()+"\n"
        );
        p.setPlayerListName(badge+" §f"+p.getName()
            +(team.isEmpty()?"":" §a[§l"+team+"§r§a]")
            +(tag.isEmpty()?"":" §8[§7"+tag+"§8]"));

        Scoreboard b=boards.computeIfAbsent(p.getUniqueId(),x->Bukkit.getScoreboardManager().getNewScoreboard());
        Objective o=b.getObjective("emerald");
        if(o==null){
            o=b.registerNewObjective("emerald","dummy","§a§l💚 EMERALD SMP");
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        Set<String> old=oldEntries.computeIfAbsent(p.getUniqueId(),x->new HashSet<>());
        for(String s:old)b.resetScores(s);
        old.clear();

        String rank=r.color()+"§l✦ "+r.label()+" ✦";
        long money=plugin.getEconomyManager().getBalance(p.getUniqueId());
        long shards=plugin.getPlayerDataManager().getEmeraldShards(p.getUniqueId());

        line(o,old,"§8━━━━━━━━━━━━",6);
        line(o,old,"§7✦ RANK: "+rank,5);
        line(o,old,"§6💰 Money: §f"+plugin.getEconomyManager().format(money),4);
        line(o,old,"§a💚 Shards: §f"+String.format(Locale.US,"%,d",shards),3);
        line(o,old,"§b📶 MS: §f"+p.getPing(),2);
        line(o,old,"§7s1.seranodes.com:25638",1);
        p.setScoreboard(b);
        updateNametags(p);
    }

    private void updateNametags(Player viewer){
        Scoreboard b=viewer.getScoreboard();
        for(Team t:new ArrayList<>(b.getTeams()))if(t.getName().startsWith("emr_"))t.unregister();
        for(Player target:Bukkit.getOnlinePlayers()){
            String id=target.getUniqueId().toString().replace("-","");
            int order=7-plugin.getRoleManager().get(target).weight();
            Team t=b.registerNewTeam(String.format(Locale.US,"emr%d%s",order,id.substring(0,12)));
            RoleManager.Role r=plugin.getRoleManager().get(target);
            String team=plugin.getTeamManager()==null?"":plugin.getTeamManager().tag(target.getUniqueId());
            String tag=plugin.getTagsManager()==null?"":plugin.getTagsManager().active(target.getUniqueId());
            t.setPrefix(r.color()+"§l✦ "+r.label()+" ✦ §f");
            t.setSuffix((team.isEmpty()?"":"§a[§l"+team+"§r§a] ")+(tag.isEmpty()?"":"§8["+tag+"§8]"));
            t.addEntry(target.getName());
        }
    }

    private void line(Objective o,Set<String> set,String s,int score){
        String x=s;while(set.contains(x))x+="§r";
        o.getScore(x).setScore(score);set.add(x);
    }
}
