package net.emeraldsmp;

import net.emeraldsmp.roles.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scoreboard.*;

import java.util.*;

public final class ServerUI implements Listener {
    private final EmeraldSMP plugin;
    private final Map<UUID,Scoreboard> boards=new HashMap<>();
    private final Map<UUID,Set<String>> oldEntries=new HashMap<>();
    private int taskId=-1;

    public ServerUI(EmeraldSMP plugin){this.plugin=plugin;}

    public void start(){
        taskId=Bukkit.getScheduler().runTaskTimer(plugin,this::updateAll,20L,10L).getTaskId();
        updateAll();
    }

    public void stop(){
        if(taskId!=-1)Bukkit.getScheduler().cancelTask(taskId);
        for(Player p:Bukkit.getOnlinePlayers()){
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            p.setPlayerListHeaderFooter("","");
            p.setPlayerListName(p.getName());
            p.setCustomName(null);
            p.setCustomNameVisible(false);
        }
        boards.clear(); oldEntries.clear();
    }

    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(plugin,()->update(e.getPlayer()),2L);}
    @EventHandler public void quit(PlayerQuitEvent e){boards.remove(e.getPlayer().getUniqueId());oldEntries.remove(e.getPlayer().getUniqueId());}
    public void refresh(Player p){if(p!=null&&p.isOnline())update(p);}
    private void updateAll(){for(Player p:Bukkit.getOnlinePlayers())update(p);}

    private void update(Player viewer){
        RoleManager.Role r=plugin.getRoleManager().get(viewer);
        viewer.setPlayerListHeaderFooter("\n§a§l💚 EMERALD SMP §8• §7Network","§7s1.seranodes.com:25638 §8• §7MS: §b"+viewer.getPing()+"\n");
        updatePlayerList();
        updateScoreboard(viewer,r);
        updateNametags(viewer);
    }

    private void updatePlayerList(){
        List<Player> players=new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparingInt((Player p)->-plugin.getRoleManager().get(p).weight()).thenComparing(Player::getName,String.CASE_INSENSITIVE_ORDER));
        for(Player target:players){
            RoleManager.Role role=plugin.getRoleManager().get(target);
            String team=plugin.getTeamManager()==null?"":plugin.getTeamManager().tag(target.getUniqueId());
            String teamText=team.isEmpty()?"§8[§7None§8]":"§a[§l"+team+"§r§a]";
            target.setPlayerListName(role.icon()+" §f"+target.getName()+" §7"+teamText);
            target.setCustomName("§a§l"+formatMoney(plugin.getEconomyManager().getBalance(target.getUniqueId())));
            target.setCustomNameVisible(true);
        }
        // The client sorts the player list by scoreboard team first. Give every role a
        // deterministic prefix and never create numeric fake entries.
        for(Player viewer:Bukkit.getOnlinePlayers()){
            Scoreboard board=viewer.getScoreboard();
            for(Team t:new ArrayList<>(board.getTeams())) if(t.getName().startsWith("tab_")) t.unregister();
            for(Player target:players){
                RoleManager.Role role=plugin.getRoleManager().get(target);
                String name=String.format(Locale.US,"tab_%02d_%s",99-role.weight(),target.getUniqueId().toString().replace("-","").substring(0,12));
                Team sort=board.registerNewTeam(name);
                sort.setOption(Team.Option.NAME_TAG_VISIBILITY,Team.OptionStatus.NEVER);
                sort.addEntry(target.getName());
            }
        }
    }

    private void updateScoreboard(Player viewer,RoleManager.Role role){
        Scoreboard b=boards.computeIfAbsent(viewer.getUniqueId(),x->Bukkit.getScoreboardManager().getNewScoreboard());
        Objective o=b.getObjective("emerald");
        if(o==null){o=b.registerNewObjective("emerald","dummy","§a§l💚 EMERALD SMP");o.setDisplaySlot(DisplaySlot.SIDEBAR);}
        Set<String> old=oldEntries.computeIfAbsent(viewer.getUniqueId(),x->new HashSet<>());
        for(String s:old)b.resetScores(s); old.clear();
        long money=plugin.getEconomyManager().getBalance(viewer.getUniqueId());
        long shards=plugin.getPlayerDataManager().getEmeraldShards(viewer.getUniqueId());
        line(o,old,"§8━━━━━━━━━━━━",6);
        line(o,old,"§7✦ RANK: "+role.color()+"§l"+role.label(),5);
        line(o,old,"§6💰 Money: §f"+plugin.getEconomyManager().format(money),4);
        line(o,old,"§a💚 Shards: §f"+String.format(Locale.US,"%,d",shards),3);
        line(o,old,"§b📶 MS: §f"+viewer.getPing(),2);
        line(o,old,"§7s1.seranodes.com:25638",1);
        viewer.setScoreboard(b);
    }

    private void updateNametags(Player viewer){
        Scoreboard b=viewer.getScoreboard();
        for(Team t:new ArrayList<>(b.getTeams())) if(t.getName().startsWith("emr_")) t.unregister();
        for(Player target:Bukkit.getOnlinePlayers()){
            RoleManager.Role r=plugin.getRoleManager().get(target);
            Team t=b.registerNewTeam("emr_"+target.getUniqueId().toString().replace("-","").substring(0,12));
            t.setPrefix(r.color()+"§l"+r.icon()+" "+r.label()+" §f");
            t.setSuffix("");
            t.addEntry(target.getName());
        }
    }

    private String formatMoney(long amount){
        double value=amount; String suffix="";
        long abs=Math.abs(amount);
        if(abs>=1_000_000_000_000L){value=amount/1_000_000_000_000.0;suffix="T";}
        else if(abs>=1_000_000_000L){value=amount/1_000_000_000.0;suffix="B";}
        else if(abs>=1_000_000L){value=amount/1_000_000.0;suffix="M";}
        else if(abs>=1_000L){value=amount/1_000.0;suffix="K";}
        if(suffix.isEmpty())return "$"+String.format(Locale.US,"%,d",amount);
        String n=Math.abs(value-Math.rint(value))<0.0001?String.format(Locale.US,"%.0f",value):String.format(Locale.US,"%.1f",value);
        return "$"+n+suffix;
    }
    private void line(Objective o,Set<String> set,String s,int score){String x=s;while(set.contains(x))x+="§r";o.getScore(x).setScore(score);set.add(x);}
}
