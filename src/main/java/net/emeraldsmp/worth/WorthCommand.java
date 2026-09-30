package net.emeraldsmp.worth;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class WorthCommand implements CommandExecutor, TabCompleter {
    private final EmeraldSMP plugin;
    public WorthCommand(EmeraldSMP plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { plugin.getMessageService().send(sender, "&cOnly players can use /worth."); return true; }
        String query = args.length == 0 ? "" : String.join(" ", args).trim();
        plugin.getWorthManager().openBrowser(p, query, 0, WorthCategoryFilter.ALL, WorthSort.NAME_ASC);
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        String q=args[0].toLowerCase(Locale.ROOT);
        return plugin.getWorthManager().all().stream()
                .map(e -> e.material().name().toLowerCase(Locale.ROOT))
                .filter(s -> s.contains(q)).distinct().sorted().limit(40).collect(Collectors.toList());
    }
}
