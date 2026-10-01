package net.emeraldsmp.combat;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import java.lang.reflect.Method;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class CombatManager implements Listener {
    private static final long COMBAT_MILLIS = 15_000L;
    private final EmeraldSMP plugin;
    private final Map<UUID, Long> taggedUntil = new HashMap<>();
    private int cleanupTask = -1;

    public CombatManager(EmeraldSMP plugin) {
        this.plugin = plugin;
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpired, 20L, 20L).getTaskId();
    }

    public boolean isInCombat(Player player) {
        return player != null && isInCombat(player.getUniqueId());
    }

    public boolean isInCombat(UUID uuid) {
        Long until = taggedUntil.get(uuid);
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            taggedUntil.remove(uuid);
            return false;
        }
        return true;
    }

    public void tag(Player player) {
        if (player == null) return;
        boolean wasTagged = isInCombat(player.getUniqueId());
        taggedUntil.put(player.getUniqueId(), System.currentTimeMillis() + COMBAT_MILLIS);
        if (!wasTagged) {
            player.sendMessage("§c⚔ You are now in combat!");
            plugin.getTpaManager().cancelRequestsFor(player);
        }
    }

    public void tagBoth(Player a, Player b) {
        if (a == null || b == null || a.getUniqueId().equals(b.getUniqueId())) return;
        tag(a);
        tag(b);
    }

    public void remove(Player player) {
        if (player != null) remove(player.getUniqueId());
    }

    public void remove(UUID uuid) {
        if (taggedUntil.remove(uuid) != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage("§a⚔ You are no longer in combat.");
            }
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = taggedUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (entry.getValue() <= now) {
                UUID uuid = entry.getKey();
                it.remove();
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.sendMessage("§a⚔ You are no longer in combat.");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvPDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        if (!attacker.isOnline() || !victim.isOnline()) return;
        tagBoth(attacker, victim);
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;

        Player fromDamager = reflectPlayerSource(damager);
        if (fromDamager != null) return fromDamager;

        try {
            Method getDamageSource = event.getClass().getMethod("getDamageSource");
            Object source = getDamageSource.invoke(event);
            if (source != null) {
                Method getCausingEntity = source.getClass().getMethod("getCausingEntity");
                Object causing = getCausingEntity.invoke(source);
                if (causing instanceof Player player) return player;
            }
        } catch (ReflectiveOperationException ignored) {
            // The current Paper damage source does not expose an identifiable player.
        }
        return null;
    }

    private Player reflectPlayerSource(Entity entity) {
        if (entity == null) return null;
        try {
            Method sourceMethod = entity.getClass().getMethod("getSource");
            Object source = sourceMethod.invoke(entity);
            if (source instanceof Player player) return player;
        } catch (ReflectiveOperationException ignored) {
            // This entity has no player source that can be identified reliably.
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isInCombat(player) || isStaff(player)) return;

        String raw = event.getMessage();
        if (raw == null || raw.length() <= 1) return;
        String command = raw.substring(1).trim();
        if (command.isEmpty()) return;
        int space = command.indexOf(' ');
        String label = (space >= 0 ? command.substring(0, space) : command).toLowerCase();
        if (label.equals("shop") || label.endsWith(":shop")) return;

        event.setCancelled(true);
        player.sendMessage("§c⚔ You cannot use that command while in combat!");
    }

    private boolean isStaff(Player player) {
        return player.isOp() || player.hasPermission("emerald.admin") || plugin.getRoleManager().isStaff(player);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        remove(event.getEntity());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // UUID state intentionally remains in memory through a disconnect.
        // If the player rejoins before the 15-second expiry, the combat tag still applies.
        if (isInCombat(event.getPlayer())) {
            event.getPlayer().sendMessage("§c⚔ You are still in combat!");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Do not remove the UUID here: logging out must not clear the combat timer.
    }

    public void disable() {
        if (cleanupTask != -1) Bukkit.getScheduler().cancelTask(cleanupTask);
        cleanupTask = -1;
        taggedUntil.clear();
    }
}
