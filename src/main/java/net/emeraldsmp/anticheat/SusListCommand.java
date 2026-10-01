package net.emeraldsmp.anticheat;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.roles.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public final class SusListCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String TITLE = "§8§lEmerald AntiCheat • Suslist";
    private final EmeraldSMP plugin;
    private final SusListManager manager;

    public SusListCommand(EmeraldSMP plugin, SusListManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private boolean staff(Player p) {
        RoleManager.Role r = plugin.getRoleManager().get(p);
        return r == RoleManager.Role.OWNER || r == RoleManager.Role.DEV || r == RoleManager.Role.MOD
                || p.hasPermission("emerald.anticheat.bypass");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p) || !staff(p)) {
            sender.sendMessage("§cOnly Emerald SMP staff can use /suslist.");
            return true;
        }
        if (args.length == 0) {
            open(p);
            return true;
        }
        if (args[0].equalsIgnoreCase("tp") && args.length >= 2) {
            Player online = Bukkit.getPlayerExact(args[1]);
            SusListManager.Suspect s = online == null ? null : manager.get(online.getUniqueId());
            if (s == null) {
                try { s = manager.get(UUID.fromString(args[1])); } catch (IllegalArgumentException ignored) {}
            }
            if (s == null) {
                for (SusListManager.Suspect x : manager.all()) {
                    if (x.name().equalsIgnoreCase(args[1])) { s = x; break; }
                }
            }
            if (s == null) {
                p.sendMessage("§cThat player is not on the suslist.");
                return true;
            }
            if (s.location().getWorld() == null) {
                p.sendMessage("§cThe recorded world is unavailable.");
                return true;
            }
            p.teleport(s.location());
            p.sendMessage("§a§lSUSLIST §8» §fTeleported to §a" + s.name() + "§f's detection location.");
            return true;
        }
        if (args[0].equalsIgnoreCase("remove") && args.length >= 2) {
            SusListManager.Suspect found = find(args[1]);
            if (found == null) { p.sendMessage("§cPlayer not found on suslist."); return true; }
            manager.remove(found.uuid());
            p.sendMessage("§aRemoved §f" + found.name() + " §afrom the suslist.");
            open(p);
            return true;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            manager.clear();
            p.sendMessage("§a§lSUSLIST §8» §fCleared.");
            return true;
        }
        manager.sendList(p);
        return true;
    }

    private SusListManager.Suspect find(String name) {
        for (SusListManager.Suspect s : manager.all()) if (s.name().equalsIgnoreCase(name)) return s;
        return null;
    }

    private void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName(" ");
        filler.setItemMeta(fm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        int slot = 10;
        for (SusListManager.Suspect s : manager.all()) {
            if (slot >= 44) break;
            while (slot % 9 == 0 || slot % 9 == 8) slot++;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            Player online = Bukkit.getPlayer(s.uuid());
            if (online != null) meta.setOwningPlayer(online);
            meta.setDisplayName("§c§l" + s.name());
            meta.setLore(List.of(
                    "§7Detection: §f" + s.reason(),
                    "§7Flags: §c" + s.flags(),
                    "§7Status: " + (online != null ? "§aOnline" : "§7Offline"),
                    "",
                    "§e▶ Click to teleport to detection location",
                    "§8Right-click is not required"
            ));
            head.setItemMeta(meta);
            inv.setItem(slot, head);
            slot++;
        }

        ItemStack info = new ItemStack(Material.COMPASS);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§a§lAntiCheat Suslist");
        im.setLore(List.of("§7Players are added here when", "§7the flight detector kicks them.", "", "§f" + manager.all().size() + " §7current suspect(s)"));
        info.setItemMeta(im);
        inv.setItem(49, info);
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !staff(p)) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return;
        String display = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (display == null) return;
        SusListManager.Suspect s = find(display.replace("§l", "").replace("§c", "").trim());
        if (s == null) {
            p.sendMessage("§cThat suspect is no longer on the list.");
            return;
        }
        p.teleport(s.location());
        p.sendMessage("§a§lSUSLIST §8» §fTeleported to §a" + s.name() + "§f's detection location.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("remove"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (SusListManager.Suspect s : manager.all())
                if (s.name().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(s.name());
            return out;
        }
        return List.of("tp", "remove", "clear");
    }
}
