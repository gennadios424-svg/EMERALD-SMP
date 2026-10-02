package net.emeraldsmp.anticheat;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.staff.StaffCommand;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import java.text.SimpleDateFormat;
import java.util.*;

public final class SusListCommand implements CommandExecutor,TabCompleter,Listener{
 private static final String LIST="§8§lEmerald AntiCheat • Suslist",DETAIL="§8§lEmerald AntiCheat • Player";
 private final EmeraldSMP plugin; private final SusListManager manager; private final Map<UUID,WatchState>watches=new HashMap<>();
 public SusListCommand(EmeraldSMP p,SusListManager m){plugin=p;manager=m;}
 private boolean staff(Player p){return plugin.getRoleManager().isStaff(p);}
 public boolean onCommand(CommandSender s,Command c,String l,String[]a){
  if(!(s instanceof Player p)||!staff(p)){s.sendMessage("§cOnly Emerald SMP staff can use this command.");return true;}
  if(c.getName().equalsIgnoreCase("suswatch")){if(a.length==1&&a[0].equalsIgnoreCase("leave")){stopWatch(p);return true;}if(a.length==1){startWatch(p,a[0]);return true;}p.sendMessage("§eUsage: /suswatch <player> or /suswatch leave");return true;}
  if(a.length==0){list(p);return true;}
  if(a[0].equalsIgnoreCase("clear")){manager.clear();p.sendMessage("§a§lSUSLIST §8» §fCleared.");return true;}
  if(a.length>=2&&a[0].equalsIgnoreCase("watch")){startWatch(p,a[1]);return true;}
  if(a.length>=2&&a[0].equalsIgnoreCase("tp")){SusListManager.Suspect x=find(a[1]);if(x!=null)p.teleport(x.location());else p.sendMessage("§cPlayer not found on suslist.");return true;}
  if(a.length>=2&&a[0].equalsIgnoreCase("remove")){SusListManager.Suspect x=find(a[1]);if(x!=null)manager.remove(x.uuid());return true;}
  manager.sendList(p);return true;
 }
 private SusListManager.Suspect find(String n){for(var s:manager.all())if(s.name().equalsIgnoreCase(n))return s;return null;}
 private void list(Player p){
  Inventory i=Bukkit.createInventory(null,54,LIST);ItemStack f=button(Material.GRAY_STAINED_GLASS_PANE," ",List.of());for(int n=0;n<54;n++)i.setItem(n,f);
  int slot=10;for(var s:manager.all()){if(slot>43)break;while(slot%9==0||slot%9==8)slot++;ItemStack h=new ItemStack(Material.PLAYER_HEAD);SkullMeta m=(SkullMeta)h.getItemMeta();if(m!=null){Player o=Bukkit.getPlayer(s.uuid());if(o!=null)m.setOwningPlayer(o);m.setDisplayName("§c§l⚠ "+s.name());m.setLore(List.of("§7Detection: §f"+s.reason(),"§7Detections: §c"+s.flags(),"§7Confidence: §e"+s.suspicion()+"%","§7Last: §f"+time(s.lastDetectedAt()),"","§e▶ Click to inspect"));h.setItemMeta(m);}i.setItem(slot++,h);}
  i.setItem(49,button(Material.COMPASS,"§a§l🛡 SUSPICIOUS PLAYERS",List.of("§7Detection is recorded before punishment","§f"+manager.all().size()+" §7current suspect(s)")));p.openInventory(i);
 }
 private void detail(Player p,SusListManager.Suspect s){
  Inventory i=Bukkit.createInventory(null,36,DETAIL);
  i.setItem(4,button(Material.PLAYER_HEAD,"§c§l⚠ "+s.name(),List.of("§7Detection: §f"+s.reason(),"§7Detections: §c"+s.flags(),"§7Confidence: §e"+s.suspicion()+"%","§7First: §f"+time(s.firstDetectedAt()),"§7Last: §f"+time(s.lastDetectedAt()))));
  i.setItem(20,button(Material.ENDER_EYE,"§a§l👁 VANISH & WATCH",List.of("§7Use existing /vanish system","§7Never enter spectator mode")));
  i.setItem(22,button(Material.COMPASS,"§a§l📍 TELEPORT TO PLAYER",List.of("§7Teleport to the suspect")));
  i.setItem(24,button(Material.CHEST,"§b§l🎒 INSPECT INVENTORY",List.of("§7Open the player's inventory")));
  i.setItem(25,button(Material.BOOK,"§f§l📋 DETECTION DETAILS",List.of("§7View recorded detection types")));
  i.setItem(29,button(Material.BARRIER,"§c§l🧹 CLEAR SUSPICION",List.of("§7Remove this suspect")));
  i.setItem(31,button(Material.REDSTONE,"§c§l⏹ STOP WATCHING",List.of("§7Restore your previous staff state")));
  i.setItem(35,button(Material.ARROW,"§e§l← BACK",List.of("§7Return to suspects")));p.openInventory(i);
 }
 private void startWatch(Player p,String name){
  SusListManager.Suspect s=find(name);Player target=s==null?Bukkit.getPlayerExact(name):Bukkit.getPlayer(s.uuid());
  if(target==null){p.sendMessage("§cPlayer not found or offline.");return;}if(target.equals(p)){p.sendMessage("§cYou cannot watch yourself.");return;}
  stopWatchIfActive(p);
  StaffCommand sc=plugin.getStaffCommand();boolean wasVanished=sc!=null&&sc.isVanished(p);
  watches.put(p.getUniqueId(),new WatchState(p.getLocation().clone(),p.getGameMode(),p.getAllowFlight(),p.isFlying(),wasVanished));
  if(!wasVanished)Bukkit.dispatchCommand(p,"vanish");
  Location watch=safeWatchLocation(target);
  if(watch==null){watches.remove(p.getUniqueId());if(!wasVanished)Bukkit.dispatchCommand(p,"vanish");p.sendMessage("§cCould not find a safe watch position.");return;}
  p.teleport(watch);p.sendMessage("§a§l👁 VANISH & WATCH §8» §fWatching §a"+target.getName()+"§f. Use §c/suswatch leave §fto stop.");
 }
 private void stopWatchIfActive(Player p){if(watches.containsKey(p.getUniqueId()))stopWatch(p);}
 private Location safeWatchLocation(Player t){
  Location b=t.getLocation().clone();double[][]o={{6,2,0},{-6,2,0},{0,2,6},{0,2,-6},{4,3,4},{-4,3,-4}};
  for(double[]x:o){Location q=b.clone().add(x[0],x[1],x[2]);if(!q.getChunk().isLoaded())q.getChunk().load();if(q.getBlock().isPassable()&&q.clone().add(0,1,0).getBlock().isPassable())return q.setDirection(b.toVector().subtract(q.toVector()));}return null;
 }
 private void stopWatch(Player p){
  WatchState w=watches.remove(p.getUniqueId());if(w==null){p.sendMessage("§7You are not currently watching a suspect.");return;}
  p.teleport(w.location);p.setGameMode(w.mode);p.setAllowFlight(w.flight);p.setFlying(w.flight&&w.flying);
  StaffCommand sc=plugin.getStaffCommand();boolean now=sc!=null&&sc.isVanished(p);if(now!=w.wasVanished)Bukkit.dispatchCommand(p,"vanish");
  p.sendMessage("§a§l⏹ STOP WATCHING §8» §fPrevious staff state restored.");
 }
 private String time(long x){return x<=0?"unknown":new SimpleDateFormat("HH:mm:ss").format(new Date(x));}
 @EventHandler public void click(InventoryClickEvent e){
  if(!(e.getWhoClicked() instanceof Player p)||!staff(p))return;String t=ChatColor.stripColor(e.getView().getTitle());if(!t.equals(ChatColor.stripColor(LIST))&&!t.equals(ChatColor.stripColor(DETAIL)))return;e.setCancelled(true);int slot=e.getRawSlot();
  if(t.equals(ChatColor.stripColor(LIST))){ItemStack x=e.getCurrentItem();if(x==null||x.getType()!=Material.PLAYER_HEAD||!x.hasItemMeta())return;String n=ChatColor.stripColor(x.getItemMeta().getDisplayName()).replace("⚠","").trim();var s=find(n);if(s!=null)detail(p,s);return;}
  ItemStack h=e.getView().getTopInventory().getItem(4);if(h==null||!h.hasItemMeta())return;String n=ChatColor.stripColor(h.getItemMeta().getDisplayName()).replace("⚠","").trim();var s=find(n);if(s==null)return;
  switch(slot){case 20->startWatch(p,s.name());case 22->p.teleport(s.location());case 24->{Player o=Bukkit.getPlayer(s.uuid());if(o==null)p.sendMessage("§cThat player is offline.");else p.openInventory(o.getInventory());}case 25->p.sendMessage("§a§lDETECTION DETAILS §8» §f"+s.violations());case 29->{manager.remove(s.uuid());list(p);}case 31->stopWatch(p);case 35->list(p);default->{}}}
 @EventHandler public void quit(PlayerQuitEvent e){watches.remove(e.getPlayer().getUniqueId());}
 public List<String> onTabComplete(CommandSender s,Command c,String l,String[]a){if(a.length==2&&(a[0].equalsIgnoreCase("tp")||a[0].equalsIgnoreCase("remove")||a[0].equalsIgnoreCase("watch"))||c.getName().equalsIgnoreCase("suswatch")&&a.length==1){String q=a[a.length-1].toLowerCase(Locale.ROOT);List<String>x=new ArrayList<>();for(var z:manager.all())if(z.name().toLowerCase(Locale.ROOT).startsWith(q))x.add(z.name());if(c.getName().equalsIgnoreCase("suswatch"))x.add("leave");return x;}return List.of("watch","tp","remove","clear");}
 private static ItemStack button(Material m,String n,List<String>l){ItemStack i=new ItemStack(m);ItemMeta x=i.getItemMeta();if(x!=null){x.setDisplayName(n);x.setLore(l);i.setItemMeta(x);}return i;}
 private record WatchState(Location location,GameMode mode,boolean flight,boolean flying,boolean wasVanished){}
}