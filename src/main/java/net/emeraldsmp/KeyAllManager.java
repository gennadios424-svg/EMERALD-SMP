package net.emeraldsmp;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Locale;
import java.util.Random;

public final class KeyAllManager implements Listener {
    private static final long INTERVAL_MS = 30L * 60L * 1000L;
    private final EmeraldSMP plugin;
    private final File file;
    private final Random random = new Random();
    private BossBar bossBar;
    private BukkitTask task;
    private long nextKeyAllAt;

    public KeyAllManager(EmeraldSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "keyall.yml");
    }

    public void load() {
        YamlConfiguration y = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        nextKeyAllAt = y.getLong("next-keyall-at", 0L);
        if (nextKeyAllAt <= 0L) nextKeyAllAt = System.currentTimeMillis() + INTERVAL_MS;
        save();
    }

    public void start() {
        bossBar = Bukkit.createBossBar("🔑 COMMON KEYALL", BarColor.GREEN, BarStyle.SOLID);
        bossBar.setVisible(true);
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        tick();
    }

    public void stop() {
        if (task != null) task.cancel();
        if (bossBar != null) bossBar.removeAll();
        save();
    }

    @EventHandler public void join(PlayerJoinEvent e) {
        if (bossBar != null) bossBar.addPlayer(e.getPlayer());
    }

    @EventHandler public void quit(PlayerQuitEvent e) {
        if (bossBar != null) bossBar.removePlayer(e.getPlayer());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (now >= nextKeyAllAt) {
            if (plugin.getCrateManager().isKeyAllRunning()) {
                nextKeyAllAt = now + 5000L;
                save();
            } else {
                int amount = 1 + random.nextInt(3);
                nextKeyAllAt = now + INTERVAL_MS;
                save();
                Bukkit.broadcastMessage("§a§l💚 COMMON KEYALL! §fEveryone online received §e" + amount
                        + " Common Key" + (amount == 1 ? "" : "s") + "§f!");
                plugin.getCrateManager().keyAll("common", amount, Bukkit.getConsoleSender());
            }
        }
        long remaining = Math.max(0L, nextKeyAllAt - now);
        long seconds = remaining / 1000L;
        if (bossBar != null) {
            bossBar.setProgress(Math.max(0D, Math.min(1D, remaining / (double) INTERVAL_MS)));
            bossBar.setTitle("§a🔑 COMMON KEYALL §8IN §f" + format(seconds));
        }
    }

    private String format(long seconds) {
        return String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-keyall-at", nextKeyAllAt);
        try { y.save(file); }
        catch (Exception ex) { plugin.getLogger().warning("Could not save keyall.yml: " + ex.getMessage()); }
    }
}
