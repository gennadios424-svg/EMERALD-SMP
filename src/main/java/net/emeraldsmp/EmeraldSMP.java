package net.emeraldsmp;

import net.emeraldsmp.commands.*;
import net.emeraldsmp.auction.AuctionCommand;
import net.emeraldsmp.auction.AuctionManager;
import net.emeraldsmp.data.PlayerDataManager;
import net.emeraldsmp.listeners.PlayerDataListener;
import net.emeraldsmp.managers.ConfigManager;
import net.emeraldsmp.managers.EconomyManager;
import net.emeraldsmp.shop.*;
import net.emeraldsmp.utils.MessageService;
import net.emeraldsmp.worth.*;
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
    private RtpCommand rtpCommand;
    private ServerUI serverUI;
    private AuctionManager auctionManager;

    @Override public void onEnable() {
        try {
            configManager=new ConfigManager(this);configManager.load();
            messageService=new MessageService(this);
            playerDataManager=new PlayerDataManager(this);playerDataManager.initialize();
            economyManager=new EconomyManager(this);
            shopManager=new ShopManager(this);
            worthManager=new WorthManager(this);worthManager.load();

            EmeraldCommand emerald=new EmeraldCommand(this);register("emerald",emerald,emerald);
            register("balance",new BalanceCommand(this),null);
            register("pay",new PayCommand(this),null);
            EcoCommand eco=new EcoCommand(this);register("eco",eco,eco);
            register("shop",new ShopCommand(this),null);
            register("sell",new SellCommand(this),null);
            WorthCommand worth=new WorthCommand(this);register("worth",worth,worth);
            rtpCommand=new RtpCommand(this);register("rtp",rtpCommand,null);
            OrderCommand order=new OrderCommand(this);register("order",order,null);
            CoinFlipCommand cf=new CoinFlipCommand(this);register("cf",cf,null);
            auctionManager=new AuctionManager(this);
            AuctionCommand ah=new AuctionCommand(this,auctionManager);register("ah",ah,null);

            getServer().getPluginManager().registerEvents(new PlayerDataListener(playerDataManager),this);
            getServer().getPluginManager().registerEvents(new ShopListener(this),this);
            worthListener=new WorthListener(this);getServer().getPluginManager().registerEvents(worthListener,this);
            getServer().getPluginManager().registerEvents(rtpCommand,this);
            getServer().getPluginManager().registerEvents(order,this);
            getServer().getPluginManager().registerEvents(cf,this);
            getServer().getPluginManager().registerEvents(ah,this);

            serverUI=new ServerUI(this);
            getServer().getPluginManager().registerEvents(serverUI,this);
            serverUI.start();

            getLogger().info("EmeraldSMP has been enabled!");
            getLogger().info("Worth database loaded: "+worthManager.all().size()+" supported items.");
        } catch(Exception ex) {
            getLogger().log(Level.SEVERE,"ERROR: EmeraldSMP could not start safely.",ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void register(String n,org.bukkit.command.CommandExecutor e,org.bukkit.command.TabCompleter t) {
        PluginCommand c=getCommand(n);
        if(c==null)throw new IllegalStateException("Command '"+n+"' is missing from plugin.yml");
        c.setExecutor(e);if(t!=null)c.setTabCompleter(t);
    }

    @Override public void onDisable() {
        if(serverUI!=null) serverUI.stop();
        if(playerDataManager!=null)playerDataManager.shutdown();
        getLogger().info("EmeraldSMP has been disabled.");
    }

    public void beginWorthSearch(Player p){if(worthListener!=null)worthListener.beginSearch(p);}
    public ConfigManager getConfigManager(){return configManager;}
    public MessageService getMessageService(){return messageService;}
    public PlayerDataManager getPlayerDataManager(){return playerDataManager;}
    public EconomyManager getEconomyManager(){return economyManager;}
    public ShopManager getShopManager(){return shopManager;}
    public WorthManager getWorthManager(){return worthManager;}
    public AuctionManager getAuctionManager(){return auctionManager;}
}
