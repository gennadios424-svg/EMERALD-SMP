package net.emeraldsmp.tools;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Locale;

public final class EmeraldToolsCommand implements CommandExecutor, TabCompleter {
    private final EmeraldSMP plugin;
    private final EmeraldToolsManager manager;
    public EmeraldToolsCommand(EmeraldSMP plugin, EmeraldToolsManager manager) {
        this.plugin=plugin; this.manager=manager;
    }
    private boolean admin(CommandSender sender) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED+"❌ You do not have permission to use this command.");
            return false;
        }
        return true;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!admin(sender)) return true;
        Player target=args.length>0?Bukkit.getPlayerExact(args[0]):(sender instanceof Player p?p:null);
        if (target==null) { sender.sendMessage(ChatColor.RED+"Player must be online."); return true; }
        EmeraldToolsManager.ToolType type=switch(command.getName().toLowerCase(Locale.ROOT)) {
            case "sellaxe" -> EmeraldToolsManager.ToolType.SELL_AXE;
            case "treechopper" -> EmeraldToolsManager.ToolType.TREE_CHOPPER;
            case "emeraldhelmet" -> EmeraldToolsManager.ToolType.MONEY_HELMET;
            default -> null;
        };
        if (type==null) return true;
        target.getInventory().addItem(manager.createItem(type));
        sender.sendMessage(ChatColor.GREEN+"💚 Gave "+target.getName()+" "+type.name().toLowerCase(Locale.ROOT)+".");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length==1) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
