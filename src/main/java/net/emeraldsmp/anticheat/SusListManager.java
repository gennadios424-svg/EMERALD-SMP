package net.emeraldsmp.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class SusListManager {
    private final JavaPlugin plugin;
    private final Map<UUID, Suspect> suspects = new LinkedHashMap<>();
    private final File file;

    public SusListManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "suslist.yml");
        load();
    }

    public void add(Player player, Location location, String reason, int flags) {
        suspects.put(player.getUniqueId(), new Suspect(
                player.getUniqueId(), player.getName(), location.clone(), reason, flags, System.currentTimeMillis()));
        save();
    }

    public boolean has(UUID uuid) {
        return suspects.containsKey(uuid);
    }

    public Collection<Suspect> all() {
        return Collections.unmodifiableCollection(suspects.values());
    }

    public Suspect get(UUID uuid) {
        return suspects.get(uuid);
    }

    public void remove(UUID uuid) {
        suspects.remove(uuid);
        save();
    }

    public void clear() {
        suspects.clear();
        save();
    }

    public void sendList(CommandSender sender) {
        if (suspects.isEmpty()) {
            sender.sendMessage("§a§lEMERALD SUSLIST §8» §7No anti-cheat suspects.");
            return;
        }
        sender.sendMessage("§a§lEMERALD SUSLIST §8» §f" + suspects.size() + " suspect(s)");
        int i = 1;
        for (Suspect s : suspects.values()) {
            Player online = Bukkit.getPlayer(s.uuid());
            String status = online != null ? "§aONLINE" : "§7OFFLINE";
            sender.sendMessage("§8" + i++ + ". §f" + s.name() + " §8» §c" + s.reason()
                    + " §8(§f" + s.flags() + " flags§8) " + status);
        }
        sender.sendMessage("§7Use §f/suslist tp <player> §7to teleport to their recorded detection location.");
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Suspect s : suspects.values()) {
            String path = "suspects." + s.uuid();
            yml.set(path + ".name", s.name());
            yml.set(path + ".reason", s.reason());
            yml.set(path + ".flags", s.flags());
            yml.set(path + ".detected-at", s.detectedAt());
            yml.set(path + ".world", s.location().getWorld() == null ? null : s.location().getWorld().getName());
            yml.set(path + ".x", s.location().getX());
            yml.set(path + ".y", s.location().getY());
            yml.set(path + ".z", s.location().getZ());
            yml.set(path + ".yaw", s.location().getYaw());
            yml.set(path + ".pitch", s.location().getPitch());
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save suslist.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        var section = yml.getConfigurationSection("suspects");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String worldName = yml.getString("suspects." + key + ".world");
                if (worldName == null) continue;
                var world = Bukkit.getWorld(worldName);
                if (world == null) continue;
                Location loc = new Location(world,
                        yml.getDouble("suspects." + key + ".x"),
                        yml.getDouble("suspects." + key + ".y"),
                        yml.getDouble("suspects." + key + ".z"),
                        (float)yml.getDouble("suspects." + key + ".yaw"),
                        (float)yml.getDouble("suspects." + key + ".pitch"));
                suspects.put(uuid, new Suspect(uuid,
                        yml.getString("suspects." + key + ".name", uuid.toString()),
                        loc,
                        yml.getString("suspects." + key + ".reason", "AntiCheat"),
                        yml.getInt("suspects." + key + ".flags"),
                        yml.getLong("suspects." + key + ".detected-at")));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public record Suspect(UUID uuid, String name, Location location, String reason, int flags, long detectedAt) {}
}
