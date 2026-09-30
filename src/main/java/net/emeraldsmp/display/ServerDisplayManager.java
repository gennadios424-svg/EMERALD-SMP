package net.emeraldsmp.display;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public final class ServerDisplayManager {
    private final EmeraldSMP plugin;

    public ServerDisplayManager(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
        updateAll();
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) updatePlayer(player);
    }

    public void updatePlayer(Player player) {
        updateTab(player);
        updateScoreboard(player);
    }

    private void updateTab(Player player) {
        String header = color("&a&l💚 Emerald SMP\n&7Player: &f" + player.getName());
        String footer = color("&7Online: &f" + Bukkit.getOnlinePlayers().size()
                + " &8• &7Ping: &f" + safePing(player) + "ms");
        player.setPlayerListHeaderFooter(header, footer);
    }

    private void updateScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("emerald", Criteria.DUMMY, color("&a&l💚 EMERALD SMP"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        long balance = plugin.getEconomyManager().getBalance(player.getUniqueId());
        String money = plugin.getEconomyManager().format(balance);

        objective.getScore(color("&aMoney: &f" + money)).setScore(4);
        objective.getScore(color("&aPlayers: &f" + Bukkit.getOnlinePlayers().size())).setScore(3);
        objective.getScore(color("&aPing: &f" + safePing(player) + "ms")).setScore(2);
        objective.getScore(color("&8play.emeraldsmp...")).setScore(1);

        player.setScoreboard(board);
    }

    private int safePing(Player player) {
        try { return Math.max(0, player.getPing()); }
        catch (Throwable ignored) { return 0; }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
