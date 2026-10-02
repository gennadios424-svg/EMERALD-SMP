package net.emeraldsmp.auth;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.AsyncPlayerChatEvent;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthManager implements Listener {
    private static final int ITERATIONS=210_000, KEY_BITS=256, SALT_BYTES=16;
    private static final long ANTICHEAT_GRACE_MS=500L;
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final File file;
    private final Map<String,Record> records=new HashMap<>();
    private final Set<UUID> authenticated=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Long> antiCheatReadyAt=new ConcurrentHashMap<>();

    public AuthManager(org.bukkit.plugin.java.JavaPlugin plugin){this.plugin=plugin;this.file=new File(plugin.getDataFolder(),"auth.yml");load();}

    public void load(){
        records.clear();
        if(!file.exists())return;
        org.bukkit.configuration.file.YamlConfiguration y=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        org.bukkit.configuration.ConfigurationSection s=y.getConfigurationSection("accounts");
        if(s==null)return;
        for(String key:s.getKeys(false)){
            String salt=s.getString(key+".salt"), hash=s.getString(key+".hash");
            int it=s.getInt(key+".iterations",ITERATIONS);
            if(salt!=null&&hash!=null)records.put(key.toLowerCase(Locale.ROOT),new Record(salt,hash,it));
        }
    }
    private void save(){
        org.bukkit.configuration.file.YamlConfiguration y=new org.bukkit.configuration.file.YamlConfiguration();
        for(var e:records.entrySet()){y.set("accounts."+e.getKey()+".salt",e.getValue().salt);y.set("accounts."+e.getKey()+".hash",e.getValue().hash);y.set("accounts."+e.getKey()+".iterations",e.getValue().iterations);}
        try{y.save(file);}catch(IOException e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Could not save auth.yml",e);}
    }
    private String key(Player p){return p.getName().toLowerCase(Locale.ROOT);}
    public boolean isRegistered(Player p){return records.containsKey(key(p));}
    public boolean isAuthenticated(Player p){return authenticated.contains(p.getUniqueId());}
    public boolean isAntiCheatReady(Player p){
        UUID u=p.getUniqueId();
        return isAuthenticated(p) && p.isOnline() && antiCheatReadyAt.getOrDefault(u,Long.MAX_VALUE)<=System.currentTimeMillis();
    }
    private void markAuthenticated(Player p){
        authenticated.add(p.getUniqueId());
        antiCheatReadyAt.put(p.getUniqueId(),System.currentTimeMillis()+ANTICHEAT_GRACE_MS);
    }
    public void register(Player p,String password){
        if(isRegistered(p)){p.sendMessage(ChatColor.RED+"You are already registered. Use /login <password>.");return;}
        if(!validPassword(password)){p.sendMessage(ChatColor.RED+"Password must be 6-64 characters.");return;}
        Record r=hash(password);records.put(key(p),r);save();markAuthenticated(p);
        p.sendMessage(ChatColor.GREEN+"💚 Account registered and logged in!");
    }
    public void login(Player p,String password){
        if(!isRegistered(p)){p.sendMessage(ChatColor.RED+"You are not registered. Use /register <password>.");return;}
        Record r=records.get(key(p));
        if(!verify(password,r)){p.sendMessage(ChatColor.RED+"❌ Incorrect password.");return;}
        markAuthenticated(p);p.sendMessage(ChatColor.GREEN+"💚 Login successful!");
    }
    private boolean validPassword(String p){return p!=null&&p.length()>=6&&p.length()<=64;}
    private Record hash(String password){
        try{byte[] salt=new byte[SALT_BYTES];SecureRandom.getInstanceStrong().nextBytes(salt);byte[] out=derive(password,salt,ITERATIONS);return new Record(b64(salt),b64(out),ITERATIONS);}
        catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    private boolean verify(String password,Record r){
        try{byte[] a=Base64.getDecoder().decode(r.hash),b=derive(password,Base64.getDecoder().decode(r.salt),r.iterations);return MessageDigest.isEqual(a,b);}
        catch(Exception e){return false;}
    }
    private byte[] derive(String password,byte[] salt,int iterations)throws GeneralSecurityException{
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,iterations,KEY_BITS);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    private String b64(byte[] b){return Base64.getEncoder().encodeToString(b);}

    @EventHandler public void join(PlayerJoinEvent e){
        Player p=e.getPlayer();authenticated.remove(p.getUniqueId());antiCheatReadyAt.remove(p.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin,()->{
            if(!p.isOnline()||isAuthenticated(p))return;
            p.sendMessage(ChatColor.GREEN+"━━━━━━━━━━━━━━━━━━━━━━━━");
            if(isRegistered(p))p.sendMessage(ChatColor.GREEN+"💚 Welcome back! Use "+ChatColor.WHITE+"/login <password>");
            else p.sendMessage(ChatColor.GREEN+"💚 Welcome! Use "+ChatColor.WHITE+"/register <password>");
            p.sendMessage(ChatColor.GREEN+"You must authenticate before playing.");
            p.sendMessage(ChatColor.GREEN+"━━━━━━━━━━━━━━━━━━━━━━━━");
        },5L);
    }
    @EventHandler public void quit(PlayerQuitEvent e){UUID u=e.getPlayer().getUniqueId();authenticated.remove(u);antiCheatReadyAt.remove(u);}
    private boolean locked(Player p){return !isAuthenticated(p);}
    @EventHandler public void command(PlayerCommandPreprocessEvent e){
        if(!locked(e.getPlayer()))return;
        String cmd=e.getMessage().trim().toLowerCase(Locale.ROOT);
        if(cmd.startsWith("/login ")||cmd.equals("/login")||cmd.startsWith("/register ")||cmd.equals("/register"))return;
        e.setCancelled(true);e.getPlayer().sendMessage(ChatColor.RED+"🔒 Please authenticate first: "+(isRegistered(e.getPlayer())?"/login <password>":"/register <password>"));
    }
    @EventHandler public void move(PlayerMoveEvent e){if(locked(e.getPlayer())&&e.getTo()!=null&&((int)e.getFrom().getX()!=(int)e.getTo().getX()||(int)e.getFrom().getZ()!=(int)e.getTo().getZ()))e.setTo(e.getFrom());}
    @EventHandler public void interact(PlayerInteractEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void breakBlock(BlockBreakEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void placeBlock(BlockPlaceEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void inventory(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p&&locked(p))e.setCancelled(true);}
    @EventHandler public void chat(AsyncPlayerChatEvent e){if(locked(e.getPlayer())){e.setCancelled(true);e.getPlayer().sendMessage(ChatColor.RED+"🔒 Authenticate first with /login or /register.");}}
    public void shutdown(){authenticated.clear();antiCheatReadyAt.clear();}
    private record Record(String salt,String hash,int iterations){}
}
