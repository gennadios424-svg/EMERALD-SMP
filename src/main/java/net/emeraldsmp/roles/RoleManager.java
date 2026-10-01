package net.emeraldsmp.roles;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RoleManager {
    public enum Role {
        OWNER("OWNER","§c","§4"), DEV("DEV","§b","§3"), MOD("MOD","§9","§1"),
        MEDIA("MEDIA","§d","§5"), EMERALD("EMERALD","§a","§2"), MVP("MVP","§6","§e"),
        VIP("VIP","§e","§6"), MEMBER("MEMBER","§7","§f");
        private final String label,color,darkColor;
        Role(String label,String color,String darkColor){this.label=label;this.color=color;this.darkColor=darkColor;}
        public String label(){return label;} public String color(){return color;} public String darkColor(){return darkColor;}
        public static Role parse(String s){try{return valueOf(s.toUpperCase(Locale.ROOT));}catch(Exception e){return null;}}
    }
    private final EmeraldSMP plugin; private final File file; private final Map<UUID,Role> roles=new ConcurrentHashMap<>();
    public RoleManager(EmeraldSMP plugin){this.plugin=plugin;file=new File(plugin.getDataFolder(),"roles.yml");}
    public synchronized void load(){roles.clear();if(!file.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
        for(String raw:y.getStringList("players")){String[] p=raw.split("=",2);if(p.length!=2)continue;try{Role r=Role.parse(p[1]);if(r!=null)roles.put(UUID.fromString(p[0]),r);}catch(Exception ignored){}}}
    public synchronized void save(){YamlConfiguration y=new YamlConfiguration();List<String> list=new ArrayList<>();roles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->list.add(e.getKey()+"="+e.getValue().label()));y.set("players",list);try{y.save(file);}catch(IOException e){plugin.getLogger().warning("Could not save roles.yml: "+e.getMessage());}}
    public Role get(UUID uuid){return roles.getOrDefault(uuid,Role.MEMBER);} public Role get(Player p){return get(p.getUniqueId());}
    public synchronized boolean ensureMember(Player p){if(roles.containsKey(p.getUniqueId()))return false;roles.put(p.getUniqueId(),Role.MEMBER);save();return true;}
    public synchronized boolean set(Player p,Role role){if(role==null)return false;Role old=roles.put(p.getUniqueId(),role);save();refresh(p);return old!=role;}
    public synchronized boolean set(UUID uuid,Role role){if(role==null)return false;Role old=roles.put(uuid,role);save();Player p=Bukkit.getPlayer(uuid);if(p!=null)refresh(p);return old!=role;}
    public void refresh(Player p){if(!p.isOnline())return;p.setPlayerListName(RoleUtil.tabName(p,this));Bukkit.getScheduler().runTask(plugin,()->{if(plugin.getServerUI()!=null)plugin.getServerUI().refresh(p);});}
}
