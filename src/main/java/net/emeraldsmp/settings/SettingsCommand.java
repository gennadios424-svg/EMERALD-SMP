package net.emeraldsmp.settings;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.util.*;

public final class SettingsCommand implements CommandExecutor, Listener {
    private final EmeraldSMP plugin;
    private final File file;
    private final Map<UUID, Map<String, Boolean>> settings = new HashMap<>();
    private static final List<String> KEYS=List.of("sounds","chat-notifications","gui-sounds","particles","tab-display");
    public SettingsCommand(EmeraldSMP plugin){this.plugin=plugin;file=new File(plugin.getDataFolder(),"player-settings.yml");load();}
    private void load(){if(!file.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(file);for(String id:y.getKeys(false))try{UUID u=UUID.fromString(id);Map<String,Boolean> m=new HashMap<>();for(String k:KEYS)m.put(k,y.getBoolean(id+"."+k,true));settings.put(u,m);}catch(Exception ignored){}}
    private void save(){YamlConfiguration y=new YamlConfiguration();for(var e:settings.entrySet())for(String k:KEYS)y.set(e.getKey()+"."+k,e.getValue().getOrDefault(k,true));try{file.getParentFile().mkdirs();y.save(file);}catch(IOException ex){plugin.getLogger().warning("Could not save player settings: "+ex.getMessage());}}
    public boolean enabled(Player p,String key){return settings.computeIfAbsent(p.getUniqueId(),x->defaults()).getOrDefault(key,true);}
    private Map<String,Boolean> defaults(){Map<String,Boolean> m=new HashMap<>();for(String k:KEYS)m.put(k,true);return m;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}open(p);return true;}
    private void open(Player p){Inventory inv=Bukkit.createInventory(new Holder(),27,"§2§l⚙ EMERALD SETTINGS");String[] names={"🔊 Sounds","📢 Chat Notifications","🎵 GUI Sounds","✨ Particles","📋 Tab Display"};for(int i=0;i<KEYS.size();i++){String k=KEYS.get(i);inv.setItem(10+i*2,item(Material.EMERALD,names[i],enabled(p,k)));}inv.setItem(22,item(Material.BARRIER,"§c§l✕ CLOSE",true));p.openInventory(inv);}
    private ItemStack item(Material mat,String name,boolean on){ItemStack i=new ItemStack(mat);ItemMeta m=i.getItemMeta();m.setDisplayName("§a§l"+name);m.setLore(List.of("§7Status: "+(on?"§aON":"§cOFF"),"§8Click to toggle"));i.setItemMeta(m);return i;}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p)||!(e.getInventory().getHolder() instanceof Holder))return;e.setCancelled(true);int slot=e.getRawSlot();if(slot==22){p.closeInventory();return;}int idx=(slot-10)/2;if(slot>=10&&slot<=18&&slot%2==0&&idx>=0&&idx<KEYS.size()){String k=KEYS.get(idx);settings.computeIfAbsent(p.getUniqueId(),x->defaults()).put(k,!enabled(p,k));save();open(p);}}
    private static final class Holder implements InventoryHolder{public Inventory getInventory(){return null;}}
}
