package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class SetSpawnCommand implements CommandExecutor {
    private final EmeraldSMP plugin;
    public SetSpawnCommand(EmeraldSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /setspawn.");
            return true;
        }
        if (!player.isOp() && !player.hasPermission("emerald.admin")) {
            player.sendMessage("§cYou do not have permission to use /setspawn.");
            return true;
        }

        plugin.getConfig().set("spawn.world", player.getWorld().getName());
        plugin.getConfig().set("spawn.x", player.getLocation().getX());
        plugin.getConfig().set("spawn.y", player.getLocation().getY());
        plugin.getConfig().set("spawn.z", player.getLocation().getZ());
        plugin.getConfig().set("spawn.yaw", player.getLocation().getYaw());
        plugin.getConfig().set("spawn.pitch", player.getLocation().getPitch());
        plugin.saveConfig();

        player.sendMessage("§a💚 Emerald SMP spawn has been set.");
        return true;
    }
}
