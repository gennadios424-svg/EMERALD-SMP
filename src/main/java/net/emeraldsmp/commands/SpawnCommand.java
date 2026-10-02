package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

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
            return setSpawn(player, args);
        }

        if (args.length != 0) {
            player.sendMessage("§cUsage: /spawn");
            return true;
        }

        return teleportToSpawn(player);
    }

    private boolean setSpawn(Player player, String[] args) {
        boolean authorized = player.isOp()
                || player.hasPermission("emerald.admin")
                || plugin.getRoleManager().isStaff(player);

        if (!authorized) {
            player.sendMessage("§c§lNO PERMISSION §8» §fOnly Emerald SMP staff can use /setspawn.");
            return true;
        }

        if (args.length != 0) {
            player.sendMessage("§cUsage: /setspawn");
            return true;
        }

        Location location = player.getLocation();
        World world = location.getWorld();

        // Save exactly where the authorized player is standing.
        // No safe-location search and no coordinate adjustment are performed.
        if (world == null || !isFinite(location) || !isValidStandingSpace(player)) {
            player.sendMessage("§c✘ You cannot set spawn while your body or head is inside a solid block.");
            return true;
        }

        plugin.getConfig().set("spawn.world", world.getName());
        plugin.getConfig().set("spawn.x", location.getX());
        plugin.getConfig().set("spawn.y", location.getY());
        plugin.getConfig().set("spawn.z", location.getZ());
        plugin.getConfig().set("spawn.yaw", location.getYaw());
        plugin.getConfig().set("spawn.pitch", location.getPitch());
        plugin.saveConfig();

        player.sendMessage("§a💚 Emerald SMP spawn has been set at your exact location.");
        player.sendMessage("§7World: §f" + world.getName()
                + " §8| §7XYZ: §f"
                + String.format(Locale.US, "%.3f %.3f %.3f", location.getX(), location.getY(), location.getZ()));
        return true;
    }

    private boolean teleportToSpawn(Player player) {
        Location configured = readConfiguredSpawn();

        if (configured == null || configured.getWorld() == null) {
            player.sendMessage("§c✘ Spawn is not configured. An authorized player must run /setspawn first.");
            return true;
        }

        // The saved Location is authoritative: world, XYZ, yaw and pitch are all preserved.
        if (!player.teleport(configured)) {
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
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return null;
        if (y < world.getMinHeight() || y > world.getMaxHeight()) return null;

        return new Location(world, x, y, z, yaw, pitch);
    }

    private boolean isValidStandingSpace(Player player) {
        World world = player.getWorld();
        BoundingBox box = player.getBoundingBox();

        // Check only the blocks actually intersecting the player's current body.
        // A block directly below the player is not considered a body collision.
        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(Math.nextDown(box.getMaxX()));
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(Math.nextDown(box.getMaxY()));
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(Math.nextDown(box.getMaxZ()));

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir() || block.isPassable()) continue;
                    if (block.getCollisionShape().overlaps(box)) return false;
                }
            }
        }

        return true;
    }

    private boolean isFinite(Location location) {
        return Double.isFinite(location.getX())
                && Double.isFinite(location.getY())
                && Double.isFinite(location.getZ())
                && Float.isFinite(location.getYaw())
                && Float.isFinite(location.getPitch());
    }
}
