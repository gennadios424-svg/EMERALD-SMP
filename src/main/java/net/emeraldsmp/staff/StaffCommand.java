package net.emeraldsmp.staff;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.roles.RoleManager;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;

public final class StaffCommand implements CommandExecutor, TabCompleter, Listener {
    private final EmeraldSMP plugin;
    private final Set<UUID> vanished = new HashSet<>();
    private final File ipFile;
    private final Map<UUID, String> storedIps = new HashMap<>();

    public StaffCommand(EmeraldSMP p) {
        plugin = p;
        ipFile = new File(p.getDataFolder(), "ipbans.yml");
        loadIps();
    }

    private boolean staff(Player p) { return plugin.getRoleManager().isStaff(p); }
    private boolean owner(Player p) { return plugin.getRoleManager().get(p) == RoleManager.Role.OWNER; }

    private boolean check(CommandSender s, String n, boolean ownerOnly) {
        if (!(s instanceof Player p)) {
            s.sendMessage("§cOnly Emerald SMP staff can use /" + n + ".");
            return false;
        }
        if (ownerOnly ? !owner(p) : !staff(p)) {
            s.sendMessage("§c§lNO PERMISSION §8» §fThis command is restricted to Emerald SMP staff.");
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        String n = c.getName().toLowerCase(Locale.ROOT);
        if (!check(s, n, n.equals("ipunban"))) return true;
        Player p = (Player) s;
        switch (n) {
            case "vanish" -> vanish(p);
            case "fly" -> fly(p);
            case "tp" -> tp(p, a, false);
            case "tphere" -> tp(p, a, true);
            case "kick" -> punish(p, a, false, false);
            case "ban" -> punish(p, a, true, false);
            case "ipban" -> punish(p, a, true, true);
            case "unban" -> unban(p, a, false);
            case "ipunban" -> unban(p, a, true);
        }
        return true;
    }

    private void vanish(Player p) {
        UUID u = p.getUniqueId();
        if (vanished.remove(u)) {
            for (Player x : Bukkit.getOnlinePlayers()) x.showPlayer(plugin, p);
            p.sendMessage("§a§l👁 VANISH DISABLED");
        } else {
            vanished.add(u);
            for (Player x : Bukkit.getOnlinePlayers()) if (!x.equals(p)) x.hidePlayer(plugin, p);
            p.sendMessage("§a§l👻 VANISH ENABLED");
        }
    }

    private void fly(Player p) {
        boolean on = !p.getAllowFlight();
        p.setAllowFlight(on);
        p.setFlying(on);
        p.sendMessage(on ? "§a§l🪽 Flight Enabled" : "§c§l🪽 Flight Disabled");
    }

    private void tp(Player p, String[] a, boolean here) {
        if (a.length != 1) {
            p.sendMessage("§cUsage: /" + (here ? "tphere" : "tp") + " <player>");
            return;
        }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) {
            p.sendMessage("§cPlayer not found or offline.");
            return;
        }
        if (here) {
            t.teleport(p.getLocation());
            p.sendMessage("§a⚡ Teleported §f" + t.getName() + " §ato you.");
        } else {
            p.teleport(t.getLocation());
            p.sendMessage("§a⚡ Teleported to §f" + t.getName() + "§a.");
        }
    }

    private void punish(Player s, String[] a, boolean ban, boolean ipban) {
        String command = ipban ? "ipban" : ban ? "ban" : "kick";
        if (a.length < 1) {
            s.sendMessage("§cUsage: /" + command + " <player> [reason]");
            return;
        }

        String inputName = a[0];
        UUID targetUuid = plugin.getPlayerDataManager().resolveUuid(inputName);
        if (targetUuid == null) {
            s.sendMessage("§c❌ Player §f" + inputName + " §cis not in Emerald SMP's persistent player data.");
            return;
        }
        if (targetUuid.equals(s.getUniqueId())) {
            s.sendMessage("§cYou cannot target yourself.");
            return;
        }

        RoleManager.Role targetRole = plugin.getRoleManager().get(targetUuid);
        if (plugin.getRoleManager().get(s.getUniqueId()).weight() <= targetRole.weight()) {
            s.sendMessage("§c❌ You cannot punish a player with an equal or higher staff rank.");
            return;
        }

        String targetName = plugin.getPlayerDataManager().getStoredUsername(targetUuid);
        if (targetName == null) targetName = inputName;
        String reason = a.length > 1 ? String.join(" ", Arrays.copyOfRange(a, 1, a.length)) : "Emerald SMP staff action";

        if (ipban) {
            String ip = plugin.getPlayerDataManager().getStoredIp(targetUuid);
            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null && target.getAddress() != null && target.getAddress().getAddress() != null) {
                ip = target.getAddress().getAddress().getHostAddress();
            }
            if (ip == null || ip.isBlank()) {
                s.sendMessage("§c❌ No previously known IP is stored for §f" + targetName + "§c. The IP ban was not applied.");
                return;
            }
            Bukkit.getBanList(BanList.Type.IP).addBan(ip, reason, null, s.getName());
            storedIps.put(targetUuid, ip);
            saveIps();
            if (target != null) {
                target.kickPlayer("§c§lEMERALD SMP\n§fYou have been IP banned.\n§7Reason: §f" + reason);
            }
            s.sendMessage("§a🔒 IP ban applied to §f" + targetName + "§a.");
            return;
        }

        if (ban) {
            Bukkit.getBanList(BanList.Type.NAME).addBan(targetName, reason, null, s.getName());
            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null) target.kickPlayer("§c§lEMERALD SMP\n§fYou have been banned.\n§7Reason: §f" + reason);
            s.sendMessage("§a🔨 Ban applied to §f" + targetName + "§a.");
        } else {
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                s.sendMessage("§c❌ Kick requires the target to be online.");
                return;
            }
            target.kickPlayer("§e§lEMERALD SMP\n§fYou have been kicked.\n§7Reason: §f" + reason);
            s.sendMessage("§a🦶 Kicked §f" + targetName + "§a.");
        }
    }

    private void unban(Player s, String[] a, boolean ip) {
        if (a.length != 1) {
            s.sendMessage("§cUsage: /" + (ip ? "ipunban" : "unban") + " <player>");
            return;
        }
        String name = a[0];
        if (ip) {
            UUID u = plugin.getPlayerDataManager().resolveUuid(name);
            String addr = u == null ? null : storedIps.get(u);
            if (addr == null && u != null) addr = plugin.getPlayerDataManager().getStoredIp(u);
            if (addr == null) {
                s.sendMessage("§cNo IP ban found for §f" + name + "§c.");
                return;
            }
            if (!Bukkit.getBanList(BanList.Type.IP).isBanned(addr)) {
                s.sendMessage("§cNo IP ban found for §f" + name + "§c.");
                return;
            }
            Bukkit.getBanList(BanList.Type.IP).pardon(addr);
            if (u != null) {
                storedIps.remove(u);
                saveIps();
            }
            s.sendMessage("§a✅ " + name + "'s IP ban has been removed.");
            return;
        }

        String canonical = name;
        UUID u = plugin.getPlayerDataManager().resolveUuid(name);
        if (u != null) {
            String stored = plugin.getPlayerDataManager().getStoredUsername(u);
            if (stored != null) canonical = stored;
        }
        if (!Bukkit.getBanList(BanList.Type.NAME).isBanned(canonical)) {
            s.sendMessage("§cNo player ban was found for §f" + name + "§c.");
            return;
        }
        Bukkit.getBanList(BanList.Type.NAME).pardon(canonical);
        s.sendMessage("§a✅ " + canonical + " has been unbanned.");
    }

    private void loadIps() {
        if (!ipFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(ipFile);
        for (String x : y.getStringList("entries")) {
            try {
                String[] a = x.split("=", 2);
                if (a.length == 2) storedIps.put(UUID.fromString(a[0]), a[1]);
            } catch (Exception ignored) {}
        }
    }

    private void saveIps() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> x = new ArrayList<>();
        for (var e : storedIps.entrySet()) x.add(e.getKey() + "=" + e.getValue());
        y.set("entries", x);
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            y.save(ipFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save ipbans.yml: " + e.getMessage());
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        Player j = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (UUID u : vanished) {
                Player h = Bukkit.getPlayer(u);
                if (h != null && !h.equals(j)) j.hidePlayer(plugin, h);
            }
        });
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        vanished.remove(e.getPlayer().getUniqueId());
    }

    public boolean isVanished(Player p) { return vanished.contains(p.getUniqueId()); }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length != 1) return List.of();
        String q = a[0].toLowerCase(Locale.ROOT);
        List<String> x = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(q)) x.add(p.getName());
        return x;
    }
}
