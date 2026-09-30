package net.emeraldsmp.worth;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

public final class WorthManager {
    private static final Set<Material> EXCLUDED = EnumSet.of(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR, Material.BEDROCK,
            Material.BARRIER, Material.LIGHT, Material.DEBUG_STICK, Material.KNOWLEDGE_BOOK,
            Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART, Material.JIGSAW, Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID, Material.END_PORTAL, Material.END_GATEWAY,
            Material.NETHER_PORTAL, Material.FIRE, Material.SOUL_FIRE
    );

    private final EmeraldSMP plugin;
    private final File file;
    private final Map<Material, WorthEntry> entries = new EnumMap<>(Material.class);
    private YamlConfiguration config;

    public WorthManager(EmeraldSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "worth.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder.");
        }
        if (!file.exists()) generateDefaultFile();
        reload();
    }

    public synchronized boolean reload() {
        try {
            if (!file.exists()) generateDefaultFile();
            YamlConfiguration next = YamlConfiguration.loadConfiguration(file);
            Map<Material, WorthEntry> parsed = new EnumMap<>(Material.class);
            ConfigurationSection worth = next.getConfigurationSection("worth");
            if (worth == null) throw new IllegalArgumentException("Missing 'worth' section.");

            for (String key : worth.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null || !isSupported(material)) {
                    plugin.getLogger().warning("[Worth] Ignoring invalid/unsupported material: " + key);
                    continue;
                }
                Object raw = worth.get(key);
                long value;
                try {
                    if (raw instanceof Number n) value = n.longValue();
                    else value = new BigDecimal(String.valueOf(raw)).longValueExact();
                } catch (Exception ex) {
                    plugin.getLogger().warning("[Worth] Invalid price for " + key + ": " + raw + " (keeping previous value)");
                    continue;
                }
                if (value < 0) {
                    plugin.getLogger().warning("[Worth] Negative price for " + key + ": " + value + " (keeping previous value)");
                    continue;
                }
                String categoryName = next.getString("categories." + key, defaultCategory(material).name());
                WorthCategory category;
                try { category = WorthCategory.valueOf(categoryName.toUpperCase(Locale.ROOT)); }
                catch (Exception ex) {
                    plugin.getLogger().warning("[Worth] Invalid category for " + key + ": " + categoryName + " (using " + defaultCategory(material).name() + ")");
                    category = defaultCategory(material);
                }
                boolean enabled = next.getBoolean("enabled." + key, true);
                parsed.put(material, new WorthEntry(material, value, category, enabled));
            }

            for (Material material : Material.values()) {
                if (isSupported(material) && !parsed.containsKey(material)) {
                    WorthEntry old = entries.get(material);
                    if (old != null) parsed.put(material, old);
                }
            }

            if (parsed.isEmpty()) throw new IllegalArgumentException("Worth file contains no valid supported items.");
            entries.clear();
            entries.putAll(parsed);
            config = next;
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "[Worth] Reload failed; previous valid data was kept.", ex);
            return false;
        }
    }

    public synchronized Collection<WorthEntry> all() {
        return List.copyOf(entries.values().stream().filter(WorthEntry::enabled).toList());
    }

    public synchronized WorthEntry get(Material material) { return entries.get(material); }

    public synchronized long value(Material material, int amount) {
        WorthEntry e = entries.get(material);
        if (e == null || !e.enabled() || amount < 0) return 0L;
        return Math.multiplyExact(e.worth(), (long) amount);
    }

    private boolean isSupported(Material m) {
        return m.isItem() && !m.isLegacy() && !EXCLUDED.contains(m) && !m.name().endsWith("_SPAWN_EGG");
    }

    private void generateDefaultFile() {
        YamlConfiguration out = new YamlConfiguration();
        out.set("version", 1);
        out.set("info", "Emerald SMP master /worth database. Prices are whole dollars and can be edited safely.");
        for (Material material : Material.values()) {
            if (!isSupported(material)) continue;
            long price = defaultPrice(material);
            out.set("worth." + material.name(), price);
            out.set("categories." + material.name(), defaultCategory(material).name());
            out.set("enabled." + material.name(), true);
        }
        try {
            out.save(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create worth.yml", ex);
        }
    }

    private WorthCategory defaultCategory(Material m) {
        String n = m.name();
        if (n.contains("NETHER") || n.contains("NETHERRACK") || n.contains("QUARTZ") || n.contains("BASALT") || n.contains("BLACKSTONE") || n.contains("GILDED_BLACKSTONE") || n.contains("CRIMSON") || n.contains("WARPED") || n.contains("SOUL")) return WorthCategory.NETHER;
        if (n.contains("END_") || n.startsWith("END") || n.contains("CHORUS") || n.contains("SHULKER")) return WorthCategory.END;
        if (n.contains("REDSTONE") || n.contains("REPEATER") || n.contains("COMPARATOR") || n.contains("PISTON") || n.contains("OBSERVER") || n.contains("DISPENSER") || n.contains("DROPPER") || n.contains("HOPPER") || n.contains("TARGET") || n.contains("TRIPWIRE") || n.contains("DAYLIGHT")) return WorthCategory.REDSTONE;
        if (n.contains("SWORD") || n.contains("AXE") || n.contains("BOW") || n.contains("CROSSBOW") || n.contains("TRIDENT") || n.contains("SHIELD") || n.contains("HELMET") || n.contains("CHESTPLATE") || n.contains("LEGGINGS") || n.contains("BOOTS") || n.contains("MACE") || n.contains("ARROW")) return WorthCategory.COMBAT;
        if (n.contains("PICKAXE") || n.contains("SHOVEL") || n.contains("HOE") || n.contains("SHEARS") || n.contains("FLINT_AND_STEEL") || n.contains("FISHING_ROD") || n.contains("BRUSH") || n.contains("BUCKET") || n.contains("COMPASS") || n.contains("CLOCK") || n.contains("LEAD") || n.contains("NAME_TAG")) return WorthCategory.TOOLS;
        if (m.isEdible()) return WorthCategory.FOOD;
        if (n.contains("SEEDS") || n.contains("SAPLING") || n.contains("WHEAT") || n.contains("CARROT") || n.contains("POTATO") || n.contains("BEETROOT") || n.contains("BAMBOO") || n.contains("SUGAR_CANE") || n.contains("KELP") || n.contains("VINE") || n.contains("MELON") || n.contains("PUMPKIN") || n.contains("COCOA")) return WorthCategory.FARMING;
        if (n.contains("BONE") || n.contains("ROTTEN_FLESH") || n.contains("STRING") || n.contains("SPIDER_EYE") || n.contains("GUNPOWDER") || n.contains("LEATHER") || n.contains("FEATHER") || n.contains("SLIME") || n.contains("PRISMARINE_SHARD") || n.contains("PRISMARINE_CRYSTALS") || n.contains("INK_SAC") || n.contains("BLAZE_ROD") || n.contains("ENDER_PEARL")) return WorthCategory.MOB_DROPS;
        if (n.contains("POTION") || n.contains("BREWING") || n.contains("BLAZE_POWDER") || n.contains("FERMENTED_SPIDER_EYE") || n.contains("GLISTERING_MELON") || n.contains("MAGMA_CREAM")) return WorthCategory.BREWING;
        if (n.contains("DIAMOND") || n.contains("EMERALD") || n.contains("IRON") || n.contains("GOLD") || n.contains("COPPER") || n.contains("NETHERITE") || n.contains("COAL") || n.contains("LAPIS") || n.contains("AMETHYST") || n.contains("RAW_")) return WorthCategory.RESOURCES;
        if (m.isBlock()) return WorthCategory.BLOCKS;
        return WorthCategory.MISC;
    }

    private long defaultPrice(Material m) {
        String n = m.name();
        long base = 2;
        if (n.contains("DIAMOND")) base = 500;
        else if (n.contains("NETHERITE")) base = 2500;
        else if (n.contains("EMERALD")) base = 300;
        else if (n.contains("ANCIENT_DEBRIS")) base = 1800;
        else if (n.contains("GOLD")) base = 70;
        else if (n.contains("IRON")) base = 35;
        else if (n.contains("COPPER")) base = 12;
        else if (n.contains("REDSTONE")) base = 8;
        else if (n.contains("LAPIS")) base = 12;
        else if (n.contains("COAL")) base = 10;
        else if (n.contains("QUARTZ")) base = 18;
        else if (n.contains("AMETHYST")) base = 16;
        else if (n.contains("OBSIDIAN")) base = 30;
        else if (n.contains("CRYING_OBSIDIAN")) base = 45;
        else if (n.contains("PRISMARINE")) base = 22;
        else if (n.contains("SCULK")) base = 28;
        else if (n.contains("SHULKER")) base = 1200;
        else if (n.contains("ELYTRA")) base = 10000;
        else if (n.contains("DRAGON")) base = 15000;
        else if (n.contains("BEACON")) base = 12000;
        else if (n.contains("TOTEM")) base = 3500;
        else if (n.contains("NETHER_STAR")) base = 8000;
        else if (n.contains("SPONGE")) base = 250;
        else if (n.contains("SLIME")) base = 30;
        else if (n.contains("BLAZE")) base = 45;
        else if (n.contains("ENDER")) base = 55;
        else if (n.contains("GUNPOWDER")) base = 18;
        else if (n.contains("GOLDEN_APPLE")) base = 450;
        else if (n.contains("ENCHANTED_GOLDEN_APPLE")) base = 5000;
        else if (m.isEdible()) base = 8;
        else if (m.isBlock()) base = 3;

        if (n.endsWith("_BLOCK") || n.endsWith("_ORE")) {
            if (n.contains("DIAMOND")) return 4500;
            if (n.contains("EMERALD")) return 2700;
            if (n.contains("GOLD")) return 630;
            if (n.contains("IRON")) return 315;
            if (n.contains("COPPER")) return 108;
            if (n.contains("REDSTONE")) return 72;
            if (n.contains("LAPIS")) return 108;
            return Math.max(3, base * 8);
        }
        if (n.contains("SWORD") || n.contains("PICKAXE") || n.contains("AXE") || n.contains("SHOVEL") || n.contains("HOE")) base = Math.max(base, 40);
        if (n.contains("NETHERITE")) base = Math.max(base, 2500);
        return base;
    }
}
