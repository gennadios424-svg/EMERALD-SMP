package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class RtpCommand implements CommandExecutor {
    private final EmeraldSMP plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private static final int MAX_ATTEMPTS = 40;

    public RtpCommand(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.getMessageService().send(sender, "&cOnly players can use /rtp.");
            return true;
        }

        if (!isWorldAllowed(p.getWorld())) {
            plugin.getMessageService().send(p, plugin.getConfig().getString(
                    "messages.rtp.not-allowed", "&cRTP is not allowed in this world."));
            return true;
        }

        long now = System.currentTimeMillis();
        long cooldownSeconds = Math.max(0L, plugin.getConfig().getLong("rtp.cooldown", 60L));
        long last = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        long remainingMs = (last + cooldownSeconds * 1000L) - now;

        if (!p.hasPermission("emerald.rtp.bypass") && remainingMs > 0) {
            long remaining = (remainingMs + 999L) / 1000L;
            String message = plugin.getConfig().getString(
                    "messages.rtp.cooldown",
                    "&cYou must wait &f%time%&c before using RTP again.")
                    .replace("%time%", formatTime(remaining));
            plugin.getMessageService().send(p, message);
            return true;
        }

        plugin.getMessageService().send(p, plugin.getConfig().getString(
                "messages.rtp.searching", "&7Finding a safe random location..."));

        Location destination = findSafeLocation(p.getWorld(), p.getLocation());
        if (destination == null) {
            plugin.getMessageService().send(p, plugin.getConfig().getString(
                    "messages.rtp.failed", "&cCould not find a safe RTP location. Please try again."));
            return true;
        }

        if (!p.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            plugin.getMessageService().send(p, plugin.getConfig().getString(
                    "messages.rtp.failed", "&cCould not teleport you. Please try again."));
            return true;
        }

        if (!p.hasPermission("emerald.rtp.bypass") && cooldownSeconds > 0) {
            cooldowns.put(p.getUniqueId(), now);
        }

        String message = plugin.getConfig().getString(
                "messages.rtp.success", "&aTeleported to &f%world% &7(&f%x%&7, &f%z%&7)&a.");
        message = message
                .replace("%world%", destination.getWorld().getName())
                .replace("%x%", Integer.toString(destination.getBlockX()))
                .replace("%y%", Integer.toString(destination.getBlockY()))
                .replace("%z%", Integer.toString(destination.getBlockZ()));
        plugin.getMessageService().send(p, message);
        return true;
    }

    private boolean isWorldAllowed(World world) {
        List<String> worlds = plugin.getConfig().getStringList("rtp.worlds");
        if (worlds.isEmpty()) return true;
        for (String name : worlds) {
            if (name.equalsIgnoreCase(world.getName())) return true;
        }
        return false;
    }

    private Location findSafeLocation(World world, Location center) {
        int min = Math.max(0, plugin.getConfig().getInt("rtp.min-distance", 500));
        int max = Math.max(min + 1, plugin.getConfig().getInt("rtp.max-distance", 5000));
        int worldBorderRadius = (int) Math.min(
                max,
                Math.max(1, Math.floor(world.getWorldBorder().getSize() / 2.0) - 16)
        );

        max = Math.min(max, worldBorderRadius);
        if (max <= min) min = Math.max(0, max / 2);

        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = random.nextDouble(0, Math.PI * 2.0);
            double distance = Math.sqrt(random.nextDouble(
                    (double) min * min, (double) max * max
            ));

            int x = (int) Math.round(center.getX() + Math.cos(angle) * distance);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * distance);

            if (!world.getWorldBorder().isInside(new Location(world, x, 64, z))) continue;

            Location safe = findSafeAt(world, x, z);
            if (safe != null) return safe;
        }

        return null;
    }

    private Location findSafeAt(World world, int x, int z) {
        int highest = world.getHighestBlockYAt(x, z);
        int minY = world.getMinHeight() + 1;
        int maxY = Math.min(highest, world.getMaxHeight() - 3);

        for (int y = maxY; y >= minY && y >= maxY - 64; y--) {
            Block floor = world.getBlockAt(x, y, z);
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);

            if (!isSafeFloor(floor)) continue;
            if (!feet.isPassable() || !head.isPassable()) continue;
            if (feet.isLiquid() || head.isLiquid()) continue;

            Location loc = new Location(world, x + 0.5, y + 1.0, z + 0.5);
            loc.setYaw(ThreadLocalRandom.current().nextFloat() * 360.0f);
            loc.setPitch(0.0f);
            return loc;
        }

        return null;
    }

    private boolean isSafeFloor(Block block) {
        Material m = block.getType();

        if (!m.isSolid()) return false;
        if (block.isLiquid()) return false;

        return switch (m) {
            case LAVA, MAGMA_BLOCK, CACTUS, FIRE, SOUL_FIRE,
                 CAMPFIRE, SOUL_CAMPFIRE, POWDER_SNOW,
                 SWEET_BERRY_BUSH, POINTED_DRIPSTONE,
                 WITHER_ROSE, TNT, END_PORTAL, END_GATEWAY,
                 NETHER_PORTAL, WATER -> false;
            default -> true;
        };
    }

    private String formatTime(long seconds) {
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        return remainder == 0 ? minutes + "m" : minutes + "m " + remainder + "s";
    }
}
