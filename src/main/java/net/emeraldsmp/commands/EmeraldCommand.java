package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.Collections;
import java.util.List;

public final class EmeraldCommand implements CommandExecutor, TabCompleter {
    private final EmeraldSMP plugin;
    public EmeraldCommand(EmeraldSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.getMessageService().sendRaw(sender, "&a💚 &2&lEmerald SMP");
            plugin.getMessageService().sendRaw(sender, "&8» &fFoundation successfully installed.");
            plugin.getMessageService().sendRaw(sender, "&8» &fVersion: &a1.0");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("emerald.admin")) {
                plugin.getMessageService().send(sender, "&cYou do not have permission to do that.");
                return true;
            }
            if (plugin.getConfigManager().reload()) {
                plugin.getMessageService().send(sender, "&aConfiguration reloaded successfully.");
            } else {
                plugin.getMessageService().send(sender, "&cConfiguration could not be reloaded.");
            }
            return true;
        }
        plugin.getMessageService().send(sender, "&cUsage: /emerald [reload]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("emerald.admin")) return Collections.singletonList("reload");
        return Collections.emptyList();
    }
}
