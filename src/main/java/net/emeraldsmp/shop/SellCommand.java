package net.emeraldsmp.shop;
import net.emeraldsmp.EmeraldSMP;import org.bukkit.command.*;import org.bukkit.entity.Player;
public final class SellCommand implements CommandExecutor{private final EmeraldSMP plugin;public SellCommand(EmeraldSMP p){plugin=p;}public boolean onCommand(CommandSender s,Command c,String l,String[]a){if(!(s instanceof Player p)){s.sendMessage("Only players can use /sell.");return true;}plugin.getCleanSellManager().open(p);return true;}}
