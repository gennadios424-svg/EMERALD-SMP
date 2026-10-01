package net.emeraldsmp.roles;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class RoleUtil {
    private RoleUtil(){}

    public static String role(Player player){return "MEMBER";}
    public static String role(Player player,RoleManager manager){return manager.get(player).label();}

    public static String legacyHex(String hex){
        String h=hex.startsWith("#")?hex.substring(1):hex;
        if(h.length()!=6)return "§a";
        StringBuilder b=new StringBuilder("§x");
        for(char c:h.toCharArray())b.append('§').append(c);
        return b.toString();
    }

    public static String legacyBadge(RoleManager.Role r){
        StringBuilder out=new StringBuilder();
        String[] colors=r.colors();
        String text="✦ "+r.icon()+" "+r.label()+" ✦";
        int textLength=text.length();
        int colorCount=Math.max(1,colors.length);
        for(int i=0;i<textLength;i++){
            int colorIndex=Math.min(colorCount-1,(int)Math.floor((double)i/Math.max(1,textLength-1)*(colorCount-1)));
            out.append(legacyHex(colors[colorIndex])).append("§l").append(text.charAt(i));
        }
        return out.toString();
    }

    public static String roleBadge(Player player,RoleManager manager){return legacyBadge(manager.get(player));}
    public static String rolePrefix(Player player,RoleManager manager){return roleBadge(player,manager)+" §f";}
    public static String roleLabel(Player player,RoleManager manager){return roleBadge(player,manager);}
    public static String tabName(Player player,RoleManager manager){return rolePrefix(player,manager)+player.getName();}

    public static net.kyori.adventure.text.Component componentBadge(RoleManager.Role r){
        String text="✦ "+r.icon()+" "+r.label()+" ✦";
        net.kyori.adventure.text.Component out=net.kyori.adventure.text.Component.empty();
        String[] colors=r.colors();
        int textLength=text.length();
        int colorCount=Math.max(1,colors.length);
        for(int i=0;i<textLength;i++){
            int colorIndex=Math.min(colorCount-1,(int)Math.floor((double)i/Math.max(1,textLength-1)*(colorCount-1)));
            net.kyori.adventure.text.format.TextColor c=net.kyori.adventure.text.format.TextColor.fromHexString(colors[colorIndex]);
            out=out.append(net.kyori.adventure.text.Component.text(String.valueOf(text.charAt(i)))
                    .color(c).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        }
        return out;
    }

    public static ChatColor chatColor(RoleManager.Role role){
        return switch(role){
            case OWNER,EMERALD,DEV,MOD->ChatColor.GREEN;
            case MEDIA->ChatColor.LIGHT_PURPLE;
            case MVP->ChatColor.AQUA;
            case VIP->ChatColor.YELLOW;
            case MEMBER->ChatColor.GRAY;
        };
    }
}
