package net.emeraldsmp.drill;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class DrillManager implements Listener {
    private static final String TITLE = "§2§l💎 EMERALD DRILL";
    private final EmeraldSMP plugin;
    private final File file;
    private final Map<UUID, Stats> stats = new HashMap<>();
    private final NamespacedKey drillKey;

    public static final class Stats {
        int tier = 1;
        long level = 1, blocks, shards;
    }

    public DrillManager(EmeraldSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "drills.yml");
        this.drillKey = new NamespacedKey(plugin, "emerald-drill-tier");
    }

    public void load() {
        stats.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("players");
        if (root == null) return;
        for (String id : root.getKeys(false)) try {
            UUID uuid = UUID.fromString(id);
            Stats s = new Stats();
            s.tier = Math.max(1, y.getInt("players." + id + ".tier", 1));
            s.level = Math.max(1, y.getLong("players." + id + ".level", 1));
            s.blocks = Math.max(0, y.getLong("players." + id + ".blocks", 0));
            s.shards = Math.max(0, y.getLong("players." + id + ".shards", 0));
            stats.put(uuid, s);
        } catch (IllegalArgumentException ignored) {}
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Stats> e : stats.entrySet()) {
            String p = "players." + e.getKey();
            Stats s = e.getValue();
            y.set(p + ".tier", s.tier);
            y.set(p + ".level", s.level);
            y.set(p + ".blocks", s.blocks);
            y.set(p + ".shards", s.shards);
        }
        try { y.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Could not save drills.yml: " + ex.getMessage()); }
    }

    public void stop() { save(); }

    public ItemStack createItem(int tier) {
        tier = Math.max(1, Math.min(5, tier));
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        int speed = Math.max(1, plugin.getConfig().getInt("drills.tiers." + tier + ".efficiency", tier));
        meta.setDisplayName("§a💎 Emerald Drill §fTier " + roman(tier));
        meta.setLore(List.of(
            "§7Custom Emerald SMP mining drill",
            "§7Tier: §a" + tier,
            "§7Mining Speed: §f" + speed,
            "§7Right-click to view drill stats",
            "§8Emerald SMP"
        ));
        meta.getPersistentDataContainer().set(drillKey, PersistentDataType.INTEGER, tier);
        item.setItemMeta(meta);
        return item;
    }

    private int tier(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_PICKAXE || !item.hasItemMeta()) return 0;
        Integer t = item.getItemMeta().getPersistentDataContainer().get(drillKey, PersistentDataType.INTEGER);
        return t == null ? 0 : Math.max(1, t);
    }

    @EventHandler
    public void interact(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        int t = tier(e.getItem());
        if (t == 0) return;
        e.setCancelled(true);
        open(e.getPlayer(), t);
    }

    @EventHandler
    public void breakBlock(BlockBreakEvent e) {
        int t = tier(e.getPlayer().getInventory().getItemInMainHand());
        if (t == 0 || e.isCancelled()) return;
        Material m = e.getBlock().getType();
        if (!isMineable(m)) return;

        Stats s = stats.computeIfAbsent(e.getPlayer().getUniqueId(), k -> new Stats());
        s.tier = Math.max(s.tier, t);
        s.blocks++;

        long required = requiredFor(s.level, t);
        if (s.blocks % required == 0) {
            s.level++;
            long reward = plugin.getConfig().getLong("drills.tiers." + t + ".shards-per-level", 5L);
            if (reward > 0) {
                s.shards += reward;
                long current = plugin.getPlayerDataManager().getEmeraldShards(e.getPlayer().getUniqueId());
                plugin.getPlayerDataManager().setEmeraldShards(e.getPlayer().getUniqueId(), current + reward);
                e.getPlayer().sendMessage(ChatColor.GREEN + "💎 Drill level up! +" + reward + " Emerald Shards.");
            }
            save();
        }
    }

    private boolean isMineable(Material m) {
        return m.isBlock() && m.isSolid() && m != Material.BEDROCK && m != Material.BARRIER && m != Material.SPAWNER;
    }

    private long requiredFor(long level, int tier) {
        long base = Math.max(1, plugin.getConfig().getLong("drills.blocks-per-level", 100L));
        return Math.max(1, base / Math.max(1, tier));
    }

    private void open(Player p, int tier) {
        Stats s = stats.computeIfAbsent(p.getUniqueId(), k -> new Stats());
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        long required = requiredFor(s.level, tier);
        long progress = s.blocks % required;

        inv.setItem(4, item(Material.DIAMOND_PICKAXE, "§a💎 EMERALD DRILL", List.of(
            "§7Tier: §a" + tier,
            "§7Mining Speed: §f" + plugin.getConfig().getInt("drills.tiers." + tier + ".efficiency", tier),
            "§7Level: §f" + s.level
        )));
        inv.setItem(11, item(Material.EXPERIENCE_BOTTLE, "§b⛏️ Mining Progress", List.of(
            "§7Blocks Mined: §f" + s.blocks,
            "§7Progress: §a" + progress + "§7/§f" + required
        )));
        inv.setItem(13, item(Material.EMERALD, "§b💚 Shards Earned", List.of(
            "§7From this drill: §f" + s.shards
        )));
        inv.setItem(15, item(Material.ANVIL, "§e⚙️ UPGRADES", List.of(
            "§7Upgrade foundation ready",
            "§7Current Tier: §f" + tier,
            "§7Tier upgrades are configurable"
        )));
        inv.setItem(22, item(Material.BOOK, "§f📊 STATISTICS", List.of(
            "§7Blocks Mined: §f" + s.blocks,
            "§7Drill Level: §f" + s.level,
            "§7Tier: §f" + tier,
            "§7Shards Earned: §f" + s.shards
        )));
        p.openInventory(inv);
    }

    private ItemStack item(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        i.setItemMeta(meta);
        return i;
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player && e.getView().getTitle().equals(TITLE)) e.setCancelled(true);
    }

    private String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }
}