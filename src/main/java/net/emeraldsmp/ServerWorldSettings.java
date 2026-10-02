package net.emeraldsmp;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Applies only the survival-world settings owned by EmeraldSMP.
 * The protected emerald_spawn world is intentionally excluded.
 */
public final class ServerWorldSettings implements Listener {
    private final EmeraldSMP plugin;

    public ServerWorldSettings(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    public void applyLoadedWorlds() {
        for (World world : plugin.getServer().getWorlds()) apply(world);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        apply(event.getWorld());
    }

    private void apply(World world) {
        String survival = plugin.getConfig().getString("server.survival-world", "world");
        String spawn = plugin.getConfig().getString("spawn.world", "");

        if (world.getName().equalsIgnoreCase("emerald_spawn")
                || (!spawn.isBlank() && world.getName().equalsIgnoreCase(spawn))
                || !world.getName().equalsIgnoreCase(survival)) {
            return;
        }

        double size = plugin.getConfig().getDouble("server.survival-border-size", 150000D);
        if (size < 1D) size = 150000D;

        if (Math.abs(world.getWorldBorder().getSize() - size) > 0.01D) {
            world.getWorldBorder().setSize(size);
            plugin.getLogger().info("Survival world border set to " + (long) size + " blocks in " + world.getName() + ".");
        }
    }
}
