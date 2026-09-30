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

    public synchronized PlayerData loadOrCreate(Player player) {
        return loadOrCreate(player.getUniqueId(), player.getName());
    }

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
            data = new PlayerData(uuid, username == null ? yaml.getString("username", "Unknown") : username, firstJoin, System.currentTimeMillis(), balance, shards);
        } else {
            long now = System.currentTimeMillis();
            long starting = Math.max(0L, plugin.getConfigManager().getConfig().getLong("economy.starting-balance", 0L));
            data = new PlayerData(uuid, username == null ? "Unknown" : username, now, now, starting, 0L);
        }

        loaded.put(uuid, data);
        save(data);
        return data;
    }

    public synchronized PlayerData getLoaded(UUID uuid) { return loaded.get(uuid); }

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
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean write(PlayerData data) {
        File target = fileFor(data.getUuid());
        File temp = new File(dataFolder, data.getUuid() + ".tmp");
        try {
            writeYaml(data, temp);
            moveAtomic(temp, target);
            return true;
        } catch (IOException failure) {
            plugin.getLogger().log(Level.SEVERE, "ERROR: Could not save player data for " + data.getUuid() + ".", failure);
            return false;
        }
    }

    private void writeYaml(PlayerData data, File target) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", data.getUuid().toString());
        yaml.set("username", data.getUsername());
        yaml.set("first-join", data.getFirstJoin());
        yaml.set("last-seen", data.getLastSeen());
        yaml.set("balance", data.getBalance());
        yaml.set("emerald-shards", data.getEmeraldShards());
        yaml.save(target);
    }

    public synchronized void shutdown() {
        for (PlayerData data : loaded.values()) save(data);
        loaded.clear();
    }

    private File fileFor(UUID uuid) { return new File(dataFolder, uuid + ".yml"); }
}
