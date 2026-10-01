package net.emeraldsmp.roles;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class RolesCommand implements CommandExecutor,TabCompleter,Listener {
    private static final String TITLE="§2§l💚 EMERALD SMP §8• §fROLE COLLECTION";
    private static final String PICKER_PREFIX="§2§l💚 ROLE §8• §f";
    private final EmeraldSMP plugin;
    private final RoleManager roles;

    public RolesCommand(EmeraldSMP plugin,RoleManager roles){this.plugin=plugin;this.roles=roles;}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.isOp()){sender.sendMessage(ChatColor.RED+"Only OP can manage roles.");return true;}
        if(args.length>=3&&args[0].equalsIgnoreCase("set")){
            Player target=Bukkit.getPlayerExact(args[1]);
            if(target==null){sender.sendMessage(ChatColor.RED+"Player must be online.");return true;}
            RoleManager.Role role=RoleManager.Role.parse(args[2]);
            if(role==null){sender.sendMessage(ChatColor.RED+"Invalid role. Use OWNER, DEV, MOD, MEDIA, EMERALD, MVP, VIP or MEMBER.");return true;}
            roles.set(target,role);
            sender.sendMessage("§a💚 Role updated: §f"+target.getName()+" §8→ "+role.badge());
            return true;
        }
        if(!(sender instanceof Player p)){sender.sendMessage("Use /roles set <player> <role> from console.");return true;}
        openCollection(p);return true;
    }

    private void openCollection(Player p){
        Inventory inv=Bukkit.createInventory(null,45,TITLE);
        inv.setItem(4,icon(Material.EMERALD,"§a§l💚 EMERALD SMP",List.of(
            "§7Premium server rank collection",
            "§8Every rank uses the same Emerald SMP badge language"
        )));
        int[] slots={10,12,14,16,19,21,23,25};
        RoleManager.Role[] all=RoleManager.Role.values();
        for(int i=0;i<all.length;i++){
            RoleManager.Role r=all[i];
            List<String> lore=new ArrayList<>();
            lore.add("§7"+r.description());
            lore.add("");
            lore.add("§fBadge: "+r.badge());
            lore.add("§7Online holders: §f"+count(r));
            lore.add("");
            lore.add("§eClick to choose a player");
            inv.setItem(slots[i],icon(material(r),r.badge(),lore));
        }
        inv.setItem(40,icon(Material.BARRIER,"§c§l✕ CLOSE",List.of()));
        p.openInventory(inv);
    }

    private void openPlayers(Player admin,RoleManager.Role role){
        Inventory inv=Bukkit.createInventory(null,54,PICKER_PREFIX+role.label());
        inv.setItem(4,icon(material(role),role.badge(),List.of(
            "§7Select an online player",
            "§7Assign them the "+role.badge()+" §7rank"
        )));
        int slot=10;
        for(Player target:Bukkit.getOnlinePlayers()){
            if(slot>=44)break;
            if(slot%9==17||slot%9==18)slot+=2;
            ItemStack head=new ItemStack(Material.PLAYER_HEAD);
            ItemMeta m=head.getItemMeta();
            m.setDisplayName("§f"+target.getName());
            m.setLore(List.of("§7Current: "+roles.get(target).badge(),"§eClick to assign "+role.badge()));
            head.setItemMeta(m);
            inv.setItem(slot++,head);
        }
        inv.setItem(49,icon(Material.ARROW,"§e§l⬅ BACK",List.of("§7Return to rank collection")));
        inv.setItem(53,icon(Material.BARRIER,"§c§l✕ CLOSE",List.of()));
        p.openInventory(inv);
    }

    private int count(RoleManager.Role r){
        int n=0;for(Player p:Bukkit.getOnlinePlayers())if(roles.get(p)==r)n++;return n;
    }

    private Material material(RoleManager.Role r){
        return switch(r){
            case OWNER->Material.EMERALD_BLOCK;
            case DEV->Material.REDSTONE_LAMP;
            case MOD->Material.PRISMARINE;
            case MEDIA->Material.AMETHYST_BLOCK;
            case EMERALD->Material.EMERALD;
            case MVP->Material.DIAMOND;
            case VIP->Material.GOLD_INGOT;
            case MEMBER->Material.PLAYER_HEAD;
        };
    }

    private ItemStack icon(Material mat,String name,List<String> lore){
        ItemStack i=new ItemStack(mat);
        ItemMeta m=i.getItemMeta();m.setDisplayName(name);m.setLore(lore);i.setItemMeta(m);return i;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||!p.isOp())return;
        String title=e.getView().getTitle();
        if(!title.startsWith("§2§l💚")&&!title.startsWith(PICKER_PREFIX))return;
        e.setCancelled(true);
        if(e.getClickedInventory()!=e.getView().getTopInventory())return;
        int slot=e.getRawSlot();
        if(TITLE.equals(title)){
            if(slot==40){p.closeInventory();return;}
            RoleManager.Role[] all=RoleManager.Role.values();
            int[] slots={10,12,14,16,19,21,23,25};
            for(int i=0;i<slots.length;i++)if(slot==slots[i]){openPlayers(p,all[i]);return;}
        }else if(title.startsWith(PICKER_PREFIX)){
            if(slot==53){p.closeInventory();return;}
            if(slot==49){openCollection(p);return;}
            String suffix=ChatColor.stripColor(title.substring(PICKER_PREFIX.length())).trim();
            RoleManager.Role role=RoleManager.Role.parse(suffix);
            if(role==null)return;
            if(e.getCurrentItem()==null||e.getCurrentItem().getType()!=Material.PLAYER_HEAD)return;
            ItemMeta meta=e.getCurrentItem().getItemMeta();
            if(meta==null)return;
            String name=ChatColor.stripColor(meta.getDisplayName());
            Player target=Bukkit.getPlayerExact(name);
            if(target!=null){
                roles.set(target,role);
                p.sendMessage("§a💚 Assigned "+role.badge()+" §ato §f"+target.getName());
                openCollection(p);
            }
        }
    }

    @EventHandler public void drag(InventoryDragEvent e){
        if(e.getView().getTitle().startsWith("§2§l💚")||e.getView().getTitle().startsWith(PICKER_PREFIX))e.setCancelled(true);
    }


    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return List.of("set");
        if(args.length==2){List<String> out=new ArrayList<>();for(Player p:Bukkit.getOnlinePlayers())out.add(p.getName());return out;}
        if(args.length==3){List<String> out=new ArrayList<>();for(RoleManager.Role r:RoleManager.Role.values())out.add(r.label().toLowerCase(Locale.ROOT));return out;}
        return List.of();
    }
}
