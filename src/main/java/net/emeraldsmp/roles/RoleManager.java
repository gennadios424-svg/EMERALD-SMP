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
        OWNER("OWNER","👑",new String[]{"#064e3b","#10b981","#facc15","#ffffff"},"Server owner / leadership rank",7),
        DEV("DEV","⚡",new String[]{"#064e3b","#14b8a6","#67e8f9","#ffffff"},"Development and technical rank",6),
        MOD("MOD","🛡",new String[]{"#064e3b","#0f766e","#2dd4bf","#ffffff"},"Moderation and community rank",5),
        MEDIA("MEDIA","🎥",new String[]{"#047857","#a855f7","#ec4899","#ffffff"},"Media and creator rank",4),
        EMERALD("EMERALD","💚",new String[]{"#064e3b","#059669","#22c55e","#ffffff"},"Premium Emerald SMP supporter rank",3),
        MVP("MVP","💎",new String[]{"#064e3b","#06b6d4","#67e8f9","#ffffff","#facc15"},"High-tier premium rank",2),
        VIP("VIP","⭐",new String[]{"#047857","#d4af37","#fde047","#ffffff"},"Premium supporter rank",1),
        MEMBER("MEMBER","✦",new String[]{"#064e3b","#6b7280","#d1d5db","#ffffff"},"Default Emerald SMP rank",0);
        private final String label,icon,description; private final String[] colors; private final int weight;
        Role(String label,String icon,String[] colors,String description,int weight){this.label=label;this.icon=icon;this.colors=colors;this.description=description;this.weight=weight;}
        public String label(){return label;} public String icon(){return icon;} public String[] colors(){return colors.clone();} public String description(){return description;} public int weight(){return weight;}
        public String color(){return RoleUtil.legacyHex(colors[1]);} public String darkColor(){return RoleUtil.legacyHex(colors[0]);} public String badge(){return RoleUtil.legacyBadge(this);}
        public static Role parse(String s){try{return valueOf(s.toUpperCase(Locale.ROOT));}catch(Exception e){return null;}}
    }

    private final EmeraldSMP plugin;
    private final File file;
    private final Map<UUID,Role> roles=new ConcurrentHashMap<>();

    public RoleManager(EmeraldSMP plugin){this.plugin=plugin;file=new File(plugin.getDataFolder(),"roles.yml");}

    public synchronized void load(){
        roles.clear();
        if(!file.exists())return;
        YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
        for(String raw:y.getStringList("players")){
            String[] p=raw.split("=",2);
            if(p.length!=2)continue;
            try{
                Role r=Role.parse(p[1]);
                if(r!=null)roles.put(UUID.fromString(p[0]),r);
            }catch(Exception ignored){}
        }
    }

    public synchronized void save(){
        YamlConfiguration y=new YamlConfiguration();
        List<String> list=new ArrayList<>();
        roles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->list.add(e.getKey()+"="+e.getValue().label()));
        y.set("players",list);
        try{y.save(file);}catch(IOException e){plugin.getLogger().warning("Could not save roles.yml: "+e.getMessage());}
    }

    public Role get(UUID uuid){return roles.getOrDefault(uuid,Role.MEMBER);}
    public Role get(Player p){return get(p.getUniqueId());}
    public boolean isStaff(Player p){Role r=get(p);return r==Role.OWNER||r==Role.DEV||r==Role.MOD;}
    public boolean canPunish(Player executor,Player target){return get(executor).weight()>get(target).weight();}

    public synchronized boolean ensureMember(Player p){
        if(roles.containsKey(p.getUniqueId()))return false;
        roles.put(p.getUniqueId(),Role.MEMBER);save();return true;
    }

    public synchronized boolean set(Player p,Role role){
        if(role==null)return false;
        Role old=roles.put(p.getUniqueId(),role);
        save();refresh(p);return old!=role;
    }

    public synchronized boolean set(UUID uuid,Role role){
        if(role==null)return false;
        Role old=roles.put(uuid,role);
        save();
        Player p=Bukkit.getPlayer(uuid);
        if(p!=null)refresh(p);
        return old!=role;
    }

    public void refresh(Player p){
        if(!p.isOnline())return;
        p.setPlayerListName(RoleUtil.tabName(p,this));
        Bukkit.getScheduler().runTask(plugin,()->{if(plugin.getServerUI()!=null)plugin.getServerUI().refresh(p);});
    }
}
