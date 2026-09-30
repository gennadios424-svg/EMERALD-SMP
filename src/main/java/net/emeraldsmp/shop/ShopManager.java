package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.worth.WorthCategory;
import net.emeraldsmp.worth.WorthEntry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.block.ShulkerBox;

import java.util.*;

public final class ShopManager {
    public static final String MAIN = "shop_main";
    public static final String CATEGORY = "shop_category";
    public static final String ITEM = "shop_item";
    public static final String SELL_GUI = "sell_gui";

    private static final int ITEMS_PER_PAGE = 45;
    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15};
    private static final String[] CATEGORY_KEYS = {"blocks", "cpvp", "redstone", "food", "farm", "end"};
    private static final String[] CATEGORY_NAMES = {"§a🧱 Blocks", "§c⚔ CPVP", "§c🔴 Redstone", "§6🍖 Food", "§2🌾 Farm", "§5🟢 End"};
    private static final Material[] CATEGORY_ICONS = {
            Material.STONE, Material.NETHERITE_SWORD, Material.REDSTONE,
            Material.COOKED_BEEF, Material.WHEAT, Material.ENDER_CHEST
    };

    private final EmeraldSMP plugin;
    private final Map<UUID, ShopView> views = new HashMap<>();
    private final Map<UUID, Inventory> sellInventories = new HashMap<>();

    public ShopManager(EmeraldSMP plugin) { this.plugin = plugin; }

    public void reload() {
        views.clear();
    }

    public void openMain(Player p) {
        Inventory inv = plugin.getServer().createInventory(null, 27, "§2§l💚 EMERALD SMP SHOP");
        for (int i = 0; i < CATEGORY_SLOTS.length; i++) {
            inv.setItem(CATEGORY_SLOTS[i], icon(CATEGORY_ICONS[i], CATEGORY_NAMES[i],
                    List.of("§7Browse items available to buy")));
        }
        inv.setItem(22, icon(Material.BARRIER, "§cClose", List.of()));
        views.put(p.getUniqueId(), new ShopView(false, null, 0, List.of()));
        p.openInventory(inv);
    }

    public void openCategory(Player p, String categoryKey, int page) {
        List<ShopItem> items = configuredItems(categoryKey);
        int pages = Math.max(1, (items.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        int safePage = Math.max(0, Math.min(page, pages - 1));

        Inventory inv = plugin.getServer().createInventory(null, 54,
                "§2§l💚 SHOP §8• §f" + categoryDisplay(categoryKey) + " §8• §f" + (safePage + 1) + "/" + pages);

        int from = safePage * ITEMS_PER_PAGE;
        int to = Math.min(from + ITEMS_PER_PAGE, items.size());
        for (int i = from; i < to; i++) {
            inv.setItem(i - from, shopDisplay(items.get(i)));
        }

        inv.setItem(45, icon(Material.ARROW, "§e⬅ Back", List.of("§7Return to shop categories")));
        inv.setItem(48, icon(Material.ARROW, "§a⬅ Previous", List.of("§7Previous page")));
        inv.setItem(49, icon(Material.PAPER, "§fPage " + (safePage + 1) + "/" + pages,
                List.of("§7" + items.size() + " configured items")));
        inv.setItem(50, icon(Material.ARROW, "§aNext ➡", List.of("§7Next page")));
        inv.setItem(53, icon(Material.BARRIER, "§cClose", List.of()));

        views.put(p.getUniqueId(), new ShopView(false, categoryKey, safePage, items));
        p.openInventory(inv);
    }

    public void openItem(Player p, ShopItem item, String categoryKey, int page) {
        Inventory inv = plugin.getServer().createInventory(null, 27, "§2§l💚 BUY ITEM");
        ItemStack display = new ItemStack(item.material());
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(item.displayName());
            meta.setLore(List.of(
                    "§7Buy price: §a" + plugin.getEconomyManager().format(item.buyPrice()),
                    "§8Click a button to purchase"
            ));
            display.setItemMeta(meta);
        }
        inv.setItem(13, display);
        inv.setItem(10, buyButton(item, 1));
        inv.setItem(11, buyButton(item, 16));
        inv.setItem(12, buyButton(item, 32));
        inv.setItem(14, buyButton(item, 64));
        inv.setItem(18, icon(Material.ARROW, "§e⬅ Back", List.of("§7Return to this shop category")));
        inv.setItem(22, icon(Material.BARRIER, "§cClose", List.of()));
        views.put(p.getUniqueId(), new ShopView(false, categoryKey, page, List.of(item)));
        p.openInventory(inv);
    }

    public void openSell(Player p) {
        Inventory inv = plugin.getServer().createInventory(null, 54, "§2§l💚 SELL ITEMS");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler());
        }
        inv.setItem(49, icon(Material.EMERALD, "§a§lSELL", List.of(
                "§7Sell every supported item currently inside",
                "§7Shulker contents are counted too",
                "§eClick to sell"
        )));
        inv.setItem(53, icon(Material.BARRIER, "§cClose", List.of("§7Items will be returned")));
        sellInventories.put(p.getUniqueId(), inv);
        p.openInventory(inv);
    }

    public boolean isSellInventory(Player p, Inventory inv) {
        return sellInventories.get(p.getUniqueId()) == inv;
    }

    public void handleSellClick(Player p, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= 54) return;
        if (rawSlot == 49) {
            sellContents(p);
        } else if (rawSlot == 53) {
            returnSellItems(p);
            p.closeInventory();
        }
    }

    public void sellContents(Player p) {
        Inventory inv = sellInventories.get(p.getUniqueId());
        if (inv == null) return;

        List<ItemStack> original = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            ItemStack stack = inv.getItem(slot);
            original.add(stack == null ? null : stack.clone());
        }
        long total = calculateSellValue(inv, new HashSet<>());
        if (total <= 0) {
            p.sendMessage("§a💚 §2§lEmerald SMP §8» §cThere are no sellable items in the sell menu.");
            return;
        }

        // Remove everything from the sell area only after the complete value was calculated.
        for (int slot = 0; slot < 45; slot++) inv.setItem(slot, null);

        if (!plugin.getEconomyManager().deposit(p.getUniqueId(), total)) {
            for (ItemStack stack : original) {
                if (stack == null || stack.getType().isAir()) continue;
                Map<Integer, ItemStack> left = p.getInventory().addItem(stack);
                for (ItemStack drop : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
            }
            p.sendMessage("§a💚 §2§lEmerald SMP §8» §cThe transaction could not be completed; your items were returned.");
            return;
        }

        p.sendMessage("§a💚 §2§lEmerald SMP §8» §aSold items for §f" +
                plugin.getEconomyManager().format(total) + "§a.");
    }

    private long calculateSellValue(Inventory inv, Set<String> recursionGuard) {
        long total = 0L;
        for (int slot = 0; slot < 45; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack == null || stack.getType().isAir()) continue;
            long value = valueOfStack(stack, recursionGuard);
            if (Long.MAX_VALUE - total < value) return Long.MAX_VALUE;
            total += value;
        }
        return total;
    }

    private long valueOfStack(ItemStack stack, Set<String> recursionGuard) {
        Material material = stack.getType();
        long direct = plugin.getWorthManager().sellValue(material, stack.getAmount());

        if (isShulker(stack)) {
            BlockStateMeta meta = (BlockStateMeta) stack.getItemMeta();
            if (meta != null && meta.getBlockState() instanceof ShulkerBox shulker) {
                String guard = shulker.getPersistentDataContainer().toString() + ":" + material.name();
                if (recursionGuard.add(guard)) {
                    for (ItemStack inside : shulker.getInventory().getContents()) {
                        if (inside != null && !inside.getType().isAir()) {
                            long child = valueOfStack(inside, recursionGuard);
                            if (Long.MAX_VALUE - direct < child) return Long.MAX_VALUE;
                            direct += child;
                        }
                    }
                    recursionGuard.remove(guard);
                }
            }
        }
        return direct;
    }

    private boolean isShulker(ItemStack stack) {
        return stack.getItemMeta() instanceof BlockStateMeta meta && meta.getBlockState() instanceof ShulkerBox;
    }

    public void returnSellItems(Player p) {
        Inventory inv = sellInventories.remove(p.getUniqueId());
        if (inv == null) return;
        for (int slot = 0; slot < 45; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack == null || stack.getType().isAir()) continue;
            Map<Integer, ItemStack> left = p.getInventory().addItem(stack.clone());
            for (ItemStack drop : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
            inv.setItem(slot, null);
        }
    }

    public void closeSell(Player p) {
        if (sellInventories.containsKey(p.getUniqueId())) returnSellItems(p);
    }

    public ShopView view(Player p) { return views.get(p.getUniqueId()); }

    public ShopItem itemFor(Player p, int slot) {
        ShopView view = views.get(p.getUniqueId());
        if (view == null || view.items == null || slot < 0 || slot >= ITEMS_PER_PAGE) return null;
        int index = view.page * ITEMS_PER_PAGE + slot;
        return index >= 0 && index < view.items.size() ? (ShopItem) view.items.get(index) : null;
    }

    public boolean buy(Player p, ShopItem item, int qty) {
        if (qty <= 0 || item == null) return false;
        long unit = item.buyPrice();
        if (unit <= 0) return false;
        long total;
        try {
            total = Math.multiplyExact(unit, (long) qty);
        } catch (ArithmeticException ex) {
            return false;
        }
        if (plugin.getEconomyManager().getBalance(p.getUniqueId()) < total) return false;
        if (!hasSpace(p, item.material(), qty)) return false;
        if (!plugin.getEconomyManager().withdraw(p.getUniqueId(), total)) return false;

        Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(item.material(), qty));
        if (!left.isEmpty()) {
            plugin.getEconomyManager().deposit(p.getUniqueId(), total);
            return false;
        }
        return true;
    }

    private List<ShopItem> configuredItems(String categoryKey) {
        String path = "shop.categories." + categoryKey + ".items";
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);

        // Backwards compatibility with the previous Stage 3 name.
        if (section == null && categoryKey.equals("cpvp"))
            section = plugin.getConfig().getConfigurationSection("shop.categories.gear");

        List<ShopItem> result = new ArrayList<>();
        if (section == null) return result;

        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null || !s.getBoolean("enabled", true)) continue;
            Material material = Material.matchMaterial(s.getString("material", key));
            if (material == null || !material.isItem()) continue;

            long buy = s.getLong("buy", -1L);
            if (buy < 0) {
                WorthEntry worth = plugin.getWorthManager().get(material);
                buy = worth == null ? 0L : plugin.getWorthManager().buyValue(material, 1);
            }
            if (buy <= 0) continue;

            String name = s.getString("display-name", "§f" + pretty(material));
            List<String> lore = s.getStringList("lore");
            result.add(new ShopItem(key, material, name, buy, lore));
        }
        result.sort(Comparator.comparing(a -> a.material().name()));
        return result;
    }

    private String categoryDisplay(String key) {
        for (int i = 0; i < CATEGORY_KEYS.length; i++)
            if (CATEGORY_KEYS[i].equals(key)) return ChatColorless(CATEGORY_NAMES[i]);
        return key;
    }

    private String ChatColorless(String s) {
        return s.replaceAll("§.", "");
    }

    private ItemStack shopDisplay(ShopItem item) {
        ItemStack stack = new ItemStack(item.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add("§aBuy: §f" + plugin.getEconomyManager().format(item.buyPrice()) + " §7/ item");
            lore.addAll(item.lore());
            lore.add("§8Click to view purchase options");
            meta.setDisplayName(item.displayName());
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack buyButton(ShopItem item, int qty) {
        long total;
        try { total = Math.multiplyExact(item.buyPrice(), qty); }
        catch (ArithmeticException ex) { total = Long.MAX_VALUE; }
        return icon(Material.PAPER, "§eBuy " + qty, List.of("§7Price: §a" + plugin.getEconomyManager().format(total)));
    }

    private ItemStack filler() {
        ItemStack i = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = i.getItemMeta();
        if (m != null) { m.setDisplayName(" "); i.setItemMeta(m); }
        return i;
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean hasSpace(Player p, Material m, int qty) {
        int capacity = 0;
        for (ItemStack item : p.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) capacity += m.getMaxStackSize();
            else if (item.getType() == m) capacity += m.getMaxStackSize() - item.getAmount();
            if (capacity >= qty) return true;
        }
        return false;
    }

    private String pretty(Material m) {
        String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" "))
            if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return b.toString().trim();
    }

    public record ShopView(boolean sellMode, String category, int page, List<?> items) {}
    public record ShopItem(String key, Material material, String displayName, long buyPrice, List<String> lore) {}
}
