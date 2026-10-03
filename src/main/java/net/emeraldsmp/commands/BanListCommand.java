package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BanListCommand implements CommandExecutor {
    private static final int PER_PAGE = 10;
    private final EmeraldSMP plugin;

    public BanListCommand(EmeraldSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("emerald.admin")) {
            plugin.getMessageService().send(sender, "&cYou do not have permission to do that.");
            return true;
        }
        int page = 1;
        if (args.length > 1) {
            plugin.getMessageService().send(sender, "&cUsage: /banlist [page]");
            return true;
        }
        if (args.length == 1) {
            try { page = Math.max(1, Integer.parseInt(args[0])); }
            catch (NumberFormatException ex) {
                plugin.getMessageService().send(sender, "&cPage must be a number.");
                return true;
            }
        }

        List<String> bans = new ArrayList<>();
        for (BanEntry<?> entry : Bukkit.getBanList(BanList.Type.NAME).getEntries()) {
            Object target = entry.getTarget();
            if (target != null && !target.toString().isBlank()) bans.add(target.toString());
        }
        bans.sort(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT)));
        int total = bans.size();
        int pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        if (page > pages) page = pages;

        sender.sendMessage("§2§m━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§a§l💚 BAN LIST §7— Page " + page + "/" + pages);
        sender.sendMessage("§2§m━━━━━━━━━━━━━━━━━━━━");
        int from = (page - 1) * PER_PAGE;
        int to = Math.min(from + PER_PAGE, total);
        if (from >= to) sender.sendMessage("§7No players are currently banned.");
        else for (int i = from; i < to; i++) sender.sendMessage("§c🔨 §f" + bans.get(i));
        sender.sendMessage("§7Total Banned: §f" + total);
        sender.sendMessage("§2§m━━━━━━━━━━━━━━━━━━━━");
        if (page > 1) sender.sendMessage(Component.text("§a◀ Previous").clickEvent(ClickEvent.runCommand("/banlist " + (page - 1))));
        if (page < pages) sender.sendMessage(Component.text("§aNext ▶").clickEvent(ClickEvent.runCommand("/banlist " + (page + 1))));
        return true;
    }
}
