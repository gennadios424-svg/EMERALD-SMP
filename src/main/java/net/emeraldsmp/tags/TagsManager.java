package net.emeraldsmp.tags;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class TagsManager {
    public static final List<String> TAGS = List.of("EMERALD","GG","GREEN","IDK","C00L");
    public static final long COST = 500L;
    private final EmeraldSMP plugin;
    private final File file;
    private final Map<UUID, LinkedHashSet<String>> owned = new HashMap<>();
    private final Map<UUID, String> active = new HashMap<>();

    public TagsManager(EmeraldSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tags.yml");
    }

    public synchronized void load() {
        owned.clear(); active.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                LinkedHashSet<String> set = new LinkedHashSet<>();
                for (String tag : yaml.getStringList(key + ".owned")) if (TAGS.contains(tag)) set.add(tag);
                owned.put(uuid, set);
                String current = yaml.getString(key + ".active");
                if (current != null && set.contains(current)) active.put(uuid, current);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, LinkedHashSet<String>> e : owned.entrySet()) {
            yaml.set(e.getKey() + ".owned", new ArrayList<>(e.getValue()));
            if (active.containsKey(e.getKey())) yaml.set(e.getKey() + ".active", active.get(e.getKey()));
        }
        try { yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Could not save tags.yml: " + ex.getMessage()); }
    }

    public synchronized boolean owns(UUID uuid, String tag) {
        return owned.getOrDefault(uuid, new LinkedHashSet<>()).contains(tag);
    }

    public synchronized boolean buy(UUID uuid, String tag) {
        if (!TAGS.contains(tag) || owns(uuid, tag)) return false;
        if (!plugin.getPlayerDataManager().withdrawEmeraldShards(uuid, COST)) return false;
        owned.computeIfAbsent(uuid, k -> new LinkedHashSet<>()).add(tag);
        save();
        return true;
    }

    public synchronized boolean select(UUID uuid, String tag) {
        if (!owns(uuid, tag)) return false;
        active.put(uuid, tag);
        save();
        return true;
    }

    public synchronized String active(UUID uuid) { return active.getOrDefault(uuid, ""); }
    public synchronized Set<String> owned(UUID uuid) { return Collections.unmodifiableSet(owned.getOrDefault(uuid, new LinkedHashSet<>())); }
}
