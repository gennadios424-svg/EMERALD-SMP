package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public final class OrderCommand implements org.bukkit.command.CommandExecutor, Listener {
    private final EmeraldSMP plugin;
    private final Map<UUID, Order> orders = new LinkedHashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, String> searchWaiting = new HashMap<>();
    private SortMode sortMode = SortMode.MOST_PAID;
    private final Set<UUID> busy = new HashSet<>();
    private final List<Material> requestable;

    public OrderCommand(EmeraldSMP plugin) {
        this.plugin = plugin;
        this.requestable = buildRequestable();
    }

    private List<Material> buildRequestable() {
        Set<String> excluded = Set.of(
            "AIR","CAVE_AIR","VOID_AIR","BARRIER","BEDROCK","COMMAND_BLOCK","CHAIN_COMMAND_BLOCK",
            "REPEATING_COMMAND_BLOCK","COMMAND_BLOCK_MINECART","STRUCTURE_BLOCK","STRUCTURE_VOID",
            "JIGSAW","LIGHT","KNOWLEDGE_BOOK","DEBUG_STICK","SPAWNER","END_PORTAL","END_GATEWAY",
            "END_PORTAL_FRAME","REINFORCED_DEEPSLATE","POTION","SPLASH_POTION","LINGERING_POTION",
            "TIPPED_ARROW","PLAYER_HEAD","PLAYER_WALL_HEAD","WITHER_SKELETON_SKULL","WITHER_SKELETON_WALL_HEAD"
        );
        List<Material> result = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isItem() || material.isAir() || material.isLegacy()) continue;
            if (excluded.contains(material.name())) continue;
            result.add(material);
        }
        result.sort(Comparator.comparing(this::pretty, String.CASE_INSENSITIVE_ORDER));
        return Collections.unmodifiableList(result);
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender s, org.bukkit.command.Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Only players can use /order."); return true; }
        openOrders(p, 0);
        return true;
    }

    private void openOrders(Player p, int page) {
        List<Order> visible = getVisible(p);
        int pages = Math.max(1, (visible.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = Bukkit.createInventory(new OrderHolder(page), 54, "§2§l📦 PLAYER ORDERS");
        int from = page * 45, to = Math.min(from + 45, visible.size());
        for (int i = from; i < to; i++) {
            Order o = visible.get(i);
            ItemStack it = new ItemStack(o.item, Math.min(64, Math.max(1, o.amount)));
            ItemMeta m = it.getItemMeta();
            m.setDisplayName("§a" + pretty(o.item));
            m.setLore(List.of("§7Amount: §f" + o.amount, "§7Per: §6$" + o.price, "§7Total: §6$" + o.total,
                "§7Ordered by: §f" + o.name, "", "§eClick to fulfill"));
            it.setItemMeta(m);
            inv.setItem(i - from, it);
        }
        inv.setItem(45, button(Material.HOPPER, "§e§lSORT: " + sortMode.label, List.of("§7Click to switch sorting")));
        if (page > 0) inv.setItem(48, button(Material.ARROW, "§a§lPREVIOUS PAGE", List.of("§7Page " + page + " / " + pages)));
        inv.setItem(49, button(Material.CHEST, "§a§lCREATE ORDER", List.of("§7Choose any obtainable item", "§7Search or browse the full list")));
        if (page + 1 < pages) inv.setItem(50, button(Material.ARROW, "§a§lNEXT PAGE", List.of("§7Page " + (page + 2) + " / " + pages)));
        inv.setItem(53, button(Material.BARRIER, "§c§lCLOSE", List.of("§7Close this menu")));
        p.openInventory(inv);
    }

    private List<Order> getVisible(Player p) {
        List<Order> list = new ArrayList<>();
        for (Order o : orders.values()) if (!o.owner.equals(p.getUniqueId())) list.add(o);
        Comparator<Order> cmp = sortMode == SortMode.MOST_PAID
            ? Comparator.comparingLong((Order o) -> o.total).reversed().thenComparingLong(o -> o.price).reversed()
            : Comparator.comparingLong((Order o) -> o.price).reversed().thenComparingLong(o -> o.total).reversed();
        list.sort(cmp);
        return list;
    }

    private void openItemSelection(Player p, int page, String query) {
        List<Material> filtered = filteredItems(query);
        int pages = Math.max(1, (filtered.size() + 35) / 36);
        page = Math.max(0, Math.min(page, pages - 1));
        String title = query == null || query.isBlank() ? "§2§l🛒 SELECT ITEM" : "§2§l🔎 " + trimTitle(query);
        Inventory inv = Bukkit.createInventory(new ItemHolder(page, query == null ? "" : query), 45, title);
        int from = page * 36, to = Math.min(from + 36, filtered.size());
        for (int i = from; i < to; i++) {
            Material mat = filtered.get(i);
            ItemStack it = new ItemStack(mat);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName("§a" + pretty(mat));
            m.setLore(List.of("§7Click to request this item"));
            it.setItemMeta(m);
            inv.setItem(i - from, it);
        }
        inv.setItem(40, button(Material.NAME_TAG, "§e§lSEARCH", List.of(
            query == null || query.isBlank() ? "§7Search the full item list" : "§7Current: §f" + query,
            "§7Click and type an item name in chat"
        )));
        if (page > 0) inv.setItem(36, button(Material.ARROW, "§a§lPREVIOUS", List.of("§7Page " + page + " / " + pages)));
        if (page + 1 < pages) inv.setItem(44, button(Material.ARROW, "§a§lNEXT", List.of("§7Page " + (page + 2) + " / " + pages)));
        if (filtered.isEmpty()) inv.setItem(22, button(Material.BARRIER, "§c§lNO ITEMS FOUND", List.of("§7Try another search")));
        p.openInventory(inv);
    }

    private List<Material> filteredItems(String query) {
        if (query == null || query.isBlank()) return requestable;
        String q = query.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        List<Material> result = new ArrayList<>();
        for (Material m : requestable) {
            String name = m.name().toLowerCase(Locale.ROOT);
            String pretty = pretty(m).toLowerCase(Locale.ROOT);
            if (name.contains(q) || pretty.contains(q)) result.add(m);
        }
        return result;
    }

    private void requestSearch(Player p) {
        UUID uuid = p.getUniqueId();
        searchWaiting.put(uuid, "order");
        p.closeInventory();
        p.sendMessage("§a§lORDER SEARCH §8» §fType an item name in chat.");
        p.sendMessage("§7Examples: §fdiamond§7, §fredstone§7, §fstone§7, §firon");
        p.sendMessage("§7Type §ccancel §7to return to the item list.");
    }

    private void beginSign(Player p, Material item) {
        if (pending.containsKey(p.getUniqueId())) return;
        Block b = p.getLocation().getBlock().getRelative(BlockFace.UP);
        if (!b.getType().isAir()) {
            p.sendMessage("§cPlease stand in an open space to create an order.");
            return;
        }
        BlockData original = b.getBlockData();
        b.setType(Material.OAK_SIGN, false);
        Sign sign = (Sign) b.getState();
        sign.setLine(0, "AMOUNT");
        sign.setLine(1, "");
        sign.setLine(2, "PRICE / ITEM");
        sign.setLine(3, "");
        sign.update(true, false);
        pending.put(p.getUniqueId(), new Pending(item, b.getLocation(), original));
        p.sendMessage("§a§lCREATE ORDER");
        p.sendMessage("§7Line 1 = §fAmount§7 (example: §f64§7)");
        p.sendMessage("§7Line 2 = §fPrice PER ITEM§7 (example: §f500§7)");
        p.sendMessage("§7Example: §f64§7 + §f500§7 = 64 " + pretty(item) + " at $500 each.");
        new BukkitRunnable() {
            public void run() {
                if (p.isOnline() && pending.containsKey(p.getUniqueId())) p.openSign((Sign) b.getState());
            }
        }.runTask(plugin);
    }

    @EventHandler
    public void sign(SignChangeEvent e) {
        Player p = e.getPlayer();
        Pending q = pending.remove(p.getUniqueId());
        if (q == null) return;
        new BukkitRunnable() {
            public void run() { q.location.getBlock().setBlockData(q.original, false); }
        }.runTask(plugin);

        String amountText = e.getLine(1);
        String priceText = e.getLine(3);
        int amount = parseInt(amountText);
        long price = plugin.getEconomyManager().parseAmount(priceText);
        if (amount < 1 || amount > 2304 || price < 1) {
            p.sendMessage("§cInvalid order. Enter a positive amount (max 2304) and price per item.");
            return;
        }
        long total;
        try { total = Math.multiplyExact(price, (long) amount); }
        catch (ArithmeticException ex) { p.sendMessage("§cOrder value is too large."); return; }
        if (plugin.getEconomyManager().getBalance(p.getUniqueId()) < total) {
            p.sendMessage("§cYou need §f$" + total + " §cto create this order.");
            return;
        }
        if (!plugin.getEconomyManager().withdraw(p.getUniqueId(), total)) {
            p.sendMessage("§cCould not reserve the order payment.");
            return;
        }
        UUID id = UUID.randomUUID();
        orders.put(id, new Order(id, p.getUniqueId(), p.getName(), q.item, amount, price, total));
        p.sendMessage("§aOrder created:");
        p.sendMessage("§7Selected Item: §f" + pretty(q.item));
        p.sendMessage("§7Amount: §f" + amount);
        p.sendMessage("§7Price Per Item: §6$" + price);
        p.sendMessage("§7Total: §6$" + total);
        refreshOrders();
    }

    @EventHandler
    public void chat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String mode = searchWaiting.remove(p.getUniqueId());\n        if (!"order".equals(mode)) return;
        e.setCancelled(true);
        String query = e.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (query.equalsIgnoreCase("cancel")) openItemSelection(p, 0, "");
            else openItemSelection(p, 0, query);
        });
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; }
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof OrderHolder h) {
            e.setCancelled(true);
            int raw = e.getRawSlot();
            if (raw == 45) { sortMode = sortMode == SortMode.MOST_PAID ? SortMode.MOST_PER_ITEM : SortMode.MOST_PAID; openOrders(p, h.page); return; }
            if (raw == 48) { if (h.page > 0) openOrders(p, h.page - 1); return; }
            if (raw == 49) { openItemSelection(p, 0, ""); return; }
            if (raw == 50) { List<Order> v = getVisible(p); if ((h.page + 1) * 45 < v.size()) openOrders(p, h.page + 1); return; }
            if (raw == 53) { p.closeInventory(); return; }
            if (raw < 0 || raw >= 45) return;
            List<Order> v = getVisible(p);
            int idx = h.page * 45 + raw;
            if (idx < v.size()) fulfill(p, v.get(idx));
        } else if (holder instanceof ItemHolder h) {
            e.setCancelled(true);
            int raw = e.getRawSlot();
            if (raw == 36 && h.page > 0) { openItemSelection(p, h.page - 1, h.query); return; }
            if (raw == 40) { requestSearch(p); return; }
            if (raw == 44) { if ((h.page + 1) * 36 < filteredItems(h.query).size()) openItemSelection(p, h.page + 1, h.query); return; }
            if (raw < 0 || raw >= 36) return;
            List<Material> filtered = filteredItems(h.query);
            int idx = h.page * 36 + raw;
            if (idx >= filtered.size()) return;
            beginSign(p, filtered.get(idx));
        }
    }

    private void fulfill(Player seller, Order o) {
        if (o.owner.equals(seller.getUniqueId())) { seller.sendMessage("§cYou cannot fulfill your own order."); return; }
        if (!orders.containsKey(o.id)) { seller.sendMessage("§cThat order is no longer active."); openOrders(seller, 0); return; }
        if (!busy.add(o.id)) { seller.sendMessage("§cThat order is already being fulfilled."); return; }
        try {
            Order current = orders.get(o.id);
            if (current == null) return;
            Player buyer = Bukkit.getPlayer(o.owner);
            if (buyer == null) { seller.sendMessage("§cThe order owner is offline."); return; }
            if (!hasItems(seller, o.item, o.amount)) { seller.sendMessage("§cYou do not have enough " + pretty(o.item) + "§c."); return; }
            removeItems(seller, o.item, o.amount);
            Map<Integer, ItemStack> extra = buyer.getInventory().addItem(new ItemStack(o.item, o.amount));
            if (!extra.isEmpty()) {
                removeItems(buyer, o.item, o.amount);
                giveItems(seller, o.item, o.amount);
                seller.sendMessage("§cThe buyer has no inventory space for the requested items.");
                return;
            }
            if (!plugin.getEconomyManager().deposit(seller.getUniqueId(), o.total)) {
                removeItems(buyer, o.item, o.amount); giveItems(seller, o.item, o.amount);
                seller.sendMessage("§cPayment failed; the transaction was rolled back.");
                return;
            }
            orders.remove(o.id);
            buyer.sendMessage("§aYour order was fulfilled by §f" + seller.getName() + "§a.");
            seller.sendMessage("§aOrder fulfilled! You received §6$" + o.total + "§a.");
            openOrders(seller, 0); refreshOrders();
        } finally { busy.remove(o.id); }
    }

    private boolean hasItems(Player p, Material m, int n) {
        int left = n;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && it.getType() == m) left -= it.getAmount();
        return left <= 0;
    }

    private void removeItems(Player p, Material m, int n) {
        for (int i = 0; i < p.getInventory().getStorageContents().length && n > 0; i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it == null || it.getType() != m) continue;
            int take = Math.min(n, it.getAmount());
            it.setAmount(it.getAmount() - take);
            n -= take;
        }
    }

    private void giveItems(Player p, Material m, int n) {
        ItemStack left = new ItemStack(m, n);
        Map<Integer, ItemStack> extra = p.getInventory().addItem(left);
        for (ItemStack x : extra.values()) p.getWorld().dropItemNaturally(p.getLocation(), x);
    }

    private String pretty(Material m) { return m.name().toLowerCase(Locale.ROOT).replace('_', ' '); }
    private String trimTitle(String s) { return s.length() > 20 ? s.substring(0, 20) : s; }

    private ItemStack button(Material m, String n, List<String> lore) {
        ItemStack i = new ItemStack(m); ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(n); meta.setLore(lore); i.setItemMeta(meta); return i;
    }

    private void refreshOrders() {
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof OrderHolder h) openOrders(p, h.page);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        Pending q = pending.remove(u);
        searchWaiting.remove(u);
        if (q != null) q.location.getBlock().setBlockData(q.original, false);
        List<UUID> refund = new ArrayList<>();
        for (Order o : orders.values()) if (o.owner.equals(u)) refund.add(o.id);
        for (UUID id : refund) {
            Order o = orders.remove(id);
            if (o != null) plugin.getEconomyManager().deposit(u, o.total);
        }
        refreshOrders();
    }

    private enum SortMode { MOST_PAID("MOST PAID"), MOST_PER_ITEM("MOST PER ITEM"); final String label; SortMode(String label) { this.label = label; } }
    private record Order(UUID id, UUID owner, String name, Material item, int amount, long price, long total) {}
    private record Pending(Material item, Location location, BlockData original) {}
    private static final class OrderHolder implements InventoryHolder {
        final int page; OrderHolder(int page) { this.page = page; } public Inventory getInventory() { return null; }
    }
    private static final class ItemHolder implements InventoryHolder {
        final int page; final String query;
        ItemHolder(int page, String query) { this.page = page; this.query = query; }
        public Inventory getInventory() { return null; }
    }
}
