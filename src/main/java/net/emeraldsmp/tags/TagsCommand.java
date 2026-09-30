package net.emeraldsmp.tags;

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

import java.util.List;

public final class TagsCommand implements CommandExecutor, Listener {
    private static final String TITLE = "§2§l💚 TAGS";
    private final EmeraldSMP plugin;
    private final TagsManager manager;

    public TagsCommand(EmeraldSMP plugin, TagsManager manager) { this.plugin = plugin; this.manager = manager; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) open(p);
        else sender.sendMessage("Players only.");
        return true;
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        long shards = plugin.getPlayerDataManager().getEmeraldShards(p.getUniqueId());
        inv.setItem(4, item(Material.EMERALD, "§a§l💚 TAGS", List.of(
                "§7Each tag costs §f" + String.format("%,d", TagsManager.COST) + " Emerald Shards",
                "§7Your Shards: §f" + String.format("%,d", shards),
                "§7Owned tags can be switched for free.")));
        int[] slots = {10,12,14,16,22};
        for (int i=0;i<TagsManager.TAGS.size();i++) {
            String tag = TagsManager.TAGS.get(i);
            boolean owned = manager.owns(p.getUniqueId(), tag);
            boolean active = tag.equals(manager.active(p.getUniqueId()));
            String status = active ? "§a✓ ACTIVE" : owned ? "§a✓ OWNED" : "§c§l500 SHARDS";
            inv.setItem(slots[i], item(Material.NAME_TAG, "§f[" + tag + "]", List.of(status,
                    owned ? "§7Click to select" : "§7Click to purchase")));
        }
        p.openInventory(inv);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack i = new ItemStack(material);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(name); m.setLore(lore); i.setItemMeta(m);
        return i;
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !e.getView().getTitle().equals(TITLE)) return;
        e.setCancelled(true);
        int[] slots = {10,12,14,16,22};
        for (int i=0;i<slots.length;i++) if (e.getRawSlot()==slots[i]) {
            String tag=TagsManager.TAGS.get(i);
            if (manager.owns(p.getUniqueId(), tag)) {
                manager.select(p.getUniqueId(), tag);
                p.sendMessage(ChatColor.GREEN + "Selected tag [" + tag + "].");
            } else if (manager.buy(p.getUniqueId(), tag)) {
                manager.select(p.getUniqueId(), tag);
                p.sendMessage(ChatColor.GREEN + "Purchased and selected [" + tag + "] for 500 Emerald Shards.");
            } else {
                p.sendMessage(ChatColor.RED + "You need 500 Emerald Shards.");
            }
            open(p);
            break;
        }
    }
}
