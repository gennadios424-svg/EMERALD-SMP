package net.emeraldsmp.live;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.roles.RoleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class LiveManager implements Listener, CommandExecutor {
    private static final String TITLE = "§2§l💚 LIVE STATUS";
    private static final long COOLDOWN_MS = 60L * 60L * 1000L;
    private final EmeraldSMP plugin;
    private final Set<UUID> live = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, String> links = new ConcurrentHashMap<>();

    public LiveManager(EmeraldSMP plugin){this.plugin=plugin;}
    public boolean isLive(Player p){return p!=null&&live.contains(p.getUniqueId());}
    public String getLink(Player p){return p==null?null:links.get(p.getUniqueId());}
    public void refresh(){if(plugin.getServerUI()!=null)plugin.getServerUI().refreshAll();}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage("§cOnly players can use /live.");return true;}
        if(plugin.getRoleManager().get(p)!=RoleManager.Role.MEDIA){
            p.sendMessage("§cYou must have the MEDIA role to use /live.");
            p.playSound(p.getLocation(),Sound.ENTITY_VILLAGER_NO,.7f,.9f); return true;
        }
        if(args.length>0){startLinkedLive(p,args[0]);return true;}
        open(p);return true;
    }

    private void startLinkedLive(Player p,String link){
        String platform=platformFor(link);
        if(platform==null){
            p.sendMessage("§cInvalid live URL! Only YouTube, Twitch, and TikTok links are allowed.");
            p.playSound(p.getLocation(),Sound.ENTITY_VILLAGER_NO,.7f,.9f); return;
        }
        long now=System.currentTimeMillis(); Long last=cooldowns.get(p.getUniqueId());
        if(last!=null&&now-last<COOLDOWN_MS){
            long left=COOLDOWN_MS-(now-last);
            long minutes=Math.max(1,(left+59999L)/60000L);
            p.sendMessage("§e§lLIVE COOLDOWN §8» §7You can use §f/live <link> §7again in §f"+minutes+"m§7.");
            p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.5f,.9f); return;
        }

        cooldowns.put(p.getUniqueId(),now);
        live.add(p.getUniqueId()); links.put(p.getUniqueId(),link); refresh();
        p.sendMessage("§a§l🔴 YOU ARE LIVE §8» §7Your "+platform+" stream link has been shared with the server.");
        p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.7f,1.15f);

        Component prefix=Component.text("§c§l🔴 LIVE NOW §f§f");
        Component message=prefix.append(Component.text("§f"+p.getName()+" is now LIVE! §7▶ "))
                .append(Component.text("§b§nWatch on "+platform, ClickEvent.openUrl(link),
                        HoverEvent.showText(Component.text("§a§lCLICK TO WATCH §7• "+p.getName()+" is live"))));
        Bukkit.broadcast(message);

        for(Player viewer:Bukkit.getOnlinePlayers()) viewer.playSound(viewer.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.65f,1.15f);
    }

    private String platformFor(String raw){
        if(raw==null||raw.isBlank())return null;
        try{
            URI u=URI.create(raw.trim());
            String scheme=u.getScheme(), host=u.getHost();
            if(scheme==null||host==null||!("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)))return null;
            host=host.toLowerCase(Locale.ROOT);
            return switch(host){
                case "youtube.com","www.youtube.com","youtu.be","www.youtube-nocookie.com" -> "YouTube";
                case "twitch.tv","www.twitch.tv" -> "Twitch";
                case "tiktok.com","www.tiktok.com","vm.tiktok.com" -> "TikTok";
                default -> null;
            };
        }catch(Exception e){return null;}
    }

    private void open(Player p){
        Inventory inv=Bukkit.createInventory(null,27,TITLE); boolean active=isLive(p);
        for(int i=0;i<27;i++)inv.setItem(i,icon(Material.GRAY_STAINED_GLASS_PANE," ",List.of()));
        List<String> lore=active?List.of("§7Your live status is visible.","§7Stream: §b"+(getLink(p)==null?"Not linked":getLink(p)),"§eClick to turn it off."):List.of("§7Show players that you are streaming.","§7Use §f/live <link> §7to announce your stream.","§eClick to activate live status.");
        inv.setItem(13,icon(active?Material.REDSTONE_BLOCK:Material.LIME_CONCRETE,active?"§c§l🔴 LIVE ACTIVE":"§a§l▶ GO LIVE",lore));
        inv.setItem(22,icon(Material.BARRIER,"§c§l✕ CLOSE",List.of("§7Close this menu")));
        p.openInventory(inv); p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.45f,active?1.15f:1.0f);
    }

    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||!TITLE.equals(e.getView().getTitle()))return;
        e.setCancelled(true);
        if(e.getRawSlot()==22){p.closeInventory();return;} if(e.getRawSlot()!=13)return;
        if(plugin.getRoleManager().get(p)!=RoleManager.Role.MEDIA){p.closeInventory();return;}
        boolean nowLive;
        if(live.remove(p.getUniqueId())){nowLive=false;links.remove(p.getUniqueId());}
        else{live.add(p.getUniqueId());nowLive=true;}
        p.closeInventory();
        if(nowLive){p.sendMessage("§a§l🔴 YOU ARE LIVE §8» §7Use §f/live <link> §7to announce your stream link.");p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.65f,1.15f);}
        else{p.sendMessage("§7§lLIVE OFF §8» §7Your live status is now hidden.");p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.55f,.9f);}
        refresh();
    }

    @EventHandler public void quit(PlayerQuitEvent e){live.remove(e.getPlayer().getUniqueId());links.remove(e.getPlayer().getUniqueId());}
    public void disable(){live.clear();links.clear();cooldowns.clear();}
    private ItemStack icon(Material m,String name,List<String> lore){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.setDisplayName(name);meta.setLore(lore);i.setItemMeta(meta);return i;}
}
