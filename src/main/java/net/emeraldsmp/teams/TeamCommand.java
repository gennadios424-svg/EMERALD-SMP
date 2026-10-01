package net.emeraldsmp.teams;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.roles.RoleUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

public final class TeamCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String TITLE = "§2§l💚 TEAM";
    private final EmeraldSMP plugin;
    private final TeamManager manager;

    public TeamCommand(EmeraldSMP plugin, TeamManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (args.length == 0) { open(p); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 2) { msg(p, "&cUsage: /team create <name>"); return true; }
                if (manager.create(p, args[1])) msg(p, "&aTeam created: &f" + args[1]);
                else msg(p, "&cCould not create that team. Names are 3-16 letters/numbers/underscores and you cannot already be in a team.");
            }
            case "invite" -> {
                if (args.length < 2) { msg(p, "&cUsage: /team invite <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { msg(p, "&cThat player must be online."); return true; }
                if (manager.invite(p, target)) {
                    msg(p, "&aInvite sent to &f" + target.getName());
                    msg(target, "&aYou were invited to &f" + manager.getTeam(p.getUniqueId()).name() + "&a. Use &f/team accept&a.");
                } else msg(p, "&cYou cannot invite that player.");
            }
            case "accept" -> msg(p, manager.accept(p) ? "&aYou joined the team." : "&cYou have no valid pending team invite.");
            case "leave" -> {
                if (manager.leave(p)) msg(p, "&aYou left your team.");
                else msg(p, "&cOwners cannot leave. Disbanding can be added later from team settings.");
            }
            case "kick" -> {
                if (args.length < 2) { msg(p, "&cUsage: /team kick <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !manager.kick(p, target)) msg(p, "&cYou cannot kick that player.");
                else msg(p, "&aPlayer removed from the team.");
            }
            case "info" -> open(p);
            default -> msg(p, "&7/team create|invite|accept|leave|kick|info");
        }
        return true;
    }

    private void open(Player p) {
        TeamManager.TeamData team = manager.getTeam(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        if (team == null) {
            inv.setItem(13, item(Material.EMERALD, "§a§l➕ Create Team", List.of("§7Use §f/team create <name>§7")));
            inv.setItem(15, item(Material.PAPER, "§f/team info", List.of("§7You are not currently in a team.")));
        } else {
            inv.setItem(4, item(Material.EMERALD, "§a§l💚 " + team.name(), List.of(
                    "§7Owner: §f" + Bukkit.getOfflinePlayer(team.owner()).getName(),
                    "§7Role: §f" + plugin.getRoleManager().get(p).color()+plugin.getRoleManager().get(p).label())));
            StringBuilder members = new StringBuilder("§7Members:");
            for (String name : manager.memberNames(team)) members.append("\n§f• ").append(name);
            inv.setItem(11, item(Material.PLAYER_HEAD, "§f👥 Members", List.of(members.toString().split("\n"))));
            inv.setItem(13, item(Material.LIME_DYE, "§a➕ Invite Player", List.of("§7Use §f/team invite <player>")));
            inv.setItem(15, item(Material.COMPARATOR, "§f⚙ Team Settings", List.of(
                    team.owner().equals(p.getUniqueId()) ? "§7Owner management is available through commands." : "§7Owner-only management")));
            inv.setItem(22, item(Material.BARRIER, "§c🚪 Leave Team", List.of(
                    team.owner().equals(p.getUniqueId()) ? "§7The owner cannot leave the team." : "§7Click to leave")));
        }
        p.openInventory(inv);
    }

    private ItemStack item(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        i.setItemMeta(meta);
        return i;
    }

    private void msg(Player p, String text) { p.sendMessage(ChatColor.translateAlternateColorCodes('&', "§2§l💚 Emerald SMP §8» " + text)); }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !e.getView().getTitle().equals(TITLE)) return;
        e.setCancelled(true);
        if (e.getRawSlot() == 22 && manager.getTeam(p.getUniqueId()) != null) {
            if (manager.leave(p)) { p.closeInventory(); msg(p, "&aYou left your team."); }
            else msg(p, "&cThe team owner cannot leave.");
        }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("create","invite","accept","leave","kick","info");
        return List.of();
    }
}
