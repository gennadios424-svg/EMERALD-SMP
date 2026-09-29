package net.emeraldsmp.managers;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.logging.Level;

public final class ConfigManager {
    private final EmeraldSMP plugin;
    public ConfigManager(EmeraldSMP plugin) { this.plugin = plugin; }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
    }

    public FileConfiguration getConfig() { return plugin.getConfig(); }

    public boolean reload() {
        try {
            plugin.reloadConfig();
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "ERROR: Could not reload configuration.", exception);
            return false;
        }
    }
}
