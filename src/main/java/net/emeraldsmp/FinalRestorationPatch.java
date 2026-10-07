package net.emeraldsmp;
import net.emeraldsmp.afk.AfkManager;
import net.emeraldsmp.crates.CrateManager;
import net.emeraldsmp.roles.RoleManager;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.*;
import org.bukkit.scheduler.BukkitTask;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FinalRestorationPatch {
 private static final Map<UUID,Long> afkRewards=new ConcurrentHashMap<>();
 private static final Set<UUID> joined=ConcurrentHashMap.newKeySet();
 public static void install(EmeraldSMP p){
  if(p.getServerUI()!=null){try{p.getServerUI().stop();}catch(Throwable ignored){} org.bukkit.event.HandlerList.unregisterAll(p.getServerUI());}
  PatchListener l=new PatchListener(p);p.getServer().getPluginManager().registerEvents(l,p);
  p.getServer().getScheduler().runTaskTimer(p,()->rewardAfk(p),20L,20L);
 }
 private static void rewardAfk(EmeraldSMP p){
  AfkManager a=p.getAfkManager();if(a==null)return;long now=System.currentTimeMillis();
  for(Player x:Bukkit.getOnlinePlayers())if(a.isAfk(x.getUniqueId())){
   long last=afkRewards.getOrDefault(x.getUniqueId(),now);if(now-last<300000L)continue;
   long n=(now-last)/300000L;afkRewards.put(x.getUniqueId(),last+n*300000L);
   p.getPlayerDataManager().setEmeraldShards(x.getUniqueId(),p.getPlayerDataManager().getEmeraldShards(x.getUniqueId())+3L*n);
   x.sendMessage("§a💚 AFK reward: §b+"+(3L*n)+" Emerald Shards");
  }
 }
 static final class PatchListener implements Listener,CommandExecutor,TabCompleter{
  final EmeraldSMP p;
  PatchListener(EmeraldSMP p){this.p=p;if(p.getCommand("role")!=null){p.getCommand("role").setExecutor(this);p.getCommand("role").setTabCompleter(this);}if(p.getCommand("banlist")!=null)p.getCommand("banlist").setExecutor(this);}
  @EventHandler(priority=EventPriority.HIGHEST) public void join(PlayerJoinEvent e){Player x=e.getPlayer();joined.add(x.getUniqueId());p.getRoleManager().ensureMember(x);}
  private void ground(Player x){String wn=p.getConfig().getString("spawn.world","");World w=wn.isBlank()?x.getWorld():Bukkit.getWorld(wn);if(w==null)w=x.getWorld();Location l=new Location(w,p.getConfig().getDouble("spawn.x",.5),p.getConfig().getDouble("spawn.y",100),p.getConfig().getDouble("spawn.z",.5),(float)p.getConfig().getDouble("spawn.yaw",0),(float)p.getConfig().getDouble("spawn.pitch",0));x.teleport(l);x.setFallDistance(0);try{x.setFlying(false);}catch(Throwable ignored){}}
  @EventHandler(priority=EventPriority.HIGHEST) public void crate(PlayerInteractEvent e){if(e.getClickedBlock()==null)return;CrateManager c=p.getCrateManager();if(c==null||!c.isCrate(e.getClickedBlock()))return;e.setCancelled(true);try{String t=(String)call(c,"type",new Class[]{org.bukkit.block.Block.class},e.getClickedBlock());if(e.getAction()==Action.LEFT_CLICK_BLOCK)call(c,"openPreview",new Class[]{Player.class,String.class},e.getPlayer(),t);else if(e.getAction()==Action.RIGHT_CLICK_BLOCK)call(c,"open",new Class[]{Player.class,String.class},e.getPlayer(),t);}catch(Throwable ignored){}}
  @EventHandler public void quit(PlayerQuitEvent e){joined.remove(e.getPlayer().getUniqueId());afkRewards.remove(e.getPlayer().getUniqueId());}
  @Override public boolean onCommand(CommandSender s,Command c,String l,String[] a){if(c.getName().equalsIgnoreCase("role"))return role(s,a);if(c.getName().equalsIgnoreCase("banlist"))return banlist(s);return true;}
  private boolean role(CommandSender s,String[] a){if(!(s instanceof Player x))return true;RoleManager.Role r=p.getRoleManager().get(x);if(r.weight()<RoleManager.Role.MOD.weight()){x.sendMessage("§cYou do not have permission.");return true;}if(a.length!=2){x.sendMessage("§cUsage: /role <player> <role>");return true;}Player t=Bukkit.getPlayerExact(a[0]);if(t==null){x.sendMessage("§cPlayer must be online.");return true;}RoleManager.Role nr;try{nr=RoleManager.Role.valueOf(a[1].toUpperCase(Locale.ROOT));}catch(Exception ex){x.sendMessage("§cInvalid role.");return true;}if(t.getUniqueId().equals(x.getUniqueId())||nr.weight()>=r.weight()||!p.getRoleManager().canPunish(x,t)){x.sendMessage("§cYou cannot assign that role.");return true;}p.getRoleManager().set(t,nr);x.sendMessage("§aRole updated: §f"+t.getName()+" §7→ §f"+nr.label());return true;}
  private boolean banlist(CommandSender s){if(!(s instanceof Player x)||p.getRoleManager().get(x).weight()<RoleManager.Role.MOD.weight()){s.sendMessage("§cYou do not have permission.");return true;}Inventory inv=Bukkit.createInventory(null,54,"§2§l💚 BAN LIST");int slot=0;for(OfflinePlayer op:Bukkit.getOfflinePlayers()){if(slot>=45)break;ItemStack i=new ItemStack(Material.PLAYER_HEAD);ItemMeta m=i.getItemMeta();if(m!=null){m.setDisplayName("§c🔨 "+(op.getName()==null?"Unknown":op.getName()));m.setLore(List.of("§7Click for ban details","§8IP/UUID/network data is never shown."));i.setItemMeta(m);}inv.setItem(slot++,i);}x.openInventory(inv);return true;}
  @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){if(c.getName().equalsIgnoreCase("role")&&a.length==2)return Arrays.stream(RoleManager.Role.values()).map(Enum::name).filter(v->v.toLowerCase().startsWith(a[1].toLowerCase())).toList();return List.of();}
  private Object call(Object o,String n,Class<?>[] t,Object...a)throws Exception{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);return m.invoke(o,a);}
 }
 static final class CleanUI implements Listener{
  final EmeraldSMP p;BukkitTask task;CleanUI(EmeraldSMP p){this.p=p;}void start(){task=Bukkit.getScheduler().runTaskTimer(p,this::updateAll,1L,20L);}void stop(){if(task!=null)task.cancel();}
  @EventHandler public void join(PlayerJoinEvent e){update(e.getPlayer());}void updateAll(){for(Player x:Bukkit.getOnlinePlayers())update(x);}
  void update(Player x){RoleManager.Role r=p.getRoleManager().get(x);String team="";try{if(p.getTeamManager()!=null)team=p.getTeamManager().tag(x.getUniqueId());}catch(Throwable ignored){}String tn=team==null||team.isBlank()?"":" §8• §a"+team;x.playerListName(net.kyori.adventure.text.Component.text(r.icon()+" "+r.label()+tn+" §f"+x.getName()+" §8• §7"+x.getPing()+"ms"));x.setPlayerListOrder(r.weight()*10000);Scoreboard s=Bukkit.getScoreboardManager().getNewScoreboard();Objective o=s.registerNewObjective("emerald","dummy","§a§l💚 EMERALD SMP");o.setDisplaySlot(DisplaySlot.SIDEBAR);int n=8;add(o,"§7Rank",n--);add(o,r.color()+r.label(),n--);add(o,"§7",n--);add(o,"§6§l💰 MONEY",n--);add(o,"§f"+p.getEconomyManager().format(p.getEconomyManager().getBalance(x.getUniqueId())),n--);add(o,"§8",n--);add(o,"§b§l💎 SHARDS",n--);add(o,"§f"+p.getPlayerDataManager().getEmeraldShards(x.getUniqueId()),n--);add(o,"§8━━━━━━━━━━━━",n--);add(o,"§7s1.seranodes.com:25638",n);x.setScoreboard(s);}
  void add(Objective o,String text,int score){o.getScore(text).setScore(score);}
 }
}