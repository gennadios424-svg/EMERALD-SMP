package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.worth.WorthEntry;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class ShopManager {
    private static final int[] CATEGORY_SLOTS = {11, 13, 15, 21, 23};
    private static final String[] CATEGORY_KEYS = {"resources", "blocks", "redstone", "cpvp", "nether"};
    private static final String[] CATEGORY_NAMES = {"§a§l🌾 RESOURCES", "§2§l🧱 BLOCKS", "§c§l🔴 REDSTONE", "§5§l⚔ CPvP", "§4§l🔥 NETHER"};
    private static final Material[] CATEGORY_ICONS = {Material.WHEAT, Material.BRICKS, Material.REDSTONE, Material.END_CRYSTAL, Material.NETHER_BRICKS};
    private static final int SELL_ITEM_SLOTS = 45;
    private static final int SELL_BACK_SLOT = 45;
    private static final int SELL_INFO_SLOT = 49;
    private static final int SELL_CLOSE_SLOT = 53;

    private final EmeraldSMP plugin;
    private final Map<UUID, ShopView> views = new HashMap<>();
    private final Map<UUID, SellSession> sellSessions = new HashMap<>();

    public ShopManager(EmeraldSMP plugin) { this.plugin = plugin; }
    public void reload() { views.clear(); sellSessions.clear(); }

    public void openMain(Player p) {
        Inventory inv = plugin.getServer().createInventory(null, 36, "§2§l💚 EMERALD SMP SHOP");
        for (int i = 0; i < CATEGORY_SLOTS.length; i++) inv.setItem(CATEGORY_SLOTS[i], icon(CATEGORY_ICONS[i], CATEGORY_NAMES[i], List.of("§7Curated survival essentials", "§8Click to browse")));
        inv.setItem(4, icon(Material.EMERALD, "§a§l💚 EMERALD SMP", List.of("§7A compact survival economy shop", "§8Resources • Blocks • Redstone • CPvP • Nether")));
        inv.setItem(31, icon(Material.BARRIER, "§c§l✕ CLOSE", List.of()));
        views.put(p.getUniqueId(), new ShopView(false, null, 0, List.of()));
        p.openInventory(inv);
    }

    public void openCategory(Player p, String key, int page) {
        List<ShopItem> items = configuredItems(key);
        int pages = Math.max(1, (items.size() + 44) / 45);
        int safe = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = plugin.getServer().createInventory(null, 54, "§2§l💚 SHOP §8• §f" + categoryDisplay(key) + " §8• §f" + (safe + 1) + "/" + pages);
        int from = safe * 45, to = Math.min(from + 45, items.size());
        for (int i = from; i < to; i++) inv.setItem(i - from, shopDisplay(items.get(i)));
        inv.setItem(45, icon(Material.ARROW, "§e§l⬅ BACK", List.of("§7Return to shop categories")));
        inv.setItem(48, icon(Material.ARROW, "§a⬅ PREVIOUS", List.of("§7Previous page")));
        inv.setItem(49, icon(Material.EMERALD, "§a§l" + categoryDisplay(key), List.of("§7Page §f" + (safe + 1) + "§7/§f" + pages, "§7" + items.size() + " curated items")));
        inv.setItem(50, icon(Material.ARROW, "§aNEXT ➡", List.of("§7Next page")));
        inv.setItem(53, icon(Material.BARRIER, "§c§l✕ CLOSE", List.of()));
        views.put(p.getUniqueId(), new ShopView(false, key, safe, items));
        p.openInventory(inv);
    }

    public void openItem(Player p, ShopItem item, String key, int page) {
        Inventory inv = plugin.getServer().createInventory(null, 27, "§2§l💚 BUY §8• §f" + pretty(item.material()));
        inv.setItem(13, shopDisplay(item));
        inv.setItem(10, buyButton(item, 1)); inv.setItem(11, buyButton(item, 16)); inv.setItem(12, buyButton(item, 32)); inv.setItem(14, buyButton(item, 64));
        inv.setItem(16, icon(Material.EMERALD, "§a§l💰 BUY", List.of("§7Select an amount above", "§8Purchase only if you can afford it")));
        inv.setItem(18, icon(Material.ARROW, "§e§l⬅ BACK", List.of("§7Return to " + categoryDisplay(key))));
        inv.setItem(22, icon(Material.BARRIER, "§c§l✕ CLOSE", List.of()));
        views.put(p.getUniqueId(), new ShopView(false, key, page, List.of(item)));
        p.openInventory(inv);
    }

    /**
     * Manual sell system. /sell never sells anything merely because an inventory
     * was opened or an item was placed somewhere. A player must choose an item,
     * choose a quantity, then explicitly press the confirmation button.
     */
    public void openSell(Player p) {
        sellSessions.remove(p.getUniqueId());
        Inventory inv = plugin.getServer().createInventory(null, 54, "§2§l💚 SELL §8• §fSELECT ITEM");
        List<Material> materials = sellableMaterials(p);
        for (int i = 0; i < Math.min(SELL_ITEM_SLOTS, materials.size()); i++) {
            Material m = materials.get(i);
            int amount = countMaterial(p, m);
            long value = plugin.getWorthManager().sellValue(m, amount);
            inv.setItem(i, icon(m, "§a§l" + pretty(m), List.of(
                    "§7You have: §f" + fmt(amount),
                    "§7Value: §a" + plugin.getEconomyManager().format(value),
                    "",
                    "§e▶ Click to choose how many to sell"
            )));
        }
        inv.setItem(SELL_BACK_SLOT, icon(Material.ARROW, "§e§l⬅ BACK", List.of("§7Return to the main shop")));
        inv.setItem(SELL_INFO_SLOT, icon(Material.EMERALD, "§a§l💰 MANUAL SELLING", List.of(
                "§7Choose an item from your inventory.",
                "§7Then choose the exact amount.",
                "§7Nothing is sold automatically.",
                "",
                "§eYou must confirm every sale."
        )));
        inv.setItem(SELL_CLOSE_SLOT, icon(Material.BARRIER, "§c§l✕ CLOSE", List.of()));
        views.put(p.getUniqueId(), new ShopView(true, null, 0, materials));
        p.openInventory(inv);
    }

    public void openSellAmount(Player p, Material material) {
        int owned = countMaterial(p, material);
        if (owned <= 0 || plugin.getWorthManager().get(material) == null || !plugin.getWorthManager().get(material).enabled()) {
            p.sendMessage("§cYou no longer have a sellable amount of that item.");
            openSell(p);
            return;
        }

        SellSession session = new SellSession(material, 1);
        sellSessions.put(p.getUniqueId(), session);

        Inventory inv = plugin.getServer().createInventory(null, 27, "§2§l💚 SELL §8• §f" + pretty(material));
        inv.setItem(13, icon(material, "§a§l" + pretty(material), List.of(
                "§7In inventory: §f" + fmt(owned),
                "§7Sell price: §a" + plugin.getEconomyManager().format(plugin.getWorthManager().sellValue(material, 1)) + " §7/ item",
                "",
                "§eChoose a quantity, then confirm."
        )));
        inv.setItem(10, quantityButton(material, 1, owned));
        inv.setItem(11, quantityButton(material, 16, owned));
        inv.setItem(12, quantityButton(material, 64, owned));
        inv.setItem(14, quantityButton(material, owned, owned));
        inv.setItem(16, confirmSellButton(material, 1));
        inv.setItem(18, icon(Material.ARROW, "§e§l⬅ BACK", List.of("§7Choose another item")));
        inv.setItem(22, icon(Material.BARRIER, "§c§l✕ CANCEL", List.of("§7No items will be sold")));
        p.openInventory(inv);
    }

    private ItemStack quantityButton(Material material, int quantity, int owned) {
        int actual = Math.min(quantity, owned);
        long value = plugin.getWorthManager().sellValue(material, actual);
        return icon(Material.PAPER, "§e§lSELL ×" + fmt(actual), List.of(
                "§7Amount: §f" + fmt(actual),
                "§7You receive: §a" + plugin.getEconomyManager().format(value),
                "§eClick to select"
        ));
    }

    private ItemStack confirmSellButton(Material material, int quantity) {
        long value = plugin.getWorthManager().sellValue(material, quantity);
        return icon(Material.EMERALD_BLOCK, "§a§l✓ CONFIRM SALE", List.of(
                "§7Selling: §f" + fmt(quantity) + "x " + pretty(material),
                "§7You receive: §a" + plugin.getEconomyManager().format(value),
                "",
                "§eClick to complete the sale"
        ));
    }

    public boolean isSellInventory(Player p, Inventory inv) {
        ShopView view = views.get(p.getUniqueId());
        return view != null && view.sellMode() && p.getOpenInventory().getTopInventory() == inv;
    }

    public void handleSellClick(Player p, int rawSlot) {
        ShopView view = views.get(p.getUniqueId());
        if (view == null || !view.sellMode()) return;

        String title = org.bukkit.ChatColor.stripColor(p.getOpenInventory().getTitle());
        if (title.startsWith("💚 SELL • SELECT ITEM")) {
            if (rawSlot == SELL_BACK_SLOT) { openMain(p); return; }
            if (rawSlot == SELL_CLOSE_SLOT) { p.closeInventory(); return; }
            if (rawSlot >= 0 && rawSlot < SELL_ITEM_SLOTS && rawSlot < view.items().size()) {
                Material m = (Material) view.items().get(rawSlot);
                openSellAmount(p, m);
            }
            return;
        }

        if (title.startsWith("💚 SELL • ")) {
            SellSession session = sellSessions.get(p.getUniqueId());
            if (session == null) { openSell(p); return; }
            int owned = countMaterial(p, session.material());

            if (rawSlot == 18) { openSell(p); return; }
            if (rawSlot == 22) { sellSessions.remove(p.getUniqueId()); p.closeInventory(); return; }

            int selected = switch (rawSlot) {
                case 10 -> Math.min(1, owned);
                case 11 -> Math.min(16, owned);
                case 12 -> Math.min(64, owned);
                case 14 -> owned;
                default -> 0;
            };
            if (selected > 0) {
                session.quantity(selected);
                Inventory inv = p.getOpenInventory().getTopInventory();
                inv.setItem(16, confirmSellButton(session.material(), selected));
            } else if (rawSlot == 16) {
                sellSelected(p);
            }
        }
    }

    private void sellSelected(Player p) {
        UUID uuid = p.getUniqueId();
        SellSession session = sellSessions.get(uuid);
        if (session == null) return;

        Material material = session.material();
        int quantity = session.quantity();
        int owned = countMaterial(p, material);
        if (quantity <= 0 || owned < quantity) {
            p.sendMessage("§cYou no longer have enough " + pretty(material) + " to complete that sale.");
            openSell(p);
            return;
        }

        long total = plugin.getWorthManager().sellValue(material, quantity);
        if (total <= 0) {
            p.sendMessage("§cThat item cannot currently be sold.");
            openSell(p);
            return;
        }

        int remaining = quantity;
        for (int slot = 0; slot < p.getInventory().getStorageContents().length && remaining > 0; slot++) {
            ItemStack stack = p.getInventory().getStorageContents()[slot];
            if (stack == null || stack.getType() != material) continue;
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) p.getInventory().setItem(slot, null);
            remaining -= take;
        }

        if (remaining > 0 || !plugin.getEconomyManager().deposit(uuid, total)) {
            // Restore if anything went wrong. The exact quantity was removed from
            // matching material stacks, so returning it is deterministic.
            p.getInventory().addItem(new ItemStack(material, quantity - Math.max(0, remaining))).values()
                    .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
            p.sendMessage("§cThe sale could not be completed; your items were returned.");
            return;
        }

        sellSessions.remove(uuid);
        p.sendMessage("§a💰 Sold §f" + fmt(quantity) + "x " + pretty(material) + " §afor §f" + plugin.getEconomyManager().format(total) + "§a.");
        openSell(p);
    }

    private List<Material> sellableMaterials(Player p) {
        LinkedHashSet<Material> set = new LinkedHashSet<>();
        for (ItemStack stack : p.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            WorthEntry w = plugin.getWorthManager().get(stack.getType());
            if (w != null && w.enabled() && w.worth() > 0) set.add(stack.getType());
        }
        return new ArrayList<>(set);
    }

    private int countMaterial(Player p, Material material) {
        int total = 0;
        for (ItemStack stack : p.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) total += stack.getAmount();
        }
        return total;
    }

    public void closeSell(Player p) {
        sellSessions.remove(p.getUniqueId());
    }

    public void returnSellItems(Player p) {
        sellSessions.remove(p.getUniqueId());
    }

    public ShopView view(Player p) { return views.get(p.getUniqueId()); }
    public ShopItem itemFor(Player p, int slot) {
        ShopView v = views.get(p.getUniqueId());
        if (v == null || slot < 0 || slot >= 45) return null;
        int idx = v.page() * 45 + slot;
        return idx >= 0 && idx < v.items().size() ? (ShopItem) v.items().get(idx) : null;
    }

    public boolean buy(Player p, ShopItem item, int qty) {
        if (item == null || qty <= 0 || qty > 64) return false;
        long total;
        try { total = Math.multiplyExact(item.buyPrice(), qty); } catch (ArithmeticException ex) { return false; }
        if (total <= 0 || plugin.getEconomyManager().getBalance(p.getUniqueId()) < total || !hasSpace(p, item.material(), qty)) return false;
        if (!plugin.getEconomyManager().withdraw(p.getUniqueId(), total)) return false;
        Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(item.material(), qty));
        if (!left.isEmpty()) { plugin.getEconomyManager().deposit(p.getUniqueId(), total); return false; }
        return true;
    }

    private List<ShopItem> configuredItems(String key) {
        List<Material> mats = CURATED.getOrDefault(key, List.of());
        List<ShopItem> out = new ArrayList<>();
        for (Material m : mats) {
            WorthEntry w = plugin.getWorthManager().get(m);
            if (w == null || !w.enabled()) continue;
            long buy = plugin.getWorthManager().buyValue(m, 1);
            if (buy <= w.worth()) continue;
            out.add(new ShopItem(m.name().toLowerCase(Locale.ROOT), m, roleStyledName(m), buy, List.of("§7Buy: §a" + plugin.getEconomyManager().format(buy) + " §7/ item", "§7Sell: §a" + plugin.getEconomyManager().format(w.worth()) + " §7/ item", "§8Click for purchase amounts")));
        }
        return out;
    }

    private String categoryDisplay(String key) {
        for (int i = 0; i < CATEGORY_KEYS.length; i++) if (CATEGORY_KEYS[i].equals(key)) return ChatColor.stripColor(CATEGORY_NAMES[i]);
        return key;
    }
    private ItemStack shopDisplay(ShopItem item) { return icon(item.material(), item.displayName(), item.lore()); }
    private ItemStack buyButton(ShopItem item, int qty) {
        long total;
        try { total = Math.multiplyExact(item.buyPrice(), qty); } catch (ArithmeticException ex) { total = Long.MAX_VALUE; }
        return icon(Material.EMERALD, "§a§lBUY ×" + qty, List.of("§7Price: §f" + plugin.getEconomyManager().format(total), "§8Click to purchase"));
    }
    private ItemStack filler() { return icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()); }
    private ItemStack icon(Material m, String n, List<String> l) {
        ItemStack i = new ItemStack(m); ItemMeta meta = i.getItemMeta();
        if (meta != null) { meta.setDisplayName(n); meta.setLore(l); i.setItemMeta(meta); }
        return i;
    }
    private boolean hasSpace(Player p, Material m, int qty) {
        int remaining = qty;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (remaining <= 0) return true;
            if (s == null || s.getType().isAir()) remaining -= m.getMaxStackSize();
            else if (s.getType() == m) remaining -= Math.max(0, m.getMaxStackSize() - s.getAmount());
        }
        return remaining <= 0;
    }
    private String roleStyledName(Material m) { return "§a§l" + pretty(m); }
    private String pretty(Material m) {
        String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' '); StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return b.toString().trim();
    }
    public record ShopView(boolean sellMode, String category, int page, List<?> items) {}
    public record ShopItem(String key, Material material, String displayName, long buyPrice, List<String> lore) {}
}
