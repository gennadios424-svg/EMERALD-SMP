package net.emeraldsmp.roles;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class RolesCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String TITLE="§2§l👑 ROLE MANAGEMENT";
    private final EmeraldSMP plugin;
    private final RoleManager roles;
    private final Map<UUID,UUID> selected=new HashMap<>();

    public RolesCommand(EmeraldSMP plugin,RoleManager roles){this.plugin=plugin;this.roles=roles;}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.isOp()){sender.sendMessage(ChatColor.RED+"Only OP can manage roles.");return true;}
        if(args.length>=3 && args[0].equalsIgnoreCase("set")){
            Player target=Bukkit.getPlayerExact(args[1]);
            if(target==null){sender.sendMessage(ChatColor.RED+"Player must be online.");return true;}
            RoleManager.Role role=RoleManager.Role.parse(args[2]);
            if(role==null){sender.sendMessage(ChatColor.RED+"Invalid role. Use OWNER, DEV, MOD, MEDIA, EMERALD, MVP, VIP or MEMBER.");return true;}
            roles.set(target,role); roles.refresh(target);
            sender.sendMessage(ChatColor.GREEN+"Set "+target.getName()+"'s role to "+role.label()+".");
            return true;
        }
        if(!(sender instanceof Player p)){sender.sendMessage("Use /roles set <player> <role> from console.");return true;}
        openPlayers(p); return true;
    }

    private void openPlayers(Player p){
        Inventory inv=Bukkit.createInventory(null,54,TITLE);
        for(Player target:Bukkit.getOnlinePlayers()){
            ItemStack item=new ItemStack(Material.PLAYER_HEAD);
            ItemMeta m=item.getItemMeta();m.setDisplayName(roles.get(target).color()+target.getName());
            m.setLore(List.of("§7Current role: "+roles.get(target).color()+roles.get(target).label(),"§eClick to manage"));
            item.setItemMeta(m);inv.addItem(item);
        }
        p.openInventory(inv);
    }

    private void openRolePicker(Player admin,Player target){
        Inventory inv=Bukkit.createInventory(null,27,"§2§l👑 "+target.getName()+"'S ROLE");
        int slot=0;
        for(RoleManager.Role r:RoleManager.Role.values()){
            ItemStack item=new ItemStack(material(r));ItemMeta m=item.getItemMeta();
            m.setDisplayName(r.color()+r.label());
            m.setLore(List.of("§7Set role to "+r.label(),"§eClick to apply"));
            item.setItemMeta(m);inv.setItem(slot++,item);
        }
        admin.openInventory(inv);selected.put(admin.getUniqueId(),target.getUniqueId());
    }

    private Material material(RoleManager.Role r){return switch(r){case OWNER->Material.REDSTONE_BLOCK;case DEV->Material.DIAMOND_BLOCK;case MOD->Material.BLUE_WOOL;case MEDIA->Material.PINK_WOOL;case EMERALD->Material.EMERALD_BLOCK;case MVP->Material.GOLD_BLOCK;case VIP->Material.GOLD_INGOT;case MEMBER->Material.IRON_INGOT;};}

    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String title=e.getView().getTitle();
        if(!title.startsWith("§2§l👑") || !p.isOp())return;
        e.setCancelled(true);
        if(title.equals(TITLE)){
            ItemStack item=e.getCurrentItem(); if(item==null||item.getType()!=Material.PLAYER_HEAD)return;
            String name=ChatColor.stripColor(item.getItemMeta().getDisplayName());
            Player target=Bukkit.getPlayerExact(name); if(target!=null)openRolePicker(p,target);
        }else{
            UUID targetId=selected.get(p.getUniqueId()); if(targetId==null)return;
            RoleManager.Role[] all=RoleManager.Role.values();int slot=e.getRawSlot();
            if(slot>=0&&slot<all.length){
                Player target=Bukkit.getPlayer(targetId);
                if(target!=null){roles.set(target,all[slot]);roles.refresh(target);p.sendMessage(ChatColor.GREEN+"Role updated for "+target.getName()+": "+all[slot].label());}
                p.closeInventory();
            }
        }
    }

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return List.of("set");
        if(args.length==2){List<String> out=new ArrayList<>();for(Player p:Bukkit.getOnlinePlayers())out.add(p.getName());return out;}
        if(args.length==3){List<String> out=new ArrayList<>();for(RoleManager.Role r:RoleManager.Role.values())out.add(r.label().toLowerCase(Locale.ROOT));return out;}
        return List.of();
    }
}
