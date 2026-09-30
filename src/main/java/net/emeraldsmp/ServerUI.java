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
            p.setPlayerListName(p.getName());
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
        int online = Bukkit.getOnlinePlayers().size();
        p.setPlayerListHeaderFooter(
            "\n" + ChatColor.GREEN.toString() + ChatColor.BOLD + "💚 EMERALD SMP" + ChatColor.RESET
                + ChatColor.DARK_GREEN + "  •  " + ChatColor.GRAY + "Survival Economy",
            ChatColor.DARK_GREEN + "play.emeraldsmp.net" + ChatColor.DARK_GRAY + "  •  "
                + ChatColor.GRAY + "Online: " + ChatColor.WHITE + online + ChatColor.DARK_GRAY + "  •  "
                + ChatColor.GRAY + "Your Ping: " + ChatColor.WHITE + p.getPing() + "ms" + "\n"
        );
        p.setPlayerListName(ChatColor.GREEN + "💚 " + ChatColor.WHITE + p.getName()
            + ChatColor.DARK_GRAY + "  •  " + ChatColor.GRAY + p.getPing() + "ms");
    }

    private void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    private void update(Player p) {
        setupTab(p);
        Scoreboard board = boards.computeIfAbsent(p.getUniqueId(), k -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective objective = board.getObjective("emerald");
        if (objective == null) {
            objective = board.registerNewObjective("emerald", "dummy",
                ChatColor.GREEN.toString() + ChatColor.BOLD + "💚 EMERALD SMP");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        Set<String> previous = oldEntries.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        for (String entry : previous) board.resetScores(entry);
        previous.clear();

        int online = Bukkit.getOnlinePlayers().size();
        long balance = plugin.getEconomyManager().getBalance(p.getUniqueId());
        String money = plugin.getEconomyManager().format(balance);
        int kills = p.getStatistic(org.bukkit.Statistic.PLAYER_KILLS);

        addLine(objective, previous, ChatColor.DARK_GREEN + "────────────", 9);
        addLine(objective, previous, ChatColor.GREEN + "💰 Money: " + ChatColor.WHITE + money, 8);
        addLine(objective, previous, ChatColor.GREEN + "👥 Online: " + ChatColor.WHITE + online, 7);
        addLine(objective, previous, ChatColor.GREEN + "⚔ Kills: " + ChatColor.WHITE + kills, 6);
        addLine(objective, previous, ChatColor.DARK_GRAY + " ", 5);
        addLine(objective, previous, ChatColor.GREEN.toString() + ChatColor.BOLD + "SERVER", 4);
        addLine(objective, previous, ChatColor.GRAY + "play.emeraldsmp.net", 3);
        addLine(objective, previous, ChatColor.DARK_GREEN + "────────────", 2);
        addLine(objective, previous, ChatColor.GREEN + "💚 Emerald SMP", 1);

        p.setScoreboard(board);
    }

    private void addLine(Objective objective, Set<String> entries, String text, int score) {
        String unique = text;
        while (entries.contains(unique)) unique += ChatColor.RESET;
        objective.getScore(unique).setScore(score);
        entries.add(unique);
    }
}
