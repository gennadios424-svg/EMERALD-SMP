package net.emeraldsmp.worth;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorthListener implements Listener {
    private final EmeraldSMP plugin;
    private final Map<UUID, String> queries = new HashMap<>();
    private final Map<UUID, Integer> pages = new HashMap<>();
    private final Map<UUID, WorthCategoryFilter> filters = new HashMap<>();
    private final Map<UUID, WorthSort> sorts = new HashMap<>();
    private static final Pattern PAGE = Pattern.compile("Page\\s+(\\d+)\\s*/\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    public WorthListener(EmeraldSMP plugin){this.plugin=plugin;}

    @EventHandler(priority=EventPriority.HIGHEST)
    public void command(PlayerCommandPreprocessEvent e){
        String raw=e.getMessage().trim();
        String lower=raw.toLowerCase(Locale.ROOT);
        if(lower.equals("/worth")||lower.startsWith("/worth ")){
            String q=raw.length()>6?raw.substring(6).trim():"";
            queries.put(e.getPlayer().getUniqueId(),q);
            pages.put(e.getPlayer().getUniqueId(),0);
            filters.put(e.getPlayer().getUniqueId(),WorthCategoryFilter.ALL);
            sorts.put(e.getPlayer().getUniqueId(),WorthSort.NAME_ASC);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void open(InventoryOpenEvent e){
        if(!(e.getPlayer() instanceof Player p)||!isWorth(e.getView().getTitle()))return;
        if(e.getInventory().getSize()!=54)return;
        // Remove the old clutter buttons from older WorthManager builds.
        e.getInventory().setItem(46,null); // Clear category
        e.getInventory().setItem(48,null); // Clear search
        e.getInventory().setItem(51,null); // Emerald SMP filler
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(!isWorth(e.getView().getTitle()))return;
        e.setCancelled(true);

        if(e.getClick()==ClickType.DOUBLE_CLICK||e.getClick()==ClickType.NUMBER_KEY
                ||e.getClick()==ClickType.SWAP_OFFHAND||e.getClick()==ClickType.DROP
                ||e.getClick()==ClickType.CONTROL_DROP)return;

        int top=e.getView().getTopInventory().getSize();
        if(e.getRawSlot()<0||e.getRawSlot()>=top)return;

        if(top==54){
            UUID id=p.getUniqueId();
            String title=ChatColor.stripColor(e.getView().getTitle());
            Matcher m=PAGE.matcher(title);
            int current=pages.getOrDefault(id, m.find()?Integer.parseInt(m.group(1))-1:0);
            String q=queries.getOrDefault(id,"");
            WorthCategoryFilter filter=filters.getOrDefault(id,WorthCategoryFilter.ALL);
            WorthSort sort=sorts.getOrDefault(id,WorthSort.NAME_ASC);

            // Handle pagination here so it cannot be swallowed by an older GUI listener.
            if(e.getRawSlot()==53){
                pages.put(id,current+1);
                plugin.getWorthManager().openBrowser(p,q,current+1,filter,sort);
                return;
            }
            if(e.getRawSlot()==45){
                if(current<=0)return;
                pages.put(id,current-1);
                plugin.getWorthManager().openBrowser(p,q,current-1,filter,sort);
                return;
            }
            if(e.getRawSlot()==47){
                WorthCategoryFilter[] v=WorthCategoryFilter.values();
                WorthCategoryFilter next=v[(filter.ordinal()+1)%v.length];
                filters.put(id,next); pages.put(id,0);
                plugin.getWorthManager().openBrowser(p,q,0,next,sort);
                return;
            }
            if(e.getRawSlot()==49){
                WorthSort next=sort.next();
                sorts.put(id,next); pages.put(id,0);
                plugin.getWorthManager().openBrowser(p,q,0,filter,next);
                return;
            }
            // The search button closes the GUI; search is still done with /worth <item>.
            if(e.getRawSlot()==50){
                p.closeInventory();
                plugin.getMessageService().send(p,"&eUse &f/worth <item> &eto search the database.");
                return;
            }
            // Do not route the removed filler slots anywhere.
            if(e.getRawSlot()==46||e.getRawSlot()==48||e.getRawSlot()==51||e.getRawSlot()==52)return;
            plugin.getWorthManager().click(p,e.getRawSlot());
            return;
        }

        if(top==27 && e.getRawSlot()==22){
            plugin.getWorthManager().click(p,22);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e){
        if(isWorth(e.getView().getTitle()))e.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent e){
        if(isWorth(e.getView().getTitle())&&e.getPlayer() instanceof Player p){
            // Keep state while moving between browser/info screens; it is harmless to retain until next /worth.
            pages.putIfAbsent(p.getUniqueId(),0);
        }
    }

    private boolean isWorth(String title){
        String t=ChatColor.stripColor(title);
        return t.startsWith("💚 WORTH")||t.startsWith("💚 ");
    }
}
