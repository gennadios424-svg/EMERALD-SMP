package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.worth.WorthCategory;
import net.emeraldsmp.worth.WorthEntry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class ShopManager {
    public static final String MAIN = "shop_main";
    public static final String CATEGORY = "shop_category";
    public static final String ITEM = "shop_item";
    private static final int ITEMS_PER_PAGE = 45;
    private final EmeraldSMP plugin;
    private final Map<UUID, ShopView> views = new HashMap<>();

    public ShopManager(EmeraldSMP plugin) { this.plugin = plugin; }

    public void reload() { views.clear(); }

    public void openMain(Player p) {
        Inventory inv = plugin.getServer().createInventory(null, 27, "§2§l💚 EMERALD SMP SHOP");
        int[] categorySlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25};
        WorthCategory[] categories = WorthCategory.values();
        for (int i = 0; i < categorySlots.length && i < categories.length; i++) {
            WorthCategory category = categories[i];
            inv.setItem(categorySlots[i], icon(categoryIcon(category), "§a" + category.displayName(),
                    List.of("§7Browse items in this category")));
        }
        inv.setItem(22, icon("BARRIER", "§cClose", List.of()));
        views.put(p.getUniqueId(), new ShopView(false, null, 0, null));
        p.openInventory(inv);
    }

    public void openCategory(Player p, WorthCategory category, int page, boolean sellMode) {
        List<WorthEntry> items = plugin.getWorthManager().all().stream()
                .filter(e -> e.category() == category)
                .sorted(Comparator.comparing(e -> e.material().name()))
                .toList();
        if (items.isEmpty()) return;
        int pages = Math.max(1, (items.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = plugin.getServer().createInventory(null, 54,
                (sellMode ? "§2§l💚 SELL §8• §f" : "§2§l💚 SHOP §8• §f") +
                        category.displayName() + " §8• §f" + (safePage + 1) + "/" + pages);

        int from = safePage * ITEMS_PER_PAGE;
        int to = Math.min(from + ITEMS_PER_PAGE, items.size());
        for (int i = from; i < to; i++) {
            WorthEntry entry = items.get(i);
            inv.setItem(i - from, display(entry, sellMode));
        }
        inv.setItem(45, icon("ARROW", "§e⬅ Back", List.of("§7Return to categories")));
        inv.setItem(48, icon("ARROW", "§a⬅ Previous", List.of("§7Previous page")));
        inv.setItem(49, icon("PAPER", "§fPage " + (safePage + 1) + "/" + pages, List.of("§7" + items.size() + " items")));
        inv.setItem(50, icon("ARROW", "§aNext ➡", List.of("§7Next page")));
        inv.setItem(53, icon("BARRIER", "§cClose", List.of()));
        views.put(p.getUniqueId(), new ShopView(sellMode, category, safePage, items));
        p.openInventory(inv);
    }

    public void openItem(Player p, WorthEntry entry, boolean sellMode) {
        Inventory inv = plugin.getServer().createInventory(null, 27,
                sellMode ? "§2§l💚 SELL ITEM" : "§2§l💚 BUY ITEM");
        inv.setItem(13, display(entry, sellMode));
        if (!sellMode) {
            inv.setItem(10, icon("PAPER", "§eBuy 1", List.of("§7Price: §f$" + plugin.getWorthManager().buyValue(entry.material(), 1))));
            inv.setItem(11, icon("PAPER", "§eBuy 16", List.of("§7Price: §f$" + plugin.getWorthManager().buyValue(entry.material(), 16))));
            inv.setItem(12, icon("PAPER", "§eBuy 32", List.of("§7Price: §f$" + plugin.getWorthManager().buyValue(entry.material(), 32))));
            inv.setItem(14, icon("PAPER", "§eBuy 64", List.of("§7Price: §f$" + plugin.getWorthManager().buyValue(entry.material(), 64))));
        } else {
            inv.setItem(10, icon("GOLD_INGOT", "§aSell 1", List.of("§7Value: §f$" + plugin.getWorthManager().sellValue(entry.material(), 1))));
            inv.setItem(11, icon("GOLD_INGOT", "§aSell 16", List.of("§7Value: §f$" + plugin.getWorthManager().sellValue(entry.material(), 16))));
            inv.setItem(12, icon("GOLD_INGOT", "§aSell 32", List.of("§7Value: §f$" + plugin.getWorthManager().sellValue(entry.material(), 32))));
            inv.setItem(14, icon("GOLD_INGOT", "§aSell 64", List.of("§7Value: §f$" + plugin.getWorthManager().sellValue(entry.material(), 64))));
            inv.setItem(16, icon("GOLD_BLOCK", "§aSell All", List.of("§7Sell all of this item in your inventory")));
        }
        inv.setItem(18, icon("ARROW", "§e⬅ Back", List.of()));
        inv.setItem(22, icon("BARRIER", "§cClose", List.of()));
        views.put(p.getUniqueId(), new ShopView(sellMode, entry.category(), 0, List.of(entry)));
        p.openInventory(inv);
    }

    public WorthEntry entryFor(Player p, int slot) {
        ShopView view = views.get(p.getUniqueId());
        if (view == null || view.items == null || slot < 0 || slot >= ITEMS_PER_PAGE) return null;
        int index = view.page * ITEMS_PER_PAGE + slot;
        return index >= 0 && index < view.items.size() ? view.items.get(index) : null;
    }

    public ShopView view(Player p) { return views.get(p.getUniqueId()); }

    public boolean buy(Player p, WorthEntry entry, int qty) {
        if (qty <= 0) return false;
        long total = plugin.getWorthManager().buyValue(entry.material(), qty);
        if (total <= 0 || plugin.getEconomyManager().getBalance(p.getUniqueId()) < total) return false;
        if (!hasSpace(p, entry.material(), qty)) return false;
        if (!plugin.getEconomyManager().withdraw(p.getUniqueId(), total)) return false;
        Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(entry.material(), qty));
        if (!left.isEmpty()) {
            plugin.getEconomyManager().deposit(p.getUniqueId(), total);
            return false;
        }
        return true;
    }

    public boolean sell(Player p, WorthEntry entry, int qty) {
        if (qty <= 0 || !hasItems(p, entry.material(), qty)) return false;
        long total = plugin.getWorthManager().sellValue(entry.material(), qty);
        if (total <= 0) return false;
        remove(p, entry.material(), qty);
        if (!plugin.getEconomyManager().deposit(p.getUniqueId(), total)) {
            p.getInventory().addItem(new ItemStack(entry.material(), qty));
            return false;
        }
        return true;
    }

    public int count(Player p, Material material) {
        int count = 0;
        for (ItemStack item : p.getInventory().getStorageContents())
            if (item != null && item.getType() == material) count += item.getAmount();
        return count;
    }

    private boolean hasSpace(Player p, Material m, int qty) {
        int capacity = 0;
        for (ItemStack item : p.getInventory().getStorageContents()) {
            if (item == null) capacity += m.getMaxStackSize();
            else if (item.getType() == m) capacity += m.getMaxStackSize() - item.getAmount();
            if (capacity >= qty) return true;
        }
        return false;
    }

    private boolean hasItems(Player p, Material m, int qty) { return count(p, m) >= qty; }

    private void remove(Player p, Material m, int qty) {
        for (int i = 0; i < p.getInventory().getSize() && qty > 0; i++) {
            ItemStack stack = p.getInventory().getItem(i);
            if (stack == null || stack.getType() != m) continue;
            int take = Math.min(qty, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            qty -= take;
        }
    }

    private ItemStack display(WorthEntry entry, boolean sellMode) {
        ItemStack item = new ItemStack(entry.material());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f" + pretty(entry.material()));
        long buy = plugin.getWorthManager().buyValue(entry.material(), 1);
        long sell = plugin.getWorthManager().sellValue(entry.material(), 1);
        meta.setLore(List.of(
                "§aWorth: §f$" + entry.worth() + " §7/ item",
                "§aBuy: §f$" + buy,
                "§cSell: §f$" + sell,
                "§7Category: §f" + entry.category().displayName(),
                sellMode ? "§8Click to sell" : "§8Click to buy"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack icon(String material, String name, List<String> lore) {
        Material m = Material.matchMaterial(material);
        if (m == null) m = Material.PAPER;
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String pretty(Material m) {
        String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) {
            if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return b.toString().trim();
    }

    private String categoryIcon(WorthCategory c) {
        return switch (c) {
            case BLOCKS, BUILDING -> "STONE";
            case RESOURCES -> "DIAMOND";
            case REDSTONE -> "REDSTONE";
            case COMBAT -> "NETHERITE_SWORD";
            case TOOLS -> "DIAMOND_PICKAXE";
            case FOOD -> "COOKED_BEEF";
            case FARMING -> "WHEAT";
            case MOB_DROPS -> "BONE";
            case BREWING -> "BREWING_STAND";
            case NETHER -> "NETHERRACK";
            case END -> "ENDER_EYE";
            case MISC -> "CHEST";
        };
    }

    public void clear(Player p) { views.remove(p.getUniqueId()); }

    public record ShopView(boolean sellMode, WorthCategory category, int page, List<WorthEntry> items) {}
}
