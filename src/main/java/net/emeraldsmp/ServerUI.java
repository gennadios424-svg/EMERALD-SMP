package net.emeraldsmp;

import net.emeraldsmp.roles.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scoreboard.*;

import java.util.*;

public final class ServerUI implements Listener {
    private final EmeraldSMP plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Set<String>> oldEntries = new HashMap<>();

    public ServerUI(EmeraldSMP plugin) { this.plugin = plugin; }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 10L);
        for (Player player : Bukkit.getOnlinePlayers()) update(player);
    }

    public void stop() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            player.setPlayerListHeaderFooter("", "");
            player.setPlayerListName(player.getName());
            player.setCustomName(null);
            player.setCustomNameVisible(false);
        }
        boards.clear();
        oldEntries.clear();
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> update(event.getPlayer()), 2L);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
        oldEntries.remove(event.getPlayer().getUniqueId());
    }

    public void refresh(Player player) {
        if (player != null && player.isOnline()) update(player);
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) update(player);
    }

    private void update(Player viewer) {
        RoleManager.Role viewerRole = plugin.getRoleManager().get(viewer);
        viewer.setPlayerListHeaderFooter(
                "
§a§l💚 EMERALD SMP §8• §7Network",
                "§7s1.seranodes.com:25638 §8• §7MS: §b" + viewer.getPing() + "
");

        updatePlayerList();
        updateScoreboard(viewer, viewerRole);
        updateNametags(viewer);
    }

    private void updatePlayerList() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator
                .comparingInt((Player p) -> -plugin.getRoleManager().get(p).weight())
                .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        for (Player target : players) {
            RoleManager.Role role = plugin.getRoleManager().get(target);
            String team = plugin.getTeamManager() == null ? "" : plugin.getTeamManager().tag(target.getUniqueId());
            String teamText = team.isEmpty() ? "§8[§7None§8]" : "§a[§l" + team + "§r§a]";
            String badge = role.icon() + " " + role.label();

            // The scoreboard team name is the deterministic Tab sorting key.
            // It contains no visible "1/2/3/4" entries.
            Scoreboard sortBoard = target.getScoreboard();
            if (sortBoard == null) sortBoard = Bukkit.getScoreboardManager().getNewScoreboard();
            String sortName = String.format(Locale.US, "tab_%02d", 99 - role.weight());
            Team sortTeam = sortBoard.getTeam(sortName);
            if (sortTeam == null) sortTeam = sortBoard.registerNewTeam(sortName);
            sortTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            sortTeam.addEntry(target.getName());

            target.setPlayerListName(badge + " §f" + target.getName() + " §7" + teamText);
            target.setCustomName("§a§l" + formatMoney(plugin.getEconomyManager().getBalance(target.getUniqueId())));
            target.setCustomNameVisible(true);
        }
    }

    private void updateScoreboard(Player viewer, RoleManager.Role role) {
        Scoreboard board = boards.computeIfAbsent(viewer.getUniqueId(), key -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective objective = board.getObjective("emerald");
        if (objective == null) {
            objective = board.registerNewObjective("emerald", "dummy", "§a§l💚 EMERALD SMP");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        Set<String> old = oldEntries.computeIfAbsent(viewer.getUniqueId(), key -> new HashSet<>());
        for (String entry : old) board.resetScores(entry);
        old.clear();

        long money = plugin.getEconomyManager().getBalance(viewer.getUniqueId());
        long shards = plugin.getPlayerDataManager().getEmeraldShards(viewer.getUniqueId());
        line(objective, old, "§8━━━━━━━━━━━━", 6);
        line(objective, old, "§7✦ RANK: " + role.color() + "§l" + role.label(), 5);
        line(objective, old, "§6💰 Money: §f" + plugin.getEconomyManager().format(money), 4);
        line(objective, old, "§a💚 Shards: §f" + String.format(Locale.US, "%,d", shards), 3);
        line(objective, old, "§b📶 MS: §f" + viewer.getPing(), 2);
        line(objective, old, "§7s1.seranodes.com:25638", 1);
        viewer.setScoreboard(board);
    }

    private void updateNametags(Player viewer) {
        Scoreboard board = viewer.getScoreboard();
        // These teams control the visible nameplate prefix/suffix without affecting Tab content.
        for (Team team : new ArrayList<>(board.getTeams())) {
            if (team.getName().startsWith("emr_")) team.unregister();
        }

        for (Player target : Bukkit.getOnlinePlayers()) {
            RoleManager.Role role = plugin.getRoleManager().get(target);
            Team team = board.registerNewTeam("emr_" + target.getUniqueId().toString().replace("-", "").substring(0, 12));
            team.setPrefix(role.color() + "§l" + role.icon() + " " + role.label() + " §f");
            team.setSuffix("");
            team.addEntry(target.getName());
        }
    }

    private String formatMoney(long amount) {
        double value = amount;
        String suffix = "";
        if (Math.abs(amount) >= 1_000_000_000_000L) { value = amount / 1_000_000_000_000.0; suffix = "T"; }
        else if (Math.abs(amount) >= 1_000_000_000L) { value = amount / 1_000_000_000.0; suffix = "B"; }
        else if (Math.abs(amount) >= 1_000_000L) { value = amount / 1_000_000.0; suffix = "M"; }
        else if (Math.abs(amount) >= 1_000L) { value = amount / 1_000.0; suffix = "K"; }
        if (suffix.isEmpty()) return "$" + String.format(Locale.US, "%,d", amount);
        String number = Math.abs(value - Math.rint(value)) < 0.0001 ? String.format(Locale.US, "%.0f", value) : String.format(Locale.US, "%.1f", value);
        return "$" + number + suffix;
    }

    private void line(Objective objective, Set<String> set, String text, int score) {
        String value = text;
        while (set.contains(value)) value += "§r";
        objective.getScore(value).setScore(score);
        set.add(value);
    }
}
