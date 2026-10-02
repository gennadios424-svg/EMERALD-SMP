package net.emeraldsmp.auction;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.managers.EconomyManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class AuctionCommand implements org.bukkit.command.CommandExecutor, Listener {
    private final EmeraldSMP plugin;
    private final AuctionManager manager;
    private final Map<UUID, AuctionView> views = new HashMap<>();
    private final Set<UUID> searchWaiting = new HashSet<>();
    private final Set<UUID> buying = new HashSet<>();

    public AuctionCommand(EmeraldSMP plugin, AuctionManager manager) { this.plugin = plugin; this.manager = manager; }

    @Override public boolean onCommand(org.bukkit.command.CommandSender s, org.bukkit.command.Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Only players can use /ah."); return true; }
        if (a.length > 0 && a[0].equalsIgnoreCase("sell")) { createSimpleListing(p, a); return true; }
        open(p, 0); return true;
    }

    private void createSimpleListing(Player p, String[] args) {
        if (args.length != 2) {
            p.sendMessage("§cUsage: /ah sell <price>");
            p.sendMessage("§7Hold the item you want to sell in your main hand.");
            p.sendMessage("§7Example: §f/ah sell 5000");
            return;
        }
        long price = plugin.getEconomyManager().parseAmount(args[1].replace(",", ""));
        if (price < 1) { p.sendMessage("§cEnter a valid positive whole-number price."); return; }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir() || held.getAmount() <= 0) {
            p.sendMessage("§cYou must hold an item to create an AH listing."); return;
        }
        ItemStack secured = held.clone();
        p.getInventory().setItemInMainHand(null);
        if (!manager.add(p, secured, price)) {
            p.getInventory().setItemInMainHand(held);
            p.sendMessage("§cCould not create the listing. Your item was returned."); return;
        }
        p.sendMessage("§a§lAUCTION HOUSE §8» §aListing created!");
        p.sendMessage("§7Item: §f" + pretty(secured.getType()));
        p.sendMessage("§7Amount: §f" + secured.getAmount());
        p.sendMessage("§7Price: §f" + plugin.getEconomyManager().format(price));
        p.sendMessage("§7Listing expires in: §f14d");
        open(p, 0);
    }

    private void open(Player p, int page) {
        AuctionView v = current(p);
        List<AuctionManager.Listing> ls = manager.find(v.query, v.category, v.sort);
        int pages = Math.max(1, (ls.size() + 44) / 45);
        v = new AuctionView(v.query, v.category, v.sort, Math.max(0, Math.min(page, pages - 1)));
        views.put(p.getUniqueId(), v);
        Inventory inv = Bukkit.createInventory(new AhHolder(v), 54, "§2§l🏪 AUCTION HOUSE");
        int from = v.page * 45;
        for (int i = from; i < Math.min(from + 45, ls.size()); i++) {
            AuctionManager.Listing x = ls.get(i);
            ItemStack display = x.item().clone();
            plugin.getEmeraldToolsManager().refreshExpiryLore(display);
            ItemMeta m = display.getItemMeta();
            if (m != null) {
                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add("§6💰 Price: §f" + plugin.getEconomyManager().format(x.price()));
                lore.add("§b👤 Seller: §f" + x.sellerName());
                lore.add("§e⏳ Listing expires: §f" + formatRemaining(x.expiresAt()));
                String itemExpiry = plugin.getEmeraldToolsManager().getExpiryDisplay(display);
                if (itemExpiry != null) lore.add("§a💚 Item expires: §f" + itemExpiry);
                lore.add("");
                lore.add(x.seller().equals(p.getUniqueId())
                    ? "§c§l✖ Your own listing"
                    : "§a§l➜ Click to purchase");
                m.setLore(lore);
                display.setItemMeta(m);
            }
            inv.setItem(i - from, display);
        }
        inv.setItem(45, button(Material.NAME_TAG, "§e§lSEARCH", List.of(v.query.isBlank() ? "§7Search item names" : "§7Current: §f" + v.query, "§7Partial names supported")));
        inv.setItem(46, button(Material.HOPPER, "§b§lFILTER: " + v.category.label, List.of("§7Click to cycle categories")));
        inv.setItem(47, button(Material.COMPASS, "§d§lSORT: " + v.sort.label, List.of("§7Click to cycle sorting")));
        if (v.page > 0) inv.setItem(48, button(Material.ARROW, "§a§lPREVIOUS", List.of("§7Page " + v.page + " / " + pages)));
        inv.setItem(49, button(Material.EMERALD, "§a§l➕ CREATE LISTING", List.of("§7Hold an item and use:", "§f/ah sell <price>", "§7Example: §f/ah sell 5000")));
        if (v.page + 1 < pages) inv.setItem(50, button(Material.ARROW, "§a§lNEXT", List.of("§7Page " + (v.page + 2) + " / " + pages)));
        inv.setItem(53, button(Material.BARRIER, "§c§lCLOSE", List.of("§7Close Auction House")));
        p.openInventory(inv);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof AhHolder h)) return;
        e.setCancelled(true); int slot = e.getRawSlot();
        if (slot == 45) { beginSearch(p); return; }
        if (slot == 46) { cycleCategory(p); return; }
        if (slot == 47) { cycleSort(p); return; }
        if (slot == 48) { if (h.view.page > 0) open(p, h.view.page - 1); return; }
        if (slot == 49) { p.sendMessage("§a§lAUCTION HOUSE §8» §fHold an item and use §e/ah sell <price>§f."); return; }
        if (slot == 50) { List<AuctionManager.Listing> ls = manager.find(h.view.query, h.view.category, h.view.sort); if ((h.view.page + 1) * 45 < ls.size()) open(p, h.view.page + 1); return; }
        if (slot == 53) { p.closeInventory(); return; }
        if (slot >= 0 && slot < 45) { List<AuctionManager.Listing> ls = manager.find(h.view.query, h.view.category, h.view.sort); int idx = h.view.page * 45 + slot; if (idx < ls.size()) buy(p, ls.get(idx).id()); }
    }

    private void beginSearch(Player p) {
        p.closeInventory(); searchWaiting.add(p.getUniqueId());
        p.sendMessage("§a§lAUCTION HOUSE SEARCH §8» §fEnter an item name in chat.");
        p.sendMessage("§7Examples: §fdiamond§7, §fnetherite§7, §fredstone§7, §fstone");
        p.sendMessage("§7Type §ccancel §7to clear the search.");
    }

    @EventHandler public void chat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer(); UUID u = p.getUniqueId();
        if (!searchWaiting.remove(u)) return;
        e.setCancelled(true); String q = e.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> { AuctionView v = current(p); views.put(u, new AuctionView(q.equalsIgnoreCase("cancel") ? "" : q, v.category, v.sort, 0)); open(p, 0); });
    }

    private void cycleCategory(Player p) { AuctionView v = current(p); AuctionManager.Category[] a = AuctionManager.Category.values(); views.put(p.getUniqueId(), new AuctionView(v.query, a[(v.category.ordinal() + 1) % a.length], v.sort, 0)); open(p, 0); }
    private void cycleSort(Player p) { AuctionView v = current(p); AuctionManager.SortMode[] a = AuctionManager.SortMode.values(); views.put(p.getUniqueId(), new AuctionView(v.query, v.category, a[(v.sort.ordinal() + 1) % a.length], 0)); open(p, 0); }

    private void buy(Player buyer, UUID id) {
        if (!buying.add(id)) { buyer.sendMessage("§cThat listing is already being purchased."); return; }
        try {
            AuctionManager.Listing l = manager.get(id);
            if (l == null || l.expired()) { buyer.sendMessage("§cThat listing is no longer available."); open(buyer, 0); return; }
            if (l.seller().equals(buyer.getUniqueId())) { buyer.sendMessage("§cYou cannot buy your own listing."); return; }
            EconomyManager eco = plugin.getEconomyManager();
            if (eco.getBalance(buyer.getUniqueId()) < l.price()) { buyer.sendMessage("§cYou need " + eco.format(l.price()) + "§c."); return; }
            if (!hasInventorySpaceFor(buyer, l.item())) { buyer.sendMessage("§cYou need enough inventory space for the item."); return; }
            if (!eco.withdraw(buyer.getUniqueId(), l.price())) { buyer.sendMessage("§cPayment could not be reserved."); return; }
            ItemStack[] snapshot = buyer.getInventory().getStorageContents().clone();
            AuctionManager.Listing removed = manager.remove(id);
            if (removed == null) { eco.deposit(buyer.getUniqueId(), l.price()); buyer.sendMessage("§cThe listing changed; your money was returned."); return; }
            Map<Integer, ItemStack> extra = buyer.getInventory().addItem(removed.item().clone());
            if (!extra.isEmpty()) { buyer.getInventory().setStorageContents(snapshot); manager.restore(removed); eco.deposit(buyer.getUniqueId(), removed.price()); buyer.sendMessage("§cDelivery failed; your money was returned."); return; }
            if (!eco.depositToUuid(removed.seller(), removed.price(), removed.sellerName())) {
                boolean restored = manager.restore(removed); if (restored) buyer.getInventory().setStorageContents(snapshot); eco.deposit(buyer.getUniqueId(), removed.price());
                buyer.sendMessage(restored ? "§cSeller payment failed; the purchase was rolled back." : "§cSeller payment failed; your money was returned and the item remains with you."); return;
            }
            buyer.sendMessage("§aPurchased §f" + removed.amount() + "x " + pretty(removed.item().getType()) + " §afor §6" + eco.format(removed.price()) + "§a.");
            Player seller = Bukkit.getPlayer(removed.seller()); if (seller != null) seller.sendMessage("§aYour AH listing sold for §6" + eco.format(removed.price()) + "§a.");
            open(buyer, 0);
        } finally { buying.remove(id); }
    }

    private boolean hasInventorySpaceFor(Player p, ItemStack item) {
        int free = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) free += item.getMaxStackSize();
            else if (it.isSimilar(item)) free += item.getMaxStackSize() - it.getAmount();
            if (free >= item.getAmount()) return true;
        }
        return false;
    }

    private String formatRemaining(long expiresAt) {
        long ms = Math.max(0L, expiresAt - System.currentTimeMillis());
        long minutes = ms / 60_000L;
        long days = minutes / (24L * 60L);
        minutes %= 24L * 60L;
        long hours = minutes / 60L;
        minutes %= 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(1L, minutes) + "m";
    }

    private AuctionView current(Player p) { return views.getOrDefault(p.getUniqueId(), new AuctionView("", AuctionManager.Category.ALL, AuctionManager.SortMode.NEWEST, 0)); }
    private ItemStack button(Material m, String n, List<String> lore) { ItemStack i = new ItemStack(m); ItemMeta meta = i.getItemMeta(); meta.setDisplayName(n); meta.setLore(lore); i.setItemMeta(meta); return i; }
    private String pretty(Material m) { String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' '); StringBuilder b = new StringBuilder(); for (String w : s.split(" ")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' '); return b.toString().trim(); }

    private record AuctionView(String query, AuctionManager.Category category, AuctionManager.SortMode sort, int page) {}
    private static final class AhHolder implements InventoryHolder { final AuctionView view; AhHolder(AuctionView v) { view = v; } public Inventory getInventory() { return null; } }
}
