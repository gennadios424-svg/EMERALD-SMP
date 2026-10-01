package net.emeraldsmp.staff;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.roles.RoleManager;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.InetSocketAddress;
import java.util.*;

public final class StaffCommand implements CommandExecutor, TabCompleter, Listener {
    private final EmeraldSMP plugin;
    private final Set<UUID> vanished = new HashSet<>();

    public StaffCommand(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    private RoleManager.Role role(Player p) {
        return plugin.getRoleManager().get(p);
    }

    private boolean staff(Player p) {
        RoleManager.Role r = role(p);
        return r == RoleManager.Role.OWNER || r == RoleManager.Role.DEV || r == RoleManager.Role.MOD;
    }

    private boolean owner(Player p) {
        return role(p) == RoleManager.Role.OWNER;
    }

    private boolean check(CommandSender sender, String command, boolean ownerOnly) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly an Emerald SMP staff member can use /" + command + ".");
            return false;
        }
        if (ownerOnly ? !owner(p) : !staff(p)) {
            sender.sendMessage("§c§lNO PERMISSION §8» §fThis command is restricted to the configured Emerald SMP staff role.");
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        boolean ownerOnly = name.equals("ipban") || name.equals("ipunban");
        if (!check(sender, name, ownerOnly)) return true;
        Player p = (Player) sender;

        switch (name) {
            case "vanish" -> toggleVanish(p);
            case "fly" -> toggleFly(p);
            case "tp" -> teleport(p, args, false);
            case "tphere" -> teleport(p, args, true);
            case "kick" -> punish(p, args, false, false);
            case "ban" -> punish(p, args, true, false);
            case "ipban" -> punish(p, args, true, true);\n            case "unban" -> unban(p, args, false);\n            case "ipunban" -> unban(p, args, true);
            default -> p.sendMessage("§cUnknown staff command.");
        }
        return true;
    }

    private void toggleVanish(Player p) {
        UUID u = p.getUniqueId();
        if (vanished.contains(u)) {
            vanished.remove(u);
            for (Player other : Bukkit.getOnlinePlayers()) other.showPlayer(plugin, p);
            p.sendMessage("§a§l👁 VANISH DISABLED");
        } else {
            vanished.add(u);
            for (Player other : Bukkit.getOnlinePlayers()) if (!other.equals(p)) other.hidePlayer(plugin, p);
            p.sendMessage("§a§l👻 VANISH ENABLED");
        }
    }

    private void toggleFly(Player p) {
        boolean enabled = !p.getAllowFlight();
        p.setAllowFlight(enabled);
        p.setFlying(enabled);
        p.sendMessage(enabled ? "§a§l🪽 Flight Enabled" : "§c§l🪽 Flight Disabled");
    }

    private void teleport(Player staff, String[] args, boolean here) {
        if (args.length != 1) {
            staff.sendMessage("§cUsage: /" + (here ? "tphere" : "tp") + " <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            staff.sendMessage("§cPlayer not found or offline.");
            return;
        }

        if (here) {
            Location destination = staff.getLocation().clone();
            target.teleport(destination);
            staff.sendMessage("§a⚡ Teleported §f" + target.getName() + " §ato you instantly.");
            target.sendMessage("§7You were teleported by §f" + staff.getName() + "§7.");
        } else {
            staff.teleport(target.getLocation());
            staff.sendMessage("§a⚡ Teleported instantly to §f" + target.getName() + "§a.");
        }
    }

    private void unban(Player staff, String[] args, boolean ipunban) {
        if (args.length != 1) {
            staff.sendMessage("§cUsage: /" + (ipunban ? "ipunban" : "unban") + " <player>");
            return;
        }

        String target = args[0];

        if (ipunban) {
            Player targetPlayer = Bukkit.getPlayerExact(target);
            String ip = targetPlayer != null && targetPlayer.getAddress() != null && targetPlayer.getAddress().getAddress() != null
                    ? targetPlayer.getAddress().getAddress().getHostAddress() : null;

            if (ip == null) {
                staff.sendMessage("§cThat player must be online so their IP can be safely resolved.");
                return;
            }
            if (!Bukkit.getBanList(BanList.Type.IP).isBanned(ip)) {
                staff.sendMessage("§cNo IP ban was found for §f" + target + "§c.");
                return;
            }
            Bukkit.getBanList(BanList.Type.IP).pardon(ip);
            staff.sendMessage("§a🔓 IP ban removed for §f" + target + "§a.");
            return;
        }

        boolean removed = Bukkit.getBanList(BanList.Type.NAME).isBanned(target);
        if (!removed) {
            staff.sendMessage("§cNo player ban was found for §f" + target + "§c.");
            return;
        }
        Bukkit.getBanList(BanList.Type.NAME).pardon(target);
        staff.sendMessage("§a🔓 Ban removed for §f" + target + "§a.");
    }

    private void punish(Player staff, String[] args, boolean ban, boolean ipban) {
        if (args.length < 1) {
            staff.sendMessage("§cUsage: /" + (ipban ? "ipban" : ban ? "ban" : "kick") + " <player> [reason]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Emerald SMP staff action";

        if (target == null) {
            staff.sendMessage("§cTarget must be online for this staff command.");
            return;
        }
        if (target.equals(staff)) {
            staff.sendMessage("§cYou cannot target yourself.");
            return;
        }

        if (ipban) {
            InetSocketAddress address = target.getAddress();
            if (address == null || address.getAddress() == null) {
                staff.sendMessage("§cCould not safely resolve the target IP.");
                return;
            }
            String ip = address.getAddress().getHostAddress();
            Bukkit.getBanList(BanList.Type.IP).addBan(ip, reason, null, staff.getName());
            target.kickPlayer("§c§lEMERALD SMP\n§fYou have been IP banned.\n§7Reason: §f" + reason);
            staff.sendMessage("§a🔒 IP ban applied to §f" + target.getName() + "§a.");
            return;
        }

        if (ban) {
            Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), reason, null, staff.getName());
            target.kickPlayer("§c§lEMERALD SMP\n§fYou have been banned.\n§7Reason: §f" + reason);
            staff.sendMessage("§a🔨 Ban applied to §f" + target.getName() + "§a.");
        } else {
            target.kickPlayer("§e§lEMERALD SMP\n§fYou have been kicked.\n§7Reason: §f" + reason);
            staff.sendMessage("§a🦶 Kicked §f" + target.getName() + "§a.");
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        // A vanish state is session-scoped; never persist vanish across restarts.
        Player joined = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (UUID uuid : vanished) {
                Player hidden = Bukkit.getPlayer(uuid);
                if (hidden != null && !hidden.equals(joined)) joined.hidePlayer(plugin, hidden);
            }
        });
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        vanished.remove(e.getPlayer().getUniqueId());
    }

    public boolean isVanished(Player p) {
        return vanished.contains(p.getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
        }
        return out;
    }
}
