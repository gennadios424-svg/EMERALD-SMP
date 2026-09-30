package net.emeraldsmp.afk;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AfkManager {
    private final EmeraldSMP plugin;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> afk = new ConcurrentHashMap<>();
    private BukkitTask task;

    public AfkManager(EmeraldSMP plugin) { this.plugin = plugin; }

    public void start() {
        if (!plugin.getConfig().getBoolean("afk.enabled", true)) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkAutoAfk, 20L, 20L);
        for (Player p : plugin.getServer().getOnlinePlayers()) touch(p);
    }

    public void stop() {
        if (task != null) task.cancel();
        lastActivity.clear();
        afk.clear();
    }

    public void join(Player p) {
        lastActivity.put(p.getUniqueId(), System.currentTimeMillis());
        afk.put(p.getUniqueId(), false);
        updateTab(p);
    }

    public void quit(Player p) {
        lastActivity.remove(p.getUniqueId());
        afk.remove(p.getUniqueId());
    }

    public boolean isAfk(UUID id) { return afk.getOrDefault(id, false); }

    public void toggle(Player p) {
        if (isAfk(p.getUniqueId())) {
            setAfk(p, false, true);
        } else {
            lastActivity.put(p.getUniqueId(), System.currentTimeMillis());
            setAfk(p, true, true);
        }
    }

    public void touch(Player p) {
        UUID id = p.getUniqueId();
        if (!lastActivity.containsKey(id)) lastActivity.put(id, System.currentTimeMillis());
        lastActivity.put(id, System.currentTimeMillis());
        if (isAfk(id)) setAfk(p, false, true);
    }

    private void checkAutoAfk() {
        if (!plugin.getConfig().getBoolean("afk.enabled", true)) return;
        long timeout = Math.max(1L, plugin.getConfig().getLong("afk.auto-afk-minutes", 10L)) * 60_000L;
        long now = System.currentTimeMillis();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            long last = lastActivity.getOrDefault(id, now);
            if (!isAfk(id) && now - last >= timeout) setAfk(p, true, true);
            updateTab(p);
        }
    }

    public void updateTab(Player p) {
        if (!plugin.getConfig().getBoolean("afk.show-in-tab", true)) return;
        String prefix = isAfk(p.getUniqueId()) ? "§7[AFK] " : "§a💚 ";
        String suffix = isAfk(p.getUniqueId()) ? " §8• §7AFK" : " §8• §7" + p.getPing() + "ms";
        p.setPlayerListName(prefix + "§f" + p.getName() + suffix);
    }

    private void setAfk(Player p, boolean value, boolean message) {
        UUID id = p.getUniqueId();
        if (afk.getOrDefault(id, false) == value) return;
        afk.put(id, value);
        if (!value) lastActivity.put(id, System.currentTimeMillis());
        updateTab(p);
        if (message) {
            if (value) plugin.getMessageService().sendRaw(p, "§e💤 §fYou are now AFK.");
            else plugin.getMessageService().sendRaw(p, "§a👋 §fYou are no longer AFK.");
        }
    }
}
