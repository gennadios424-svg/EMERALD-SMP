package net.emeraldsmp.live;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.roles.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class LiveManager implements Listener, CommandExecutor {
    private static final String TITLE = "§2§l💚 LIVE STATUS";
    private final EmeraldSMP plugin;
    private final Set<UUID> live = ConcurrentHashMap.newKeySet();
    public LiveManager(EmeraldSMP plugin){this.plugin=plugin;}
    public boolean isLive(Player p){return p!=null&&live.contains(p.getUniqueId());}
    public void refresh(){if(plugin.getServerUI()!=null)plugin.getServerUI().refreshAll();}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage("§cOnly players can use /live.");return true;}
        if(plugin.getRoleManager().get(p)!=RoleManager.Role.MEDIA){p.sendMessage("§c§lMEDIA ONLY §8» §7Only players with the §dMEDIA §7role can use /live.");p.playSound(p.getLocation(),Sound.ENTITY_VILLAGER_NO,.7f,.9f);return true;}
        open(p);return true;
    }
    private void open(Player p){
        Inventory inv=Bukkit.createInventory(null,27,TITLE);boolean active=isLive(p);
        for(int i=0;i<27;i++)inv.setItem(i,icon(Material.GRAY_STAINED_GLASS_PANE," ",List.of()));
        inv.setItem(13,icon(active?Material.REDSTONE_BLOCK:Material.LIME_CONCRETE,active?"§c§l🔴 LIVE ACTIVE":"§a§l▶ GO LIVE",active?List.of("§7Your live status is visible.","§eClick to turn it off."):List.of("§7Show players that you are streaming.","§eClick to activate live status.")));
        inv.setItem(22,icon(Material.BARRIER,"§c§l✕ CLOSE",List.of("§7Close this menu")));
        p.openInventory(inv);p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.45f,active?1.15f:1.0f);
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||!TITLE.equals(e.getView().getTitle()))return;e.setCancelled(true);
        if(e.getRawSlot()==22){p.closeInventory();return;}if(e.getRawSlot()!=13)return;
        if(plugin.getRoleManager().get(p)!=RoleManager.Role.MEDIA){p.closeInventory();return;}
        boolean nowLive;if(live.remove(p.getUniqueId()))nowLive=false;else{live.add(p.getUniqueId());nowLive=true;}
        p.closeInventory();
        if(nowLive){p.sendMessage("§a§l🔴 YOU ARE LIVE §8» §7Other players can now see your live status.");p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.65f,1.15f);}
        else{p.sendMessage("§7§lLIVE OFF §8» §7Your live status is now hidden.");p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.55f,.9f);}
        refresh();
    }
    @EventHandler public void quit(PlayerQuitEvent e){live.remove(e.getPlayer().getUniqueId());}
    public void disable(){live.clear();}
    private ItemStack icon(Material m,String name,List<String> lore){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.setDisplayName(name);meta.setLore(lore);i.setItemMeta(meta);return i;}
}
