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

    public void initialize() {
        dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create player-data directory");
        }
    }

    public synchronized PlayerData loadOrCreate(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = loaded.get(uuid);
        if (data != null) {
            data.setUsername(player.getName());
            data.setLastSeen(System.currentTimeMillis());
            save(data);
            return data;
        }

        File file = fileFor(uuid);
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            long firstJoin = yaml.getLong("first-join", System.currentTimeMillis());
            data = new PlayerData(uuid, player.getName(), firstJoin, System.currentTimeMillis());
        } else {
            long now = System.currentTimeMillis();
            data = new PlayerData(uuid, player.getName(), now, now);
        }

        loaded.put(uuid, data);
        save(data);
        return data;
    }

    public synchronized void markSeen(Player player) {
        PlayerData data = loadOrCreate(player);
        data.setUsername(player.getName());
        data.setLastSeen(System.currentTimeMillis());
        save(data);
    }

    public synchronized void save(PlayerData data) {
        File target = fileFor(data.getUuid());
        File temp = new File(dataFolder, data.getUuid() + ".yml.tmp");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", data.getUuid().toString());
        yaml.set("username", data.getUsername());
        yaml.set("first-join", data.getFirstJoin());
        yaml.set("last-seen", data.getLastSeen());

        try {
            yaml.save(temp);
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            plugin.getLogger().log(Level.SEVERE, "ERROR: Could not save player data for " + data.getUuid() + ".", failure);
        }
    }

    public synchronized void shutdown() {
        for (PlayerData data : loaded.values()) save(data);
        loaded.clear();
    }

    private File fileFor(UUID uuid) {
        return new File(dataFolder, uuid + ".yml");
    }
}
