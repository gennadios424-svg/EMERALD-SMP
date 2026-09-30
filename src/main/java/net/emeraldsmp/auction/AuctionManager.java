package net.emeraldsmp.auction;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class AuctionManager {
    public enum SortMode {
        LOWEST_PRICE("LOWEST PRICE"), HIGHEST_PRICE("HIGHEST PRICE"),
        LOWEST_PER_ITEM("LOWEST PER ITEM"), HIGHEST_PER_ITEM("HIGHEST PER ITEM"),
        NEWEST("NEWEST"), OLDEST("OLDEST");
        public final String label;
        SortMode(String label) { this.label = label; }
    }

    public enum Category {
        ALL("All"), BLOCKS("Blocks"), COMBAT("Combat"), REDSTONE("Redstone"),
        FOOD("Food"), FARM("Farm"), RESOURCES("Resources"), MISC("Misc");
        public final String label;
        Category(String label) { this.label = label; }
    }

    public record Listing(UUID id, UUID seller, String sellerName, ItemStack item, long price, long createdAt) {
        public int amount() { return item.getAmount(); }
        public long perItem() { return price / Math.max(1, amount()); }
    }

    private final EmeraldSMP plugin;
    private final Map<UUID, Listing> listings = new LinkedHashMap<>();
    private final File file;

    public AuctionManager(EmeraldSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
        load();
    }

    public synchronized List<Listing> find(String query, Category category, SortMode sort) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        List<Listing> result = new ArrayList<>();
        for (Listing l : listings.values()) {
            String name = l.item().getType().name().toLowerCase(Locale.ROOT);
            if (!q.isBlank() && !name.contains(q) && !pretty(l.item().getType()).contains(q)) continue;
            if (category != Category.ALL && categoryOf(l.item().getType()) != category) continue;
            result.add(l);
        }
        Comparator<Listing> cmp = switch (sort) {
            case LOWEST_PRICE -> Comparator.comparingLong(Listing::price).thenComparingLong(Listing::createdAt);
            case HIGHEST_PRICE -> Comparator.comparingLong(Listing::price).reversed().thenComparingLong(Listing::createdAt);
            case LOWEST_PER_ITEM -> Comparator.comparingLong(Listing::perItem).thenComparingLong(Listing::price);
            case HIGHEST_PER_ITEM -> Comparator.comparingLong(Listing::perItem).reversed().thenComparingLong(Listing::price);
            case NEWEST -> Comparator.comparingLong(Listing::createdAt).reversed();
            case OLDEST -> Comparator.comparingLong(Listing::createdAt);
        };
        result.sort(cmp);
        return result;
    }

    public synchronized Listing get(UUID id) { return listings.get(id); }

    public synchronized boolean add(Player seller, ItemStack item, long price) {
        return add(seller.getUniqueId(), seller.getName(), item, price, UUID.randomUUID(), System.currentTimeMillis());
    }

    public synchronized boolean add(UUID seller, String sellerName, ItemStack item, long price) {
        return add(seller, sellerName, item, price, UUID.randomUUID(), System.currentTimeMillis());
    }

    private boolean add(UUID seller, String sellerName, ItemStack item, long price, UUID id, long created) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0 || price <= 0) return false;
        Listing listing = new Listing(id, seller, sellerName, item.clone(), price, created);
        listings.put(id, listing);
        if (!save()) { listings.remove(id); return false; }
        return true;
    }

    public synchronized boolean restore(Listing listing) {
        if (listing == null || listings.containsKey(listing.id())) return false;
        listings.put(listing.id(), listing);
        if (!save()) { listings.remove(listing.id()); return false; }
        return true;
    }

    public synchronized Listing remove(UUID id) {
        Listing listing = listings.remove(id);
        if (listing != null && !save()) { listings.put(id, listing); return null; }
        return listing;
    }

    public synchronized boolean save() {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("version", 1);
            for (Listing l : listings.values()) {
                String path = "listings." + l.id();
                yaml.set(path + ".seller", l.seller().toString());
                yaml.set(path + ".seller-name", l.sellerName());
                yaml.set(path + ".price", l.price());
                yaml.set(path + ".created", l.createdAt());
                yaml.set(path + ".item", l.item());
            }
            File tmp = new File(plugin.getDataFolder(), "auctions.yml.tmp");
            yaml.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save auction listings: " + ex.getMessage());
            return false;
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("listings");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                UUID seller = UUID.fromString(section.getString(key + ".seller"));
                String sellerName = section.getString(key + ".seller-name", "Unknown");
                long price = section.getLong(key + ".price");
                long created = section.getLong(key + ".created");
                ItemStack item = section.getItemStack(key + ".item");
                if (item == null || item.getType().isAir() || item.getAmount() <= 0 || price <= 0) continue;
                listings.put(id, new Listing(id, seller, sellerName, item, price, created));
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipped invalid auction listing " + key + ".");
            }
        }
    }

    public static Category categoryOf(Material m) {
        String n = m.name();
        if (n.contains("SWORD") || n.contains("AXE") || n.contains("BOW") || n.contains("CROSSBOW") ||
            n.contains("HELMET") || n.contains("CHESTPLATE") || n.contains("LEGGINGS") || n.contains("BOOTS") ||
            n.contains("SHIELD") || n.contains("TRIDENT") || n.contains("MACE") || n.contains("ARROW") ||
            n.contains("TOTEM")) return Category.COMBAT;
        if (n.contains("REDSTONE") || n.contains("PISTON") || n.contains("OBSERVER") || n.contains("HOPPER") ||
            n.contains("DISPENSER") || n.contains("DROPPER") || n.contains("COMPARATOR") || n.contains("REPEATER") ||
            n.contains("LEVER") || n.contains("BUTTON") || n.contains("PRESSURE_PLATE") || n.contains("RAIL")) return Category.REDSTONE;
        if (m.isEdible()) return Category.FOOD;
        if (n.contains("SEED") || n.contains("SAPLING") || n.contains("WHEAT") || n.contains("CARROT") ||
            n.contains("POTATO") || n.contains("BEETROOT") || n.contains("MELON") || n.contains("PUMPKIN") ||
            n.contains("SUGAR_CANE") || n.contains("CACTUS") || n.contains("COCOA") || n.contains("KELP") ||
            n.contains("BAMBOO") || n.contains("MUSHROOM") || n.contains("FLOWER")) return Category.FARM;
        if (m.isBlock()) return Category.BLOCKS;
        if (n.contains("DIAMOND") || n.contains("EMERALD") || n.contains("IRON") || n.contains("GOLD") ||
            n.contains("COPPER") || n.contains("NETHERITE") || n.contains("COAL") || n.contains("LAPIS") ||
            n.contains("QUARTZ") || n.contains("AMETHYST") || n.contains("INGOT") || n.contains("NUGGET") ||
            n.contains("RAW_") || n.contains("ORE")) return Category.RESOURCES;
        return Category.MISC;
    }

    private static String pretty(Material m) { return m.name().toLowerCase(Locale.ROOT).replace('_', ' '); }
}
