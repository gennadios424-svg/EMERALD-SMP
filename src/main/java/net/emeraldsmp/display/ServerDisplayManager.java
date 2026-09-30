package net.emeraldsmp.display;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public final class ServerDisplayManager {
    private final EmeraldSMP plugin;
    private final Scoreboard board;

    public ServerDisplayManager(EmeraldSMP plugin) {
        this.plugin = plugin;
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
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
        player.setPlayerListHeaderFooter(
                color("&a&l💚 Emerald SMP"),
                color("&7Online: &f" + Bukkit.getOnlinePlayers().size()
                        + " &8• &7Ping: &f" + safePing(player) + "ms"));
    }

    private void updateScoreboard(Player player) {
        Objective objective = board.getObjective("emerald");
        if (objective == null) {
            objective = board.registerNewObjective("emerald", Criteria.DUMMY, color("&a&l💚 EMERALD SMP"));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        for (String entry : board.getEntries()) board.resetScores(entry);

        long balance = plugin.getEconomyManager().getBalance(player.getUniqueId());
        String money = plugin.getEconomyManager().format(balance);
        set(objective, color("&aMoney: &f" + money), 4);
        set(objective, color("&aPlayers: &f" + Bukkit.getOnlinePlayers().size()), 3);
        set(objective, color("&aPing: &f" + safePing(player) + "ms"), 2);
        set(objective, color("&8play.emeraldsmp..."), 1);

        if (player.getScoreboard() != board) player.setScoreboard(board);
    }

    private int safePing(Player player) {
        try { return Math.max(0, player.getPing()); }
        catch (Throwable ignored) { return 0; }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
