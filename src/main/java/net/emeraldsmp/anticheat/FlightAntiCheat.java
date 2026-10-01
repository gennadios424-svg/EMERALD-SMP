package net.emeraldsmp.anticheat;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FlightAntiCheat implements Listener {
    private final EmeraldSMP plugin;
    private final SusListManager susList;
    private final Map<UUID, FlightState> states = new HashMap<>();

    public FlightAntiCheat(EmeraldSMP plugin, SusListManager susList) {
        this.plugin = plugin;
        this.susList = susList;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();

        if (isExempt(p)) {
            states.remove(p.getUniqueId());
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() != to.getWorld()) {
            states.remove(p.getUniqueId());
            return;
        }

        double dy = to.getY() - from.getY();
        double horizontal = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());

        boolean inAir = !p.isOnGround() && !p.isInsideVehicle() && !p.isGliding()
                && !p.isSwimming() && !p.isFlying();
        boolean suspiciousVertical = Math.abs(dy) < 0.08;
        boolean movingOrHovering = horizontal > 0.08 || Math.abs(dy) > 0.01;

        FlightState state = states.computeIfAbsent(p.getUniqueId(), k -> new FlightState());

        if (inAir && suspiciousVertical && movingOrHovering) {
            if (state.firstFlagAt == 0L) state.firstFlagAt = System.currentTimeMillis();
            state.flags++;

            // Require sustained evidence. Normal jumps, knockback and lag should not reach this.
            if (state.flags >= 24 && System.currentTimeMillis() - state.firstFlagAt >= 2500L) {
                punish(p, state.flags);
            }
        } else {
            // Decay rather than instantly forgetting flags, preventing one-frame false positives.
            state.flags = Math.max(0, state.flags - 2);
            if (state.flags == 0) state.firstFlagAt = 0L;
        }
    }

    private boolean isExempt(Player p) {
        GameMode mode = p.getGameMode();
        return mode == GameMode.CREATIVE
                || mode == GameMode.SPECTATOR
                || p.getAllowFlight()
                || p.hasPermission("emerald.anticheat.bypass")
                || p.isGliding()
                || p.isFlying()
                || p.isInsideVehicle()
                || p.isSwimming()
                || p.isInWater()
                || p.isClimbing()
                || p.getFallDistance() > 0.0F;
    }

    private void punish(Player p, int flags) {
        UUID uuid = p.getUniqueId();
        if (susList.has(uuid)) return;

        Location location = p.getLocation().clone();
        susList.add(p, location, "Unauthorized Flight", flags);

        String reason = "Emerald SMP AntiCheat: Unauthorized Flight";
        Bukkit.getLogger().info("[Emerald AntiCheat] " + p.getName() + " flagged for unauthorized flight (" + flags + " flags).");
        p.setAllowFlight(false);
        p.setFlying(false);
        p.kickPlayer("§c§lEMERALD SMP\n§fUnauthorized flight detected.\n§7You have been kicked for review.\n\n§8Staff can review your case in §f/suslist§8.");
        states.remove(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    private static final class FlightState {
        int flags;
        long firstFlagAt;
    }
}
