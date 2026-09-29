package net.emeraldsmp;

import net.emeraldsmp.commands.BalanceCommand;
import net.emeraldsmp.commands.EcoCommand;
import net.emeraldsmp.commands.EmeraldCommand;
import net.emeraldsmp.commands.PayCommand;
import net.emeraldsmp.data.PlayerDataManager;
import net.emeraldsmp.listeners.PlayerDataListener;
import net.emeraldsmp.managers.ConfigManager;
import net.emeraldsmp.managers.EconomyManager;
import net.emeraldsmp.utils.MessageService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

public final class EmeraldSMP extends JavaPlugin {
    private ConfigManager configManager;
    private MessageService messageService;
    private PlayerDataManager playerDataManager;
    private EconomyManager economyManager;

    @Override
    public void onEnable() {
        try {
            configManager = new ConfigManager(this);
            configManager.load();
            messageService = new MessageService(this);
            playerDataManager = new PlayerDataManager(this);
            playerDataManager.initialize();
            economyManager = new EconomyManager(this);

            EmeraldCommand emeraldCommand = new EmeraldCommand(this);
            register("emerald", emeraldCommand, emeraldCommand);
            register("balance", new BalanceCommand(this), null);
            register("bal", new BalanceCommand(this), null);
            register("money", new BalanceCommand(this), null);
            register("pay", new PayCommand(this), null);
            EcoCommand ecoCommand = new EcoCommand(this);
            register("eco", ecoCommand, ecoCommand);

            getServer().getPluginManager().registerEvents(new PlayerDataListener(playerDataManager), this);
            getLogger().info("EmeraldSMP has been enabled!");
            getLogger().info("Stage 2 economy foundation loaded successfully.");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "ERROR: EmeraldSMP could not start safely.", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void register(String name, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException("Command '" + name + "' is missing from plugin.yml");
        command.setExecutor(executor);
        if (completer != null) command.setTabCompleter(completer);
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) playerDataManager.shutdown();
        getLogger().info("EmeraldSMP has been disabled.");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public MessageService getMessageService() { return messageService; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
}