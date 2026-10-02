package net.emeraldsmp;

import net.emeraldsmp.roles.RoleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scoreboard.*;

import java.util.*;

public final class ServerUI implements Listener {
    private final EmeraldSMP plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Set<String>> oldEntries = new HashMap<>();
    private int taskId = -1;

    public ServerUI(EmeraldSMP plugin) { this.plugin = plugin; }

    public void start() {
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 10L).getTaskId();
        updateAll();
    }

    public void stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            p.playerListName(Component.text(p.getName()));
            p.setCustomName(null);
            p.setCustomNameVisible(false);
        }
        boards.clear();
        oldEntries.clear();
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> update(e.getPlayer()), 2L);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
        oldEntries.remove(e.getPlayer().getUniqueId());
    }

    public void refresh(Player p) {
        if (p != null && p.isOnline()) update(p);
    }

    public void refreshAll() { updateAll(); }

    private void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    private void update(Player viewer) {
        viewer.sendPlayerListHeaderAndFooter(
                Component.text("\n💚 EMERALD SMP", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("🌐 s1.seranodes.com:25638", NamedTextColor.GRAY)
        );
        updatePlayerList();
        updateScoreboard(viewer, plugin.getRoleManager().get(viewer));
    }

    private void updatePlayerList() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());

        // Explicit Paper player-list ordering: role first, then alphabetical username.
        players.sort(
                Comparator.comparingInt((Player p) -> -plugin.getRoleManager().get(p).weight())
                        .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER)
        );

        Map<UUID, Integer> order = new HashMap<>();
        int roleGroup = -1;
        int previousWeight = Integer.MIN_VALUE;
        int withinRole = 0;

        for (Player target : players) {
            int weight = plugin.getRoleManager().get(target).weight();
            if (weight != previousWeight) {
                roleGroup++;
                withinRole = 0;
                previousWeight = weight;
            }
            // Positive and comfortably separated groups; preserves alphabetical order within a role.
            order.put(target.getUniqueId(), roleGroup * 10000 + withinRole + 1);
            withinRole++;
        }

        for (Player target : players) {
            RoleManager.Role role = plugin.getRoleManager().get(target);
            String team = plugin.getTeamManager() == null ? "" : plugin.getTeamManager().tag(target.getUniqueId());
            String teamText = team.isEmpty() ? "[NONE]" : "[" + team + "]";

            Component listName = Component.empty();

            if (plugin.getLiveManager() != null && plugin.getLiveManager().isLive(target)) {
                listName = listName.append(Component.text("🔴 LIVE ", NamedTextColor.RED, TextDecoration.BOLD));
            }

            listName = listName
                    .append(Component.text(role.icon() + " ", roleColor(role), TextDecoration.BOLD))
                    .append(Component.text(role.label() + "  ", roleColor(role), TextDecoration.BOLD))
                    .append(Component.text(teamText + " ", NamedTextColor.GRAY))
                    .append(Component.text(target.getName(), NamedTextColor.WHITE));

            target.playerListName(listName);
            target.setPlayerListOrder(order.getOrDefault(target.getUniqueId(), 1));

            // Clean overhead display: money only. The balance comes from the existing economy.
            String money = plugin.getEconomyManager().formatCompact(
                    plugin.getEconomyManager().getBalance(target.getUniqueId())
            );
            target.setCustomName("§a§l$" + money);
            target.setCustomNameVisible(true);
        }
    }

    private TextColor roleColor(RoleManager.Role role) {
        return TextColor.fromHexString(role.colors()[1]);
    }

    private void updateScoreboard(Player viewer, RoleManager.Role role) {
        Scoreboard b = boards.computeIfAbsent(
                viewer.getUniqueId(),
                x -> Bukkit.getScoreboardManager().getNewScoreboard()
        );

        Objective o = b.getObjective("emerald");
        if (o == null) {
            o = b.registerNewObjective("emerald", "dummy", "§a§l💚 EMERALD SMP");
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        Set<String> old = oldEntries.computeIfAbsent(viewer.getUniqueId(), x -> new HashSet<>());
        for (String s : old) b.resetScores(s);
        old.clear();

        long money = plugin.getEconomyManager().getBalance(viewer.getUniqueId());
        long shards = plugin.getPlayerDataManager().getEmeraldShards(viewer.getUniqueId());

        line(o, old, "§8━━━━━━━━━━━━", 6);
        line(o, old, "§7✦ RANK: " + role.color() + "§l" + role.label(), 5);
        line(o, old, "§6💰 Money: §f" + plugin.getEconomyManager().format(money), 4);
        line(o, old, "§a💚 Shards: §f" + String.format(Locale.US, "%,d", shards), 3);
        line(o, old, "§b📶 MS: §f" + viewer.getPing(), 2);
        line(o, old, "§7s1.seranodes.com:25638", 1);
        viewer.setScoreboard(b);
    }

    private void line(Objective o, Set<String> set, String s, int score) {
        String x = s;
        while (set.contains(x)) x += "§r";
        o.getScore(x).setScore(score);
        set.add(x);
    }
}
