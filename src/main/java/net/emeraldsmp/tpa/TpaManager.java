package net.emeraldsmp.tpa;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TpaManager implements Listener {
    private static final long REQUEST_TICKS = 20L * 60L;
    private static final long COUNTDOWN_TICKS = 20L * 5L;

    private final EmeraldSMP plugin;
    private final Map<UUID, Request> outgoing = new HashMap<>();
    private final Map<UUID, Request> incoming = new HashMap<>();
    private final Map<UUID, Countdown> countdowns = new HashMap<>();

    public TpaManager(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    public void request(Player sender, Player target, boolean here) {
        if (!isStaff(sender) && (plugin.getCombatManager().isInCombat(sender) || plugin.getCombatManager().isInCombat(target))) {
            sender.sendMessage("§c⚔ You cannot use that command while in combat!");
            return;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage("§cYou cannot send a teleport request to yourself.");
            return;
        }

        Request existingOutgoing = outgoing.get(sender.getUniqueId());
        if (existingOutgoing != null) {
            if (existingOutgoing.targetId.equals(target.getUniqueId())) {
                sender.sendMessage("§cYou already have a pending request to this player.");
            } else {
                sender.sendMessage("§cYou already have a pending teleport request.");
            }
            return;
        }

        if (incoming.containsKey(target.getUniqueId())) {
            sender.sendMessage("§cThat player already has a pending teleport request.");
            return;
        }

        Request request = new Request(
                sender.getUniqueId(),
                target.getUniqueId(),
                sender.getName(),
                target.getName(),
                here
        );
        outgoing.put(sender.getUniqueId(), request);
        incoming.put(target.getUniqueId(), request);

        target.sendMessage("§8━━━━━━━━━━━━━━━━━━━━");
        target.sendMessage(here ? "§b📍 §lTELEPORT-HERE REQUEST" : "§b📍 §lTELEPORT REQUEST");
        target.sendMessage("");
        target.sendMessage(here
                ? "§f" + sender.getName() + " §7wants you to teleport to them."
                : "§f" + sender.getName() + " §7wants to teleport to you.");
        target.sendMessage("");
        target.sendMessage("§a§l[ ACCEPT ] §7/ §c§l[ DENY ]");
        target.sendMessage("§7Use §f/tpaccept §7or §f/tpdeny§7.");
        target.sendMessage("§7Expires in 60 seconds.");
        target.sendMessage("§8━━━━━━━━━━━━━━━━━━━━");

        sender.sendMessage("§aTeleport request sent to §f" + target.getName() + "§a.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> expire(request), REQUEST_TICKS);
    }

    public void accept(Player target) {
        if (!isStaff(target) && plugin.getCombatManager().isInCombat(target)) {
            target.sendMessage("§c⚔ You cannot use that command while in combat!");
            return;
        }
        Request request = incoming.get(target.getUniqueId());
        if (request == null) {
            target.sendMessage("§cYou have no pending teleport request.");
            return;
        }

        if (!isValid(request)) {
            remove(request);
            target.sendMessage("§cThat teleport request is no longer valid.");
            return;
        }

        remove(request);

        Player sender = Bukkit.getPlayer(request.senderId);
        if (sender == null || !sender.isOnline()) {
            target.sendMessage("§cThe requester is no longer online.");
            return;
        }

        Player teleporting = request.here ? target : sender;
        Player destination = request.here ? sender : target;
        startCountdown(teleporting, destination, request);
    }

    public void deny(Player target) {
        if (plugin.getCombatManager().isInCombat(target)) {
            target.sendMessage("§c⚔ You cannot use that command while in combat!");
            return;
        }
        Request request = incoming.get(target.getUniqueId());
        if (request == null) {
            target.sendMessage("§cYou have no pending teleport request.");
            return;
        }

        remove(request);

        Player sender = Bukkit.getPlayer(request.senderId);
        if (sender != null && sender.isOnline()) {
            sender.sendMessage("§c❌ Your teleport request was denied.");
        }
        target.sendMessage("§cTeleport request denied.");
    }

    private boolean isStaff(Player player) {
        return player.isOp() || player.hasPermission("emerald.admin") || plugin.getRoleManager().isStaff(player);
    }

    private void startCountdown(Player teleporting, Player destination, Request request) {
        UUID id = teleporting.getUniqueId();
        Countdown old = countdowns.remove(id);
        if (old != null) {
            teleporting.sendMessage("§cYour previous teleport countdown was cancelled.");
        }

        Countdown countdown = new Countdown(id, teleporting.getLocation().clone());
        countdowns.put(id, countdown);

        teleporting.sendMessage("§aTeleporting in §f5§a...");
        countdown.taskId = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int seconds = 5;

            @Override
            public void run() {
                Countdown current = countdowns.get(id);
                Player p = Bukkit.getPlayer(id);
                Player dest = Bukkit.getPlayer(request.here ? request.senderId : request.targetId);

                if (current != countdown || p == null || !p.isOnline() || dest == null || !dest.isOnline()
                        || dest.getWorld() == null || p.getWorld() == null) {
                    cancelCountdown(id, false);
                    return;
                }

                if (moved(current.start, p.getLocation())) {
                    cancelCountdown(id, true);
                    return;
                }

                if (seconds <= 1) {
                    countdowns.remove(id);
                    Bukkit.getScheduler().cancelTask(current.taskId);

                    Location location = dest.getLocation().clone();
                    if (location.getWorld() == null) {
                        p.sendMessage("§cTeleport cancelled because the destination world is unavailable.");
                        return;
                    }

                    if (!p.teleport(location)) {
                        p.sendMessage("§cTeleport failed.");
                        return;
                    }

                    p.sendMessage("§a✔ Teleported successfully.");
                    return;
                }

                seconds--;
                p.sendMessage("§aTeleporting in §f" + seconds + "§a...");
            }
        }, 20L, 20L).getTaskId();
    }

    private boolean moved(Location start, Location now) {
        if (start.getWorld() == null || now.getWorld() == null || !start.getWorld().equals(now.getWorld())) {
            return true;
        }
        return start.distanceSquared(now) > 0.000001D;
    }

    private void cancelCountdown(UUID id, boolean moved) {
        Countdown countdown = countdowns.remove(id);
        if (countdown == null) return;
        if (countdown.taskId != -1) Bukkit.getScheduler().cancelTask(countdown.taskId);
        Player p = Bukkit.getPlayer(id);
        if (p != null && p.isOnline() && moved) {
            p.sendMessage("§cTeleport cancelled because you moved.");
        }
    }

    private boolean isValid(Request request) {
        Player sender = Bukkit.getPlayer(request.senderId);
        Player target = Bukkit.getPlayer(request.targetId);
        return sender != null && sender.isOnline() && target != null && target.isOnline();
    }

    private void expire(Request request) {
        if (!outgoing.containsKey(request.senderId) || outgoing.get(request.senderId) != request) return;
        remove(request);

        Player sender = Bukkit.getPlayer(request.senderId);
        Player target = Bukkit.getPlayer(request.targetId);
        if (sender != null && sender.isOnline()) sender.sendMessage("§cTeleport request expired.");
        if (target != null && target.isOnline()) target.sendMessage("§cTeleport request expired.");
    }

    public void cancelRequestsFor(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();

        Request sent = outgoing.remove(id);
        if (sent != null) {
            incoming.remove(sent.targetId, sent);
            Player target = Bukkit.getPlayer(sent.targetId);
            if (target != null && target.isOnline()) {
                target.sendMessage("§cTeleport request cancelled because the requester entered combat.");
            }
        }

        Request received = incoming.remove(id);
        if (received != null) {
            outgoing.remove(received.senderId, received);
            Player sender = Bukkit.getPlayer(received.senderId);
            if (sender != null && sender.isOnline()) {
                sender.sendMessage("§cTeleport request cancelled because the target entered combat.");
            }
        }
    }

    private void remove(Request request) {
        outgoing.remove(request.senderId, request);
        incoming.remove(request.targetId, request);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!countdowns.containsKey(event.getPlayer().getUniqueId())) return;
        if (event.getTo() == null || moved(event.getFrom(), event.getTo())) {
            cancelCountdown(event.getPlayer().getUniqueId(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();

        Request sent = outgoing.remove(id);
        if (sent != null) incoming.remove(sent.targetId, sent);

        Request received = incoming.remove(id);
        if (received != null) outgoing.remove(received.senderId, received);

        cancelCountdown(id, false);
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (!event.getPlugin().equals(plugin)) return;
        for (Countdown countdown : countdowns.values()) {
            if (countdown.taskId != -1) Bukkit.getScheduler().cancelTask(countdown.taskId);
        }
        countdowns.clear();
        outgoing.clear();
        incoming.clear();
    }

    private static final class Request {
        private final UUID senderId;
        private final UUID targetId;
        private final String senderName;
        private final String targetName;
        private final boolean here;

        private Request(UUID senderId, UUID targetId, String senderName, String targetName, boolean here) {
            this.senderId = senderId;
            this.targetId = targetId;
            this.senderName = senderName;
            this.targetName = targetName;
            this.here = here;
        }
    }

    private static final class Countdown {
        private final UUID playerId;
        private final Location start;
        private int taskId = -1;

        private Countdown(UUID playerId, Location start) {
            this.playerId = playerId;
            this.start = start;
        }
    }
}
