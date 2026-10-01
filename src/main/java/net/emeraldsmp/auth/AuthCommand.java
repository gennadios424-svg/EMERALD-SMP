package net.emeraldsmp.auth;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class AuthCommand implements CommandExecutor, TabCompleter {
    private final AuthManager auth;
    public AuthCommand(AuthManager auth){this.auth=auth;}
    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] a){
        if(!(s instanceof Player p)){s.sendMessage("Players only.");return true;}
        if(a.length!=1){p.sendMessage(ChatColor.RED+"Usage: /"+label+" <password>");return true;}
        if(label.equalsIgnoreCase("register"))auth.register(p,a[0]);else auth.login(p,a[0]);
        return true;
    }
    @Override public java.util.List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){return java.util.Collections.emptyList();}
}
