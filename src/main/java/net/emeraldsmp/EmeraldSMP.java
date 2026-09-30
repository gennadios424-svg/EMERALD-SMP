package net.emeraldsmp;

import net.emeraldsmp.commands.BalanceCommand;
import net.emeraldsmp.commands.EcoCommand;
import net.emeraldsmp.commands.EmeraldCommand;
import net.emeraldsmp.commands.PayCommand;
import net.emeraldsmp.data.PlayerDataManager;
import net.emeraldsmp.listeners.PlayerDataListener;
import net.emeraldsmp.managers.ConfigManager;
import net.emeraldsmp.managers.EconomyManager;
import net.emeraldsmp.shop.ShopCommand;
import net.emeraldsmp.shop.ShopListener;
import net.emeraldsmp.shop.ShopManager;
import net.emeraldsmp.utils.MessageService;
import net.emeraldsmp.worth.WorthCommand;
import net.emeraldsmp.worth.WorthListener;
import net.emeraldsmp.worth.WorthManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

public final class EmeraldSMP extends JavaPlugin {
    private ConfigManager configManager;
    private MessageService messageService;
    private PlayerDataManager playerDataManager;
    private EconomyManager economyManager;
    private ShopManager shopManager;
    private WorthManager worthManager;
    private WorthListener worthListener;

    @Override public void onEnable(){try{
        configManager=new ConfigManager(this);
        configManager.load();
        messageService=new MessageService(this);
        playerDataManager=new PlayerDataManager(this);
        playerDataManager.initialize();
        economyManager=new EconomyManager(this);
        shopManager=new ShopManager(this);
        worthManager=new WorthManager(this);
        worthManager.load();

        EmeraldCommand emerald=new EmeraldCommand(this);
        register("emerald",emerald,emerald);
        register("balance",new BalanceCommand(this),null);
        register("pay",new PayCommand(this),null);
        EcoCommand eco=new EcoCommand(this);
        register("eco",eco,eco);
        register("shop",new ShopCommand(this),null);
        register("worth",new WorthCommand(this),new WorthCommand(this));

        getServer().getPluginManager().registerEvents(new PlayerDataListener(playerDataManager),this);
        getServer().getPluginManager().registerEvents(new ShopListener(this),this);
        worthListener=new WorthListener(this);
        getServer().getPluginManager().registerEvents(worthListener,this);
        getLogger().info("EmeraldSMP has been enabled!");
        getLogger().info("Stage 2A worth database loaded: "+worthManager.all().size()+" supported items.");
    }catch(Exception ex){getLogger().log(Level.SEVERE,"ERROR: EmeraldSMP could not start safely.",ex);getServer().getPluginManager().disablePlugin(this);}}

    private void register(String n,org.bukkit.command.CommandExecutor e,org.bukkit.command.TabCompleter t){
        PluginCommand c=getCommand(n);
        if(c==null)throw new IllegalStateException("Command '"+n+"' is missing from plugin.yml");
        c.setExecutor(e);
        if(t!=null)c.setTabCompleter(t);
    }

    @Override public void onDisable(){if(playerDataManager!=null)playerDataManager.shutdown();getLogger().info("EmeraldSMP has been disabled.");}

    public void beginWorthSearch(Player p){if(worthListener!=null)worthListener.beginSearch(p);}
    public ConfigManager getConfigManager(){return configManager;}
    public MessageService getMessageService(){return messageService;}
    public PlayerDataManager getPlayerDataManager(){return playerDataManager;}
    public EconomyManager getEconomyManager(){return economyManager;}
    public ShopManager getShopManager(){return shopManager;}
    public WorthManager getWorthManager(){return worthManager;}
}
