package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.roles.RoleManager;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class SpawnCommand implements CommandExecutor {
    private final EmeraldSMP plugin;

    public SpawnCommand(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /" + command.getName().toLowerCase(Locale.ROOT) + ".");
            return true;
        }

        if (command.getName().equalsIgnoreCase("setspawn")) {
            return setSpawn(player);
        }

        return teleportToSpawn(player);
    }

    private boolean setSpawn(Player player) {
        boolean authorized = player.isOp()
                || player.hasPermission("emerald.admin")
                || plugin.getRoleManager().isStaff(player);

        if (!authorized) {
            player.sendMessage("§c§lNO PERMISSION §8» §fOnly Emerald SMP staff can use /setspawn.");
            return true;
        }

        Location location = player.getLocation();
        World world = location.getWorld();

        if (world == null || !isFinite(location) || !isSafe(location)) {
            player.sendMessage("§c✘ Your current location is not a valid safe spawn location.");
            return true;
        }

        plugin.getConfig().set("spawn.world", world.getName());
        plugin.getConfig().set("spawn.x", location.getX());
        plugin.getConfig().set("spawn.y", location.getY());
        plugin.getConfig().set("spawn.z", location.getZ());
        plugin.getConfig().set("spawn.yaw", location.getYaw());
        plugin.getConfig().set("spawn.pitch", location.getPitch());
        plugin.saveConfig();

        player.sendMessage("§a💚 Emerald SMP spawn has been set.");
        return true;
    }

    private boolean teleportToSpawn(Player player) {
        Location configured = readConfiguredSpawn();
        Location safe = configured == null ? safeWorldSpawn() : makeSafe(configured);

        if (safe == null || safe.getWorld() == null) {
            player.sendMessage("§c✘ Spawn is not configured.");
            return true;
        }

        if (!player.teleport(safe)) {
            player.sendMessage("§c✘ Spawn teleport failed.");
            return true;
        }

        player.sendMessage("§a💚 Teleported to spawn.");
        return true;
    }

    private Location readConfiguredSpawn() {
        String worldName = plugin.getConfig().getString("spawn.world", "");
        if (worldName == null || worldName.isBlank()) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = plugin.getConfig().getDouble("spawn.x", Double.NaN);
        double y = plugin.getConfig().getDouble("spawn.y", Double.NaN);
        double z = plugin.getConfig().getDouble("spawn.z", Double.NaN);
        float yaw = (float) plugin.getConfig().getDouble("spawn.yaw", 0.0D);
        float pitch = (float) plugin.getConfig().getDouble("spawn.pitch", 0.0D);

        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        if (y < world.getMinHeight() || y > world.getMaxHeight()) return null;

        return new Location(world, x, y, z, yaw, pitch);
    }

    private Location safeWorldSpawn() {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) return null;
        return makeSafe(world.getSpawnLocation());
    }

    private Location makeSafe(Location requested) {
        if (requested == null || requested.getWorld() == null || !isFinite(requested)) return null;
        if (isSafe(requested)) return requested.clone();

        World world = requested.getWorld();
        int centerX = requested.getBlockX();
        int centerZ = requested.getBlockZ();

        for (int radius = 1; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;

                    int x = centerX + dx;
                    int z = centerZ + dz;
                    int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                    Location candidate = new Location(world, x + 0.5D, y, z + 0.5D, requested.getYaw(), requested.getPitch());

                    if (isSafe(candidate)) return candidate;
                }
            }
        }

        return null;
    }

    private boolean isSafe(Location location) {
        if (location.getWorld() == null || !isFinite(location)) return false;

        World world = location.getWorld();
        int blockY = location.getBlockY();
        if (blockY <= world.getMinHeight() || blockY >= world.getMaxHeight()) return false;

        Material feet = world.getBlockAt(location.getBlockX(), blockY, location.getBlockZ()).getType();
        Material head = world.getBlockAt(location.getBlockX(), blockY + 1, location.getBlockZ()).getType();
        Material below = world.getBlockAt(location.getBlockX(), blockY - 1, location.getBlockZ()).getType();

        return world.getBlockAt(location.getBlockX(), blockY, location.getBlockZ()).isPassable()
                && world.getBlockAt(location.getBlockX(), blockY + 1, location.getBlockZ()).isPassable()
                && !feet.isAir() && !head.isAir()
                && !feet.name().contains("LAVA")
                && !head.name().contains("LAVA")
                && !below.isAir()
                && !below.name().contains("LAVA");
    }

    private boolean isFinite(Location location) {
        return Double.isFinite(location.getX())
                && Double.isFinite(location.getY())
                && Double.isFinite(location.getZ())
                && Float.isFinite(location.getYaw())
                && Float.isFinite(location.getPitch());
    }
}
