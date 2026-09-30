package net.emeraldsmp.display;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public final class PlayerDisplayManager {
    private final EmeraldSMP plugin;
    private int taskId=-1;
    public PlayerDisplayManager(EmeraldSMP plugin){this.plugin=plugin;}
    public void start(){
        Bukkit.getScheduler().runTaskTimer(plugin, this::update, 0L, 20L);
    }
    private void update(){
        for(Player p:Bukkit.getOnlinePlayers()) updatePlayer(p);
    }
    private void updatePlayer(Player p){
        Scoreboard board=Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj=board.registerNewObjective("emerald","dummy","§a§l💚 EMERALD SMP");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        int kills=p.getStatistic(org.bukkit.Statistic.PLAYER_KILLS);
        String[] lines={
            "§r",
            "§6💰 Money: §f$"+String.format("%,d",plugin.getEconomyManager().getBalance(p.getUniqueId())),
            "§a👥 Players: §f"+Bukkit.getOnlinePlayers().size(),
            "§c⚔ Kills: §f"+kills,
            "§r§7play.emeraldsmp.net"
        };
        int score=lines.length;
        for(String line:lines)obj.getScore(line).setScore(score--);
        p.setScoreboard(board);

        p.setPlayerListHeaderFooter(
            "§a§l💚 EMERALD SMP\n§7━━━━━━━━━━━━━━━━━━",
            "§7Players: §f"+Bukkit.getOnlinePlayers().size()+" §8• §7play.emeraldsmp.net"
        );
    }
    public void remove(Player p){p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());}
}
