package net.emeraldsmp;

import net.emeraldsmp.commands.*;
import net.emeraldsmp.auction.AuctionCommand;
import net.emeraldsmp.afk.AfkCommand;
import net.emeraldsmp.afk.AfkListener;
import net.emeraldsmp.afk.AfkManager;
import net.emeraldsmp.auction.AuctionManager;
import net.emeraldsmp.data.PlayerDataManager;
import net.emeraldsmp.listeners.PlayerDataListener;
import net.emeraldsmp.managers.ConfigManager;
import net.emeraldsmp.managers.EconomyManager;
import net.emeraldsmp.shop.*;
import net.emeraldsmp.utils.MessageService;
import net.emeraldsmp.worth.*;
import net.emeraldsmp.teams.*;
import net.emeraldsmp.tags.*;
import net.emeraldsmp.spawner.*;
import net.emeraldsmp.drill.*;
import net.emeraldsmp.crates.*;
import net.emeraldsmp.roles.*;
import net.emeraldsmp.kits.*;
import net.emeraldsmp.invest.*;
import net.emeraldsmp.staff.StaffCommand;
import net.emeraldsmp.anticheat.FlightAntiCheat;
import net.emeraldsmp.anticheat.MacroAntiCheat;
import net.emeraldsmp.anticheat.SusListCommand;
import net.emeraldsmp.anticheat.SusListManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

public final class EmeraldSMP extends JavaPlugin {
    private ConfigManager configManager; private MessageService messageService; private PlayerDataManager playerDataManager; private EconomyManager economyManager;
    private ShopManager shopManager; private CleanSellManager cleanSellManager; private WorthManager worthManager; private WorthListener worthListener; private RtpCommand rtpCommand; private ServerUI serverUI;
    private AuctionManager auctionManager; private HomeCommand homeCommand; private AfkManager afkManager; private TeamManager teamManager; private TagsManager tagsManager;
    private SpawnerManager spawnerManager; private DrillManager drillManager; private CrateManager crateManager; private LagCleaner lagCleaner; private RoleManager roleManager; private KitsManager kitsManager;
    private InvestmentManager investmentManager; private StaffCommand staffCommand; private SusListManager susListManager; private KeyAllManager keyAllManager;

    @Override public void onEnable() {
        try {
            configManager=new ConfigManager(this);configManager.load(); messageService=new MessageService(this);
            playerDataManager=new PlayerDataManager(this);playerDataManager.initialize(); economyManager=new EconomyManager(this); shopManager=new ShopManager(this); cleanSellManager=new CleanSellManager(this);
            worthManager=new WorthManager(this);worthManager.load(); afkManager=new AfkManager(this);
            teamManager=new TeamManager(this);teamManager.load(); tagsManager=new TagsManager(this);tagsManager.load();
            roleManager=new RoleManager(this);roleManager.load(); spawnerManager=new SpawnerManager(this);spawnerManager.load();
            drillManager=new DrillManager(this);drillManager.load(); crateManager=new CrateManager(this);crateManager.load();
            kitsManager=new KitsManager(this);kitsManager.loadPersisted(); investmentManager=new InvestmentManager(this); investmentManager.start();
            lagCleaner=new LagCleaner(this);lagCleaner.start();

            EmeraldCommand emerald=new EmeraldCommand(this);register("emerald",emerald,emerald);
            register("balance",new BalanceCommand(this),null);register("pay",new PayCommand(this),null);
            EcoCommand eco=new EcoCommand(this);register("eco",eco,eco);register("shop",new ShopCommand(this),null);register("sell",new SellCommand(this),null);
            WorthCommand worth=new WorthCommand(this);register("worth",worth,worth);rtpCommand=new RtpCommand(this);register("rtp",rtpCommand,null);
            CoinFlipCommand cf=new CoinFlipCommand(this);register("cf",cf,null);
            auctionManager=new AuctionManager(this);AuctionCommand ah=new AuctionCommand(this,auctionManager);register("ah",ah,null);
            homeCommand=new HomeCommand(this);register("sethome",homeCommand,homeCommand);register("home",homeCommand,homeCommand);register("delhome",homeCommand,homeCommand);
            register("afk",new AfkCommand(this),null);TeamCommand teams=new TeamCommand(this,teamManager);register("teams",teams,teams);
            TagsCommand tags=new TagsCommand(this,tagsManager);register("tags",tags,null);SpawnerCommand spawner=new SpawnerCommand(this,spawnerManager);register("spawner",spawner,spawner);
            DrillCommand drill=new DrillCommand(this,drillManager);register("drill",drill,drill);CrateCommand crates=new CrateCommand(crateManager);
            register("crates",crates,crates);register("crate",crates,crates);register("keyall",crates,crates);register("shardgainer",drill,drill);
            RolesCommand rolesCommand=new RolesCommand(this,roleManager);register("roles",rolesCommand,rolesCommand);KitsCommand kitsCommand=new KitsCommand(this);register("kits",kitsCommand,null);
            register("invest",new InvestCommand(this,investmentManager),null);
            staffCommand=new StaffCommand(this);
            susListManager=new SusListManager(this);
            register("vanish",staffCommand,null); register("fly",staffCommand,null); register("tp",staffCommand,null);
            SusListCommand susListCommand=new SusListCommand(this,susListManager); register("suslist",susListCommand,susListCommand);
            register("tphere",staffCommand,null); register("kick",staffCommand,null); register("ban",staffCommand,null); register("ipban",staffCommand,null); register("unban",staffCommand,null); register("ipunban",staffCommand,null);

            getServer().getPluginManager().registerEvents(new PlayerDataListener(playerDataManager),this);getServer().getPluginManager().registerEvents(new ShopListener(this),this);
            worthListener=new WorthListener(this);getServer().getPluginManager().registerEvents(worthListener,this);getServer().getPluginManager().registerEvents(rtpCommand,this);
            getServer().getPluginManager().registerEvents(cf,this);getServer().getPluginManager().registerEvents(ah,this);
            getServer().getPluginManager().registerEvents(homeCommand,this);getServer().getPluginManager().registerEvents(new AfkListener(afkManager),this);
            getServer().getPluginManager().registerEvents(teams,this);getServer().getPluginManager().registerEvents(tags,this);getServer().getPluginManager().registerEvents(spawnerManager,this);
            getServer().getPluginManager().registerEvents(drillManager,this);getServer().getPluginManager().registerEvents(crateManager,this);getServer().getPluginManager().registerEvents(new CrateKeyGuard(this),this);
            getServer().getPluginManager().registerEvents(new RolePlayerListener(this),this);getServer().getPluginManager().registerEvents(rolesCommand,this);
            getServer().getPluginManager().registerEvents(new RoleChatListener(this),this);getServer().getPluginManager().registerEvents(kitsManager,this);getServer().getPluginManager().registerEvents(investmentManager,this); getServer().getPluginManager().registerEvents(staffCommand,this);
            getServer().getPluginManager().registerEvents(new FlightAntiCheat(this,susListManager),this); getServer().getPluginManager().registerEvents(new MacroAntiCheat(this),this); getServer().getPluginManager().registerEvents(susListCommand,this);
            spawnerManager.start();afkManager.start();serverUI=new ServerUI(this);getServer().getPluginManager().registerEvents(serverUI,this);serverUI.start();keyAllManager=new KeyAllManager(this);keyAllManager.load();getServer().getPluginManager().registerEvents(keyAllManager,this);keyAllManager.start();
            getLogger().info("EmeraldSMP has been enabled!");getLogger().info("Worth database loaded: "+worthManager.all().size()+" supported items.");
        } catch(Exception ex){getLogger().log(Level.SEVERE,"ERROR: EmeraldSMP could not start safely.",ex);getServer().getPluginManager().disablePlugin(this);}
    }
    private void register(String n,org.bukkit.command.CommandExecutor e,org.bukkit.command.TabCompleter t){PluginCommand c=getCommand(n);if(c==null)throw new IllegalStateException("Command '"+n+"' is missing from plugin.yml");c.setExecutor(e);if(t!=null)c.setTabCompleter(t);}
    @Override public void onDisable(){if(keyAllManager!=null)keyAllManager.stop();if(serverUI!=null)serverUI.stop();if(afkManager!=null)afkManager.stop();if(cleanSellManager!=null)cleanSellManager.disable();if(spawnerManager!=null)spawnerManager.stop();if(drillManager!=null)drillManager.stop();if(crateManager!=null)crateManager.stop();if(investmentManager!=null)investmentManager.stop();if(teamManager!=null)teamManager.save();if(tagsManager!=null)tagsManager.save();if(roleManager!=null)roleManager.save();if(kitsManager!=null)kitsManager.save();if(playerDataManager!=null)playerDataManager.shutdown();getLogger().info("EmeraldSMP has been disabled.");}
    public void beginWorthSearch(Player p){if(worthListener!=null)worthListener.beginSearch(p);}
    public ConfigManager getConfigManager(){return configManager;} public MessageService getMessageService(){return messageService;} public PlayerDataManager getPlayerDataManager(){return playerDataManager;}
    public EconomyManager getEconomyManager(){return economyManager;} public ShopManager getShopManager(){return shopManager;} public CleanSellManager getCleanSellManager(){return cleanSellManager;} public WorthManager getWorthManager(){return worthManager;} public AuctionManager getAuctionManager(){return auctionManager;}
    public AfkManager getAfkManager(){return afkManager;} public TeamManager getTeamManager(){return teamManager;} public TagsManager getTagsManager(){return tagsManager;} public SpawnerManager getSpawnerManager(){return spawnerManager;}
    public DrillManager getDrillManager(){return drillManager;} public CrateManager getCrateManager(){return crateManager;} public RoleManager getRoleManager(){return roleManager;} public KitsManager getKitsManager(){return kitsManager;} public ServerUI getServerUI(){return serverUI;} public InvestmentManager getInvestmentManager(){return investmentManager;} public SusListManager getSusListManager(){return susListManager;} public KeyAllManager getKeyAllManager(){return keyAllManager;}
}
