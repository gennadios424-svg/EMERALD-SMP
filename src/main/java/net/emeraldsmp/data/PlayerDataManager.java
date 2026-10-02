package net.emeraldsmp.data;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class PlayerDataManager {
    private final EmeraldSMP plugin;
    private final Map<UUID, PlayerData> loaded = new HashMap<>();
    private File dataFolder;

    public PlayerDataManager(EmeraldSMP plugin) { this.plugin = plugin; }

    public synchronized void initialize() {
        dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) throw new IllegalStateException("Could not create player-data directory");
    }

    public synchronized PlayerData loadOrCreate(Player player) { return loadOrCreate(player.getUniqueId(), player.getName()); }

    public synchronized PlayerData loadOrCreate(UUID uuid, String username) {
        PlayerData data = loaded.get(uuid);
        if (data != null) {
            if (username != null && !username.isBlank()) data.setUsername(username);
            data.setLastSeen(System.currentTimeMillis());
            save(data);
            return data;
        }

        File file = fileFor(uuid);
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            long firstJoin = yaml.getLong("first-join", System.currentTimeMillis());
            long balance = Math.max(0L, yaml.getLong("balance", plugin.getConfigManager().getConfig().getLong("economy.starting-balance", 0L)));
            long shards = Math.max(0L, yaml.getLong("emerald-shards", 0L));
            long investment = Math.max(0L, yaml.getLong("investment.amount", 0L));
            long investmentEarnings = Math.max(0L, yaml.getLong("investment.earnings", 0L));
            boolean autoCollect = yaml.getBoolean("investment.auto-collect", false);
            data = new PlayerData(uuid, username == null ? yaml.getString("username", "Unknown") : username, firstJoin, System.currentTimeMillis(), balance, shards, investment, investmentEarnings, autoCollect);
            data.setLastIp(yaml.getString("last-ip"));
        } else {
            long now = System.currentTimeMillis();
            long starting = Math.max(0L, plugin.getConfigManager().getConfig().getLong("economy.starting-balance", 0L));
            data = new PlayerData(uuid, username == null ? "Unknown" : username, now, now, starting, 0L, 0L, 0L);
        }

        loaded.put(uuid, data);
        save(data);
        return data;
    }

    public synchronized PlayerData getLoaded(UUID uuid) { return loaded.get(uuid); }

    public synchronized Map<UUID, PlayerData> snapshotAll() {
        Map<UUID, PlayerData> out = new HashMap<>(loaded);
        if (dataFolder == null || !dataFolder.isDirectory()) return out;
        File[] files = dataFolder.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (files == null) return out;
        for (File f : files) {
            try {
                UUID uuid = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                if (out.containsKey(uuid)) continue;
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                long first = y.getLong("first-join", 0L);
                long last = y.getLong("last-seen", 0L);
                long bal = Math.max(0L, y.getLong("balance", 0L));
                long shards = Math.max(0L, y.getLong("emerald-shards", 0L));
                PlayerData d = new PlayerData(uuid, y.getString("username", uuid.toString()), first, last, bal, shards,
                        Math.max(0L, y.getLong("investment.amount", 0L)),
                        Math.max(0L, y.getLong("investment.earnings", 0L)),
                        y.getBoolean("investment.auto-collect", false));
                d.setLastIp(y.getString("last-ip"));
                out.put(uuid, d);
            } catch (Exception ignored) {}
        }
        return out;
    }

    /**
     * Resolves a previously known player from Emerald SMP's persistent player-data files.
     * Returns null when the name has never been recorded by this plugin.
     */
    public synchronized UUID resolveUuid(String username) {
        if (username == null || username.isBlank()) return null;
        Player online = plugin.getServer().getPlayerExact(username);
        if (online != null) return online.getUniqueId();
        String wanted = username.trim();
        if (dataFolder == null || !dataFolder.isDirectory()) return null;
        File[] files = dataFolder.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (files == null) return null;
        for (File f : files) {
            try {
                UUID uuid = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                String stored = y.getString("username");
                if (stored != null && stored.equalsIgnoreCase(wanted)) return uuid;
            } catch (Exception ignored) {}
        }
        return null;
    }

    public synchronized String getStoredUsername(UUID uuid) {
        PlayerData data = loaded.get(uuid);
        if (data != null && data.getUsername() != null && !data.getUsername().isBlank()) return data.getUsername();
        File f = fileFor(uuid);
        if (!f.exists()) return null;
        String name = YamlConfiguration.loadConfiguration(f).getString("username");
        return name == null || name.isBlank() ? null : name;
    }

    public synchronized String getStoredIp(UUID uuid) {
        PlayerData data = loaded.get(uuid);
        if (data != null && data.getLastIp() != null) return data.getLastIp();
        File f = fileFor(uuid);
        if (!f.exists()) return null;
        String ip = YamlConfiguration.loadConfiguration(f).getString("last-ip");
        return ip == null || ip.isBlank() ? null : ip;
    }

    public synchronized long getEmeraldShards(UUID uuid) {
        PlayerData data = loaded.get(uuid);
        if (data != null) return data.getEmeraldShards();
        File file = fileFor(uuid);
        if (!file.exists()) return 0L;
        return Math.max(0L, YamlConfiguration.loadConfiguration(file).getLong("emerald-shards", 0L));
    }

    public synchronized void setEmeraldShards(UUID uuid, long amount) {
        PlayerData data = loaded.get(uuid);
        if (data == null) data = loadOrCreate(uuid, null);
        data.setEmeraldShards(amount);
        save(data);
    }

    public synchronized boolean withdrawEmeraldShards(UUID uuid, long amount) {
        if (amount <= 0) return false;
        PlayerData data = loaded.get(uuid);
        if (data == null) data = loadOrCreate(uuid, null);
        long old = data.getEmeraldShards();
        if (old < amount) return false;
        data.setEmeraldShards(old - amount);
        if (save(data)) return true;
        data.setEmeraldShards(old);
        return false;
    }

    public synchronized void markSeen(Player player) {
        PlayerData data = loadOrCreate(player);
        data.setUsername(player.getName());
        data.setLastSeen(System.currentTimeMillis());
        save(data);
    }

    public synchronized boolean save(PlayerData data) { return write(data); }

    public synchronized boolean saveBoth(PlayerData first, PlayerData second) {
        if (first.getUuid().equals(second.getUuid())) return save(first);
        File firstFile = fileFor(first.getUuid());
        File secondFile = fileFor(second.getUuid());
        File firstTemp = new File(dataFolder, first.getUuid() + ".transaction.tmp");
        File secondTemp = new File(dataFolder, second.getUuid() + ".transaction.tmp");
        try {
            writeYaml(first, firstTemp);
            writeYaml(second, secondTemp);
            moveAtomic(firstTemp, firstFile);
            moveAtomic(secondTemp, secondFile);
            return true;
        } catch (IOException failure) {
            try { Files.deleteIfExists(firstTemp.toPath()); } catch (IOException ignored) {}
            try { Files.deleteIfExists(secondTemp.toPath()); } catch (IOException ignored) {}
            plugin.getLogger().log(Level.SEVERE, "ERROR: Could not commit economy transaction.", failure);
            return false;
        }
    }

    private void moveAtomic(File source, File target) throws IOException {
        try { Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException atomicFailure) { Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    }

    private boolean write(PlayerData data) {
        File target = fileFor(data.getUuid());
        File temp = new File(dataFolder, data.getUuid() + ".tmp");
        try { writeYaml(data, temp); moveAtomic(temp, target); return true; }
        catch (IOException failure) { plugin.getLogger().log(Level.SEVERE, "ERROR: Could not save player data for " + data.getUuid() + ".", failure); return false; }
    }

    private void writeYaml(PlayerData data, File target) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", data.getUuid().toString());
        yaml.set("username", data.getUsername());
        yaml.set("first-join", data.getFirstJoin());
        yaml.set("last-seen", data.getLastSeen());
        yaml.set("last-ip", data.getLastIp());
        yaml.set("balance", data.getBalance());
        yaml.set("emerald-shards", data.getEmeraldShards());
        yaml.set("investment.amount", data.getInvestment());
        yaml.set("investment.earnings", data.getInvestmentEarnings());
        yaml.set("investment.auto-collect", data.isInvestmentAutoCollect());
        yaml.save(target);
    }

    public synchronized void shutdown() { for (PlayerData data : loaded.values()) save(data); loaded.clear(); }

    private File fileFor(UUID uuid) { return new File(dataFolder, uuid + ".yml"); }
}
