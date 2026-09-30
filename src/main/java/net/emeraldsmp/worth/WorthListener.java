package net.emeraldsmp.worth;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;

public final class WorthListener implements Listener {
    private final EmeraldSMP plugin;
    public WorthListener(EmeraldSMP plugin){this.plugin=plugin;}

    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(!isWorth(e.getView().getTitle()))return;

        e.setCancelled(true);

        if(e.getClick()==ClickType.DOUBLE_CLICK||e.getClick()==ClickType.NUMBER_KEY
                ||e.getClick()==ClickType.SWAP_OFFHAND||e.getClick()==ClickType.DROP
                ||e.getClick()==ClickType.CONTROL_DROP)return;

        int topSize=e.getView().getTopInventory().getSize();
        if(e.getRawSlot()<0||e.getRawSlot()>=topSize)return;

        // Browser is a 54-slot inventory. Info screen is 27 slots.
        // Route every browser button through WorthManager, including Next/Previous.
        if(topSize==54){
            plugin.getWorthManager().click(p,e.getRawSlot());
        } else if(topSize==27 && e.getRawSlot()==22){
            plugin.getWorthManager().click(p,22);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e){
        if(isWorth(e.getView().getTitle()))e.setCancelled(true);
    }

    private boolean isWorth(String title){
        String t=ChatColor.stripColor(title);
        return t.startsWith("💚 WORTH")||t.startsWith("💚 ");
    }
}
