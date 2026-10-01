package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class SpawnCommand implements CommandExecutor {
    private final EmeraldSMP plugin;

    public SpawnCommand(EmeraldSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /spawn.");
            return true;
        }
        Location configured = getConfiguredSpawn();
        Location destination = configured != null ? findSafe(configured) : null;

        if (destination == null) {
            World fallbackWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (fallbackWorld != null) destination = findSafe(fallbackWorld.getSpawnLocation());
        }

        if (destination == null) {
            player.sendMessage("§c✘ Spawn is not configured.");
            return true;
        }

        if (!player.teleport(destination.clone(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND)) {
            player.sendMessage("§c✘ Spawn teleport failed.");
            return true;
        }

        player.sendMessage("§a💚 Teleported to spawn.");
        return true;
    }

    private Location getConfiguredSpawn() {
        if (!plugin.getConfig().isConfigurationSection("spawn")) return null;
        String worldName = plugin.getConfig().getString("spawn.world");
        if (worldName == null || worldName.isBlank()) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        Location location = new Location(
                world,
                plugin.getConfig().getDouble("spawn.x"),
                plugin.getConfig().getDouble("spawn.y"),
                plugin.getConfig().getDouble("spawn.z"),
                (float) plugin.getConfig().getDouble("spawn.yaw"),
                (float) plugin.getConfig().getDouble("spawn.pitch"));
        return location;
    }

    private Location findSafe(Location base) {
        World world = base.getWorld();
        if (world == null) return null;

        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = bx + dx, z = bz + dz;
                    int startY = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, by));
                    for (int dy = 0; dy <= 8; dy++) {
                        int[] ys = dy == 0 ? new int[]{startY} : new int[]{startY + dy, startY - dy};
                        for (int y : ys) {
                            if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) continue;
                            Location candidate = new Location(world, x + 0.5, y, z + 0.5, base.getYaw(), base.getPitch());
                            if (isSafe(candidate)) return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null) return false;
        Material feet = world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ()).getType();
        Material head = world.getBlockAt(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ()).getType();
        Material floor = world.getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ()).getType();

        if (!feet.isAir() || !head.isAir() || !floor.isSolid()) return false;
        if (feet == Material.LAVA || head == Material.LAVA || floor == Material.LAVA) return false;
        return true;
    }
}
