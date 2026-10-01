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
    private static final int SELL_INPUT_SLOTS = 45;
    private static final int SELL_BUTTON_SLOT = 49;
    private static final int SELL_CLOSE_SLOT = 53;

    private static final Map<String, List<Material>> CURATED = Map.ofEntries(
            Map.entry("resources", List.of(Material.KELP, Material.WHEAT, Material.CARROT, Material.POTATO, Material.BAMBOO, Material.CACTUS, Material.COCOA_BEANS, Material.SUGAR_CANE, Material.NETHER_WART)),
            Map.entry("blocks", List.of(Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.SMOOTH_STONE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.POLISHED_DEEPSLATE, Material.BRICKS, Material.STONE_BRICKS, Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS, Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS, Material.MANGROVE_PLANKS, Material.CHERRY_PLANKS, Material.BAMBOO_PLANKS, Material.WHITE_WOOL, Material.BLACK_WOOL, Material.GRAY_WOOL, Material.LIGHT_GRAY_WOOL, Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL, Material.GREEN_WOOL, Material.CYAN_WOOL, Material.LIGHT_BLUE_WOOL, Material.BLUE_WOOL, Material.PURPLE_WOOL, Material.MAGENTA_WOOL, Material.PINK_WOOL, Material.BROWN_WOOL, Material.WHITE_CONCRETE, Material.BLACK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.GREEN_CONCRETE, Material.CYAN_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE, Material.PINK_CONCRETE, Material.BROWN_CONCRETE, Material.GLASS, Material.GLASS_PANE, Material.TERRACOTTA, Material.WHITE_TERRACOTTA, Material.BLACK_TERRACOTTA, Material.SMOOTH_QUARTZ, Material.QUARTZ_BLOCK, Material.GLOWSTONE, Material.SEA_LANTERN)),
            Map.entry("redstone", List.of(Material.REDSTONE, Material.REDSTONE_TORCH, Material.REPEATER, Material.COMPARATOR, Material.PISTON, Material.STICKY_PISTON, Material.OBSERVER, Material.DISPENSER, Material.DROPPER, Material.HOPPER, Material.TARGET, Material.LEVER, Material.STONE_BUTTON, Material.TRIPWIRE_HOOK, Material.DAYLIGHT_DETECTOR)),
            Map.entry("cpvp", List.of(Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.END_CRYSTAL, Material.RESPAWN_ANCHOR, Material.GLOWSTONE, Material.TNT, Material.ENDER_PEARL)),
            Map.entry("nether", List.of(Material.NETHERRACK, Material.SOUL_SAND, Material.SOUL_SOIL, Material.BASALT, Material.BLACKSTONE, Material.NETHER_BRICKS, Material.NETHER_BRICK_FENCE, Material.GLOWSTONE))
    );

    private final EmeraldSMP plugin;
    private final Map<UUID, ShopView> views = new HashMap<>();
    private final Map<UUID, Inventory> sellInventories = new HashMap<>();
    private final Set<UUID> sellProcessing = new HashSet<>();

    public ShopManager(EmeraldSMP plugin) { this.plugin = plugin; }
    public void reload() { views.clear(); }

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

    public void openSell(Player p) {
        returnSellItems(p);
        Inventory inv = plugin.getServer().createInventory(null, 54, "§2§l💚 SELL ITEMS");
        for (int i = SELL_INPUT_SLOTS; i < 54; i++) inv.setItem(i, filler());
        inv.setItem(47, icon(Material.EMERALD, "§a§l💰 TOTAL", List.of("§7Current sell value", "§f$0")));
        inv.setItem(49, icon(Material.EMERALD_BLOCK, "§a§l💰 SELL", List.of("§7Sell every valid item in the input area", "§8Transaction is validated server-side", "§eClick to sell")));
        inv.setItem(53, icon(Material.BARRIER, "§c§l✕ CLOSE", List.of("§7Return your items without selling")));
        sellInventories.put(p.getUniqueId(), inv);
        p.openInventory(inv);
        updateSellDisplay(p, inv);
    }

    public boolean isSellInventory(Player p, Inventory inv) { return sellInventories.get(p.getUniqueId()) == inv; }
    public boolean isSellButton(int slot) { return slot == SELL_BUTTON_SLOT; }

    public void handleSellClick(Player p, int rawSlot) {
        Inventory inv = sellInventories.get(p.getUniqueId());
        if (inv == null) return;
        if (rawSlot == SELL_BUTTON_SLOT) sellContents(p);
        else if (rawSlot == SELL_CLOSE_SLOT) p.closeInventory();
        else if (rawSlot >= 0 && rawSlot < SELL_INPUT_SLOTS) updateSellDisplay(p, inv);
    }

    public void sellContents(Player p) {
        UUID u = p.getUniqueId();
        if (!sellProcessing.add(u)) { p.sendMessage("§e💰 Sell transaction already processing."); return; }
        Inventory inv = sellInventories.get(u);
        if (inv == null) { sellProcessing.remove(u); return; }
        try {
            long total = 0;
            long itemCount = 0;
            for (int slot = 0; slot < SELL_INPUT_SLOTS; slot++) {
                ItemStack stack = inv.getItem(slot);
                if (stack == null || stack.getType().isAir()) continue;
                long value = plugin.getWorthManager().sellValue(stack.getType(), stack.getAmount());
                if (value <= 0) continue;
                if (Long.MAX_VALUE - total < value) { p.sendMessage("§cSell value is too large; nothing was sold."); return; }
                total += value;
                itemCount += stack.getAmount();
            }
            if (total <= 0) { p.sendMessage("§cThere are no sellable items in the sell area."); return; }

            // Validate the complete transaction before mutating the inventory.
            if (!plugin.getEconomyManager().deposit(u, total)) { p.sendMessage("§cThe economy rejected this transaction; nothing was removed."); return; }

            for (int slot = 0; slot < SELL_INPUT_SLOTS; slot++) {
                ItemStack stack = inv.getItem(slot);
                if (stack == null || stack.getType().isAir()) continue;
                long value = plugin.getWorthManager().sellValue(stack.getType(), stack.getAmount());
                if (value > 0) inv.setItem(slot, null);
            }
            p.sendMessage("§a💰 Sold §f" + fmt(itemCount) + " §aitems for §f" + plugin.getEconomyManager().format(total) + "§a!");
            sellInventories.remove(u);
            p.closeInventory();
        } finally {
            sellProcessing.remove(u);
        }
    }

    public void closeSell(Player p) {
        UUID u = p.getUniqueId();
        if (sellProcessing.contains(u)) return;
        Inventory inv = sellInventories.remove(u);
        if (inv == null) return;
        restoreInputItems(p, inv);
    }

    public void returnSellItems(Player p) {
        Inventory inv = sellInventories.remove(p.getUniqueId());
        if (inv != null) restoreInputItems(p, inv);
    }

    private void restoreInputItems(Player p, Inventory inv) {
        for (int slot = 0; slot < SELL_INPUT_SLOTS; slot++) {
            ItemStack s = inv.getItem(slot);
            if (s == null || s.getType().isAir()) continue;
            inv.setItem(slot, null);
            for (ItemStack left : p.getInventory().addItem(s.clone()).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    private void updateSellDisplay(Player p, Inventory inv) {
        long total = 0;
        for (int slot = 0; slot < SELL_INPUT_SLOTS; slot++) {
            ItemStack s = inv.getItem(slot);
            if (s == null || s.getType().isAir()) continue;
            long value = plugin.getWorthManager().sellValue(s.getType(), s.getAmount());
            if (value > 0 && Long.MAX_VALUE - total >= value) total += value;
        }
        inv.setItem(47, icon(Material.EMERALD, "§a§l💰 TOTAL", List.of("§7Current sell value", "§f" + plugin.getEconomyManager().format(total))));
    }

    private String fmt(long n) { return String.format(Locale.US, "%,d", n); }

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
