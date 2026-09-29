package net.emeraldsmp;

import net.emeraldsmp.commands.EmeraldCommand;
import net.emeraldsmp.data.PlayerDataManager;
import net.emeraldsmp.listeners.PlayerDataListener;
import net.emeraldsmp.managers.ConfigManager;
import net.emeraldsmp.utils.MessageService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class EmeraldSMP extends JavaPlugin {
    private ConfigManager configManager;
    private MessageService messageService;
    private PlayerDataManager playerDataManager;

    @Override
    public void onEnable() {
        try {
            configManager = new ConfigManager(this);
            configManager.load();
            messageService = new MessageService(this);
            playerDataManager = new PlayerDataManager(this);
            playerDataManager.initialize();

            EmeraldCommand emeraldCommand = new EmeraldCommand(this);
            PluginCommand command = getCommand("emerald");
            if (command == null) {
                throw new IllegalStateException("Command 'emerald' is missing from plugin.yml");
            }
            command.setExecutor(emeraldCommand);
            command.setTabCompleter(emeraldCommand);

            getServer().getPluginManager().registerEvents(new PlayerDataListener(playerDataManager), this);
            getLogger().info("EmeraldSMP has been enabled!");
            getLogger().info("Foundation loaded successfully.");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "ERROR: EmeraldSMP could not start safely.", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.shutdown();
        }
        getLogger().info("EmeraldSMP has been disabled.");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public MessageService getMessageService() { return messageService; }
}
