package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopCommand implements CommandExecutor {
    private final EmeraldSMP plugin;
    public ShopCommand(EmeraldSMP plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /shop.");
            return true;
        }
        plugin.getShopManager().openMain(player);
        return true;
    }
}
