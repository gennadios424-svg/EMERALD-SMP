package net.emeraldsmp.crates;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class CrateManager implements Listener {
    private static final List<String> TYPES = List.of("common","spawner","gold","crimson","emerald");
    private final EmeraldSMP plugin;
    private final File file;
    private final Map<String,String> crates = new LinkedHashMap<>();
    private final Map<UUID,Pending> pending = new HashMap<>();
    private final Set<UUID> opening = new HashSet<>();
    private final NamespacedKey crateKey;
    private final NamespacedKey keyKey;
    private final Random random = new Random();

    private record Pending(String type, String reward) {}

    public CrateManager(EmeraldSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "crates.yml");
        crateKey = new NamespacedKey(plugin, "emerald-crate");
        keyKey = new NamespacedKey(plugin, "emerald-crate-key");
    }

    public void load() {
        crates.clear();
        pending.clear();
        if (!file.exists()) return;
        var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        var s = y.getConfigurationSection("crates");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                if (k.equals("pending")) continue;
                String type = normalize(y.getString("crates." + k + ".type", "common"));
                crates.put(k, type);
            }
        }
        var p = y.getConfigurationSection("pending");
        if (p != null) {
            for (String id : p.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(id);
                    String type = normalize(y.getString("pending." + id + ".type", "common"));
                    String reward = y.getString("pending." + id + ".reward");
                    if (reward != null && !reward.isBlank()) pending.put(uuid, new Pending(type, reward));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void save() {
        var y = new org.bukkit.configuration.file.YamlConfiguration();
        for (var e : crates.entrySet()) {
            y.set("crates." + e.getKey() + ".type", e.getValue());
        }
        for (var e : pending.entrySet()) {
            y.set("pending." + e.getKey() + ".type", e.getValue().type());
            y.set("pending." + e.getKey() + ".reward", e.getValue().reward());
        }
        try { y.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Could not save crates.yml: " + ex.getMessage()); }
    }

    public void stop() { save(); }

    private String loc(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    private String type(Block b) { return crates.get(loc(b)); }

    public ItemStack createKey(String type, int amount) {
        type = normalize(type);
        amount = Math.max(1, Math.min(64, amount));
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a🔑 " + cap(type) + " Key");
        meta.setLore(List.of("§7Opens a §f" + cap(type) + " Crate", "§8Emerald SMP"));
        meta.getPersistentDataContainer().set(keyKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    private String keyType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyKey, PersistentDataType.STRING);
    }

    public void place(Player player, String type) {
        type = normalize(type);
        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType() != Material.AIR) {
            player.sendMessage(ChatColor.RED + "Look at an empty block within 5 blocks.");
            return;
        }
        String key = loc(target);
        if (crates.containsKey(key)) {
            player.sendMessage(ChatColor.RED + "A crate is already registered there.");
            return;
        }
        target.setType(Material.CHEST, false);
        crates.put(key, type);
        save();
        player.sendMessage(ChatColor.GREEN + "Placed " + cap(type) + " Crate.");
    }

    public void remove(Player player) {
        Block target = player.getTargetBlockExact(5);
        if (target == null || type(target) == null) {
            player.sendMessage(ChatColor.RED + "Look at a physical Emerald SMP crate within 5 blocks.");
            return;
        }
        String key = loc(target);
        crates.remove(key);
        target.setType(Material.AIR, false);
        save();
        player.sendMessage(ChatColor.GREEN + "Removed the crate.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        String type = type(block);
        if (type == null) return;
        event.setCancelled(true);
        open(event.getPlayer(), block, type);
    }

    private void open(Player player, Block block, String type) {
        UUID uuid = player.getUniqueId();
        if (opening.contains(uuid)) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        String heldType = keyType(held);
        if (!type.equals(heldType)) {
            player.sendMessage(ChatColor.RED + "❌ This key cannot open this crate!");
            return;
        }

        // Consume exactly one authenticated key before any reward is selected.
        held.setAmount(held.getAmount() - 1);
        String reward = selectReward(type);
        pending.put(uuid, new Pending(type, reward));
        opening.add(uuid);
        save();

        Inventory inv = Bukkit.createInventory(null, 27, "§2§l🎁 " + cap(type) + " CRATE");
        player.openInventory(inv);
        animate(player, type, inv);
    }

    private void animate(Player player, String type, Inventory inv) {
        int configured = Math.max(10, plugin.getConfig().getInt("crates.animation." + type, 35));
        long[] step = {0};
        int[] task = {-1};
        task[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!opening.contains(player.getUniqueId())) {
                Bukkit.getScheduler().cancelTask(task[0]);
                return;
            }
            long t = step[0]++;
            int phase = t < configured * 0.55 ? 0 : (t < configured * 0.8 ? 1 : 2);
            if (phase == 0 || phase == 1 || phase == 2) {
                String shown = randomDisplayReward(type);
                ItemStack icon = displayIcon(shown);
                for (int slot : new int[]{10,11,12,13,14,15,16}) inv.setItem(slot, icon);
                if (phase == 2 && t % 3 == 0) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.35f, 1.2f);
            }
            if (t >= configured) {
                Bukkit.getScheduler().cancelTask(task[0]);
                opening.remove(player.getUniqueId());
                deliverPending(player);
            }
        }, 0L, 2L);
    }

    private String randomDisplayReward(String type) {
        String reward = selectReward(type);
        if (reward.startsWith("$")) return "Money";
        if (reward.startsWith("shards:")) return "Emerald Shards";
        int colon = reward.indexOf(':');
        String material = colon > 0 ? reward.substring(0, colon) : reward;
        return material.toUpperCase(Locale.ROOT);
    }

    private String selectReward(String type) {
        List<String> entries = plugin.getConfig().getStringList("crates.rewards." + type);
        if (entries.isEmpty()) return "$0";

        double total = 0;
        List<WeightedReward> parsed = new ArrayList<>();
        for (String raw : entries) {
            String[] parts = raw.split("\\|", 2);
            String reward;
            double chance;
            if (parts.length == 2) {
                try {
                    chance = Double.parseDouble(parts[0]);
                    reward = parts[1].trim();
                } catch (NumberFormatException ex) {
                    chance = 1;
                    reward = raw.trim();
                }
            } else {
                chance = 1;
                reward = raw.trim();
            }
            if (chance <= 0 || reward.isBlank()) continue;
            parsed.add(new WeightedReward(reward, chance));
            total += chance;
        }
        if (parsed.isEmpty()) return "$0";
        double roll = random.nextDouble() * total;
        double cursor = 0;
        for (WeightedReward entry : parsed) {
            cursor += entry.chance();
            if (roll < cursor) return entry.reward();
        }
        return parsed.get(parsed.size() - 1).reward();
    }

    private record WeightedReward(String reward, double chance) {}

    private void deliverPending(Player player) {
        Pending reward = pending.remove(player.getUniqueId());
        if (reward == null) return;
        giveReward(player, reward.reward());
        player.sendMessage(ChatColor.GREEN + "🎉 " + cap(reward.type()) + " Crate reward: §f" + reward.reward());
        save();
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> deliverPending(event.getPlayer()));
    }

    private void giveReward(Player player, String reward) {
        if (reward.startsWith("$")) {
            try {
                long amount = Long.parseLong(reward.substring(1));
                if (amount > 0) plugin.getEconomyManager().depositToUuid(player.getUniqueId(), amount, player.getName());
            } catch (NumberFormatException ignored) {}
            return;
        }
        if (reward.startsWith("shards:")) {
            try {
                long amount = Long.parseLong(reward.substring("shards:".length()));
                if (amount > 0) {
                    long current = plugin.getPlayerDataManager().getEmeraldShards(player.getUniqueId());
                    plugin.getPlayerDataManager().setEmeraldShards(player.getUniqueId(), current + amount);
                }
            } catch (NumberFormatException ignored) {}
            return;
        }
        String[] parts = reward.split(":", 2);
        Material material = Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));
        if (material == null || material.isAir()) return;
        int amount = 1;
        if (parts.length == 2) {
            try { amount = Integer.parseInt(parts[1].trim()); }
            catch (NumberFormatException ignored) { return; }
        }
        amount = Math.max(1, amount);
        while (amount > 0) {
            int stack = Math.min(material.getMaxStackSize(), amount);
            ItemStack item = new ItemStack(material, stack);
            Map<Integer, ItemStack> left = player.getInventory().addItem(item);
            for (ItemStack extra : left.values()) player.getWorld().dropItemNaturally(player.getLocation(), extra);
            amount -= stack;
        }
    }

    private ItemStack displayIcon(String shown) {
        Material m = switch (shown) {
            case "Money" -> Material.GOLD_INGOT;
            case "Emerald Shards" -> Material.EMERALD;
            default -> Material.matchMaterial(shown);
        };
        if (m == null || m.isAir()) m = Material.CHEST;
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f" + shown);
        item.setItemMeta(meta);
        return item;
    }

    private String normalize(String type) {
        type = type == null ? "common" : type.toLowerCase(Locale.ROOT);
        return TYPES.contains(type) ? type : "common";
    }

    private String cap(String type) {
        return type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakCrate(BlockBreakEvent event) {
        String type = type(event.getBlock());
        if (type == null) return;
        if (!event.getPlayer().hasPermission("emerald.admin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Only an admin can remove a physical crate.");
            return;
        }
        crates.remove(loc(event.getBlock()));
        event.getBlock().setType(Material.AIR, false);
        save();
    }

    public boolean isCrate(Block block) { return type(block) != null; }
}