package net.emeraldsmp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import java.util.*;

public final class ServerUI implements Listener {
    private final EmeraldSMP plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Set<String>> oldEntries = new HashMap<>();
    private BukkitTask task;

    public ServerUI(EmeraldSMP plugin) { this.plugin = plugin; }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
        for (Player p : Bukkit.getOnlinePlayers()) setupTab(p);
        updateAll();
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            p.setPlayerListHeaderFooter("", "");
        }
        boards.clear();
        oldEntries.clear();
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        setupTab(e.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin, () -> update(e.getPlayer()), 2L);
    }

    @EventHandler public void quit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
        oldEntries.remove(e.getPlayer().getUniqueId());
    }

    private void setupTab(Player p) {
        p.setPlayerListHeaderFooter(
            ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "💚 EMERALD SMP",
            ChatColor.GRAY + "Player: " + ChatColor.WHITE + p.getName()
                + ChatColor.DARK_GRAY + "  •  " + ChatColor.GRAY + "Online: "
                + ChatColor.WHITE + Bukkit.getOnlinePlayers().size()
                + ChatColor.DARK_GRAY + "  •  " + ChatColor.GRAY + "Ping: "
                + ChatColor.WHITE + p.getPing() + "ms"
        );
        p.setPlayerListName(ChatColor.GREEN + p.getName() + ChatColor.DARK_GRAY + " • " + ChatColor.GRAY + p.getPing() + "ms");
    }

    private void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    private void update(Player p) {
        setupTab(p);
        Scoreboard board = boards.computeIfAbsent(p.getUniqueId(), k -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective old = board.getObjective("emerald");
        if (old == null) {
            old = board.registerNewObjective("emerald", "dummy", ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "💚 EMERALD SMP");
            old.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        Set<String> previous = oldEntries.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        for (String entry : previous) board.resetScores(entry);
        previous.clear();

        addLine(old, previous, ChatColor.GRAY + "Server Information", 6);
        addLine(old, previous, ChatColor.GREEN + "💰 Money: " + ChatColor.WHITE + plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(p.getUniqueId())), 5);
        addLine(old, previous, ChatColor.GREEN + "👥 Players: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size(), 4);
        addLine(old, previous, ChatColor.GREEN + "⚔ Kills: " + ChatColor.WHITE + p.getStatistic(org.bukkit.Statistic.PLAYER_KILLS), 3);
        addLine(old, previous, ChatColor.DARK_GRAY + " ", 2);
        addLine(old, previous, ChatColor.GRAY + "emeraldsmp", 1);
        p.setScoreboard(board);
    }

    private void addLine(Objective objective, Set<String> entries, String text, int score) {
        String unique = text;
        while (entries.contains(unique)) unique += ChatColor.RESET;
        objective.getScore(unique).setScore(score);
        entries.add(unique);
    }
}
