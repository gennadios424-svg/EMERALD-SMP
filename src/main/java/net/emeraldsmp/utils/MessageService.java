package net.emeraldsmp.utils;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public final class MessageService {
    private final EmeraldSMP plugin;
    public MessageService(EmeraldSMP plugin) { this.plugin = plugin; }

    public String format(String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&a💚 &2&lEmerald SMP &8»");
        return color(prefix + " " + message);
    }

    public String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public void send(CommandSender sender, String message) {
        sender.sendMessage(format(message));
    }

    public void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }
}
