package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
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
            return setSpawn(player, args);
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

        if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("safe"))) {
            player.sendMessage("§cUsage: /setspawn [safe]");
            return true;
        }

        Location location = player.getLocation();
        World world = location.getWorld();

        if (world == null || !isFinite(location) || !isSafe(location)) {
            player.sendMessage("§c✘ Stand on a valid spawn platform with two clear blocks above it.");
            player.sendMessage("§7Air around the platform is allowed; natural terrain is not required.");
            return true;
        }

        boolean safeMode = args.length == 1 && args[0].equalsIgnoreCase("safe");

        plugin.getConfig().set("spawn.world", world.getName());
        plugin.getConfig().set("spawn.x", location.getX());
        plugin.getConfig().set("spawn.y", location.getY());
        plugin.getConfig().set("spawn.z", location.getZ());
        plugin.getConfig().set("spawn.yaw", location.getYaw());
        plugin.getConfig().set("spawn.pitch", location.getPitch());
        plugin.getConfig().set("spawn.safe", safeMode);
        plugin.saveConfig();

        player.sendMessage("§a💚 Emerald SMP spawn has been set at your exact location.");
        if (safeMode) {
            player.sendMessage("§7Safe custom-spawn mode enabled. Floating/custom platforms are allowed.");
        }
        return true;
    }

    private boolean teleportToSpawn(Player player) {
        Location configured = readConfiguredSpawn();

        if (configured == null || configured.getWorld() == null) {
            player.sendMessage("§c✘ Spawn is not configured.");
            return true;
        }

        // Do not search for terrain or move the player to another block.
        // The configured world, coordinates, yaw and pitch are authoritative.
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

    private boolean isSafe(Location location) {
        if (location.getWorld() == null || !isFinite(location)) return false;

        World world = location.getWorld();
        int blockY = location.getBlockY();

        if (blockY <= world.getMinHeight() || blockY + 1 >= world.getMaxHeight()) return false;

        var feetBlock = world.getBlockAt(location.getBlockX(), blockY, location.getBlockZ());
        var headBlock = world.getBlockAt(location.getBlockX(), blockY + 1, location.getBlockZ());
        var belowBlock = world.getBlockAt(location.getBlockX(), blockY - 1, location.getBlockZ());

        Material feet = feetBlock.getType();
        Material head = headBlock.getType();
        Material below = belowBlock.getType();

        // Player space must be clear, while exactly one solid platform block
        // must exist underneath. This intentionally allows floating/custom
        // platforms and does not require surrounding terrain.
        return feetBlock.isPassable()
                && headBlock.isPassable()
                && !feet.isLiquid()
                && !head.isLiquid()
                && below.isSolid()
                && !below.isLiquid()
                && !isDangerousFloor(below);
    }

    private boolean isDangerousFloor(Material material) {
        String name = material.name();
        return name.contains("LAVA")
                || name.contains("MAGMA")
                || name.contains("CAMPFIRE")
                || name.contains("SOUL_FIRE")
                || name.contains("FIRE")
                || name.contains("CACTUS")
                || name.contains("SWEET_BERRY_BUSH");
    }

    private boolean isFinite(Location location) {
        return Double.isFinite(location.getX())
                && Double.isFinite(location.getY())
                && Double.isFinite(location.getZ())
                && Float.isFinite(location.getYaw())
                && Float.isFinite(location.getPitch());
    }
}
