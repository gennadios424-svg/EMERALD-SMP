package net.emeraldsmp.crates;
import org.bukkit.*;import org.bukkit.command.*;import org.bukkit.entity.Player;import java.util.List;
public final class CrateCommand implements CommandExecutor,TabCompleter{
 private final CrateManager manager; public CrateCommand(CrateManager m){manager=m;}
 public boolean onCommand(CommandSender s,Command c,String l,String[] a){
  if(a.length==0){s.sendMessage(ChatColor.GREEN+"/crates give <player> <key> <amount> | /crates place <type>");return true;}
  if(!s.hasPermission("emerald.admin")){s.sendMessage(ChatColor.RED+"No permission.");return true;}
  if(a[0].equalsIgnoreCase("give")&&a.length>=3){Player p=Bukkit.getPlayerExact(a[1]);if(p==null){s.sendMessage(ChatColor.RED+"Player must be online.");return true;}int n=1;try{if(a.length>3)n=Math.max(1,Math.min(64,Integer.parseInt(a[3])));}catch(Exception ex){s.sendMessage(ChatColor.RED+"Invalid amount.");return true;}p.getInventory().addItem(manager.createKey(a[2],n));s.sendMessage(ChatColor.GREEN+"Gave "+n+" "+a[2]+" key(s) to "+p.getName());return true;}
  if(a[0].equalsIgnoreCase("place")&&s instanceof Player p&&a.length>=2){manager.place(p,a[1]);return true;}
  s.sendMessage(ChatColor.GREEN+"Usage: /crates give <player> <key> <amount> | /crates place <type>");return true;
 }
 public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){if(a.length==1)return List.of("give","place");if(a.length==3&&a[0].equalsIgnoreCase("give"))return List.of("common","spawner","gold","crimson","emerald");if(a.length==2&&a[0].equalsIgnoreCase("place"))return List.of("common","spawner","gold","crimson","emerald");return List.of();}
}