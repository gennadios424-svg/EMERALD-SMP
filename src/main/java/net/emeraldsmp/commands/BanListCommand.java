package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BanListCommand implements CommandExecutor, Listener {
    private static final int PAGE_SIZE = 45;
    private final EmeraldSMP plugin;

    public BanListCommand(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "&c/banlist can only be opened by a player.");
            return true;
        }
        if (!player.hasPermission("emerald.admin")) {
            plugin.getMessageService().send(player, "&cYou do not have permission to do that.");
            return true;
        }
        int page = 1;
        if (args.length > 1) {
            plugin.getMessageService().send(player, "&cUsage: /banlist [page]");
            return true;
        }
        if (args.length == 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ex) {
                plugin.getMessageService().send(player, "&cPage must be a number.");
                return true;
            }
        }
        openList(player, page);
        return true;
    }

    private void openList(Player player, int requestedPage) {
        List<BanEntry<?>> entries = entries();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(Math.max(1, requestedPage), pages);

        BanListHolder holder = new BanListHolder(page, pages, entries);
        Inventory inv = Bukkit.createInventory(holder, 54, "§2§l💚 BAN LIST §8• §a" + page + "/" + pages);

        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());
        for (int i = from; i < to; i++) {
            BanEntry<?> entry = entries.get(i);
            inv.setItem(i - from, banItem(entry));
        }

        fill(inv);
        inv.setItem(48, button(Material.ARROW, "§a◀ Previous Page",
                page > 1 ? List.of("§7Open page " + (page - 1)) : List.of("§8Already on first page")));
        inv.setItem(49, button(Material.EMERALD, "§a§lPage " + page + "/" + pages,
                List.of("§7Banned players: §f" + entries.size())));
        inv.setItem(50, button(Material.ARROW, "§aNext Page ▶",
                page < pages ? List.of("§7Open page " + (page + 1)) : List.of("§8Already on last page")));
        player.openInventory(inv);
    }

    private List<BanEntry<?>> entries() {
        List<BanEntry<?>> result = new ArrayList<>();
        for (BanEntry<?> entry : Bukkit.getBanList(BanList.Type.NAME).getEntries()) {
            if (entry.getTarget() != null && !entry.getTarget().toString().isBlank()) result.add(entry);
        }
        result.sort(Comparator.comparing(e -> e.getTarget().toString().toLowerCase(Locale.ROOT)));
        return result;
    }

    private ItemStack banItem(BanEntry<?> entry) {
        String name = entry.getTarget() == null ? "Unknown" : entry.getTarget().toString();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(offline);
            meta.setDisplayName("§c🔨 §f" + name);
            List<String> lore = new ArrayList<>();
            lore.add("§7📌 Reason: §f" + reason(entry));
            lore.add("§7⏱ Duration: §f" + duration(entry));
            lore.add("§7👤 Banned by: §f" + source(entry));
            lore.add("");
            lore.add("§aClick for ban details");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack detailItem(BanEntry<?> entry) {
        String name = entry.getTarget() == null ? "Unknown" : entry.getTarget().toString();
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(name));
            meta.setDisplayName("§a§l💚 BAN DETAILS");
            meta.setLore(List.of(
                    "§7👤 Player: §f" + name,
                    "",
                    "§7📌 Reason:",
                    "§f" + reason(entry),
                    "",
                    "§7⏱ Duration:",
                    "§f" + duration(entry),
                    "",
                    "§7👤 Banned by:",
                    "§f" + source(entry)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String reason(BanEntry<?> entry) {
        String reason = entry.getReason();
        return reason == null || reason.isBlank() ? "No reason provided" : ChatColor.stripColor(reason);
    }

    private String source(BanEntry<?> entry) {
        String source = entry.getSource();
        return source == null || source.isBlank() ? "Unknown" : ChatColor.stripColor(source);
    }

    private String duration(BanEntry<?> entry) {
        if (entry.getExpiration() == null) return "Permanent";
        long millis = Math.max(0L, entry.getExpiration().getTime() - System.currentTimeMillis());
        if (millis <= 0L) return "Expired";
        Duration d = Duration.ofMillis(millis);
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        if (days > 0) return days + " day" + (days == 1 ? "" : "s");
        if (hours > 0) return hours + " hour" + (hours == 1 ? "" : "s");
        if (minutes > 0) return minutes + " minute" + (minutes == 1 ? "" : "s");
        return Math.max(1, seconds) + " second" + (seconds == 1 ? "" : "s");
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fill(Inventory inv) {
        ItemStack filler = button(Material.LIME_STAINED_GLASS_PANE, "§2", List.of());
        for (int slot = 45; slot < 54; slot++) if (inv.getItem(slot) == null) inv.setItem(slot, filler);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof BanListHolder holder)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;

        if (slot == 48 && holder.page > 1) {
            openList(player, holder.page - 1);
            return;
        }
        if (slot == 50 && holder.page < holder.pages) {
            openList(player, holder.page + 1);
            return;
        }
        if (slot >= 0 && slot < PAGE_SIZE) {
            int index = (holder.page - 1) * PAGE_SIZE + slot;
            if (index >= 0 && index < holder.entries.size()) openDetails(player, holder.entries.get(index));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BanListHolder) event.setCancelled(true);
        if (event.getView().getTopInventory().getHolder() instanceof BanDetailsHolder) event.setCancelled(true);
    }

    private void openDetails(Player player, BanEntry<?> entry) {
        BanDetailsHolder holder = new BanDetailsHolder(entry);
        Inventory inv = Bukkit.createInventory(holder, 27, "§2§l💚 BAN DETAILS");
        inv.setItem(13, detailItem(entry));
        inv.setItem(22, button(Material.ARROW, "§a◀ Back", List.of("§7Return to the ban list")));
        fill(inv);
        player.openInventory(inv);
    }

    private static final class BanListHolder implements InventoryHolder {
        private final int page;
        private final int pages;
        private final List<BanEntry<?>> entries;

        private BanListHolder(int page, int pages, List<BanEntry<?>> entries) {
            this.page = page;
            this.pages = pages;
            this.entries = entries;
        }

        @Override public Inventory getInventory() { return null; }
    }

    private static final class BanDetailsHolder implements InventoryHolder {
        private final BanEntry<?> entry;
        private BanDetailsHolder(BanEntry<?> entry) { this.entry = entry; }
        @Override public Inventory getInventory() { return null; }
    }
}
