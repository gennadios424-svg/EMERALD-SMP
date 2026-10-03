package net.emeraldsmp.tools;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public final class EmeraldToolsManager implements Listener {
    public enum ToolType { SELL_AXE, TREE_CHOPPER, MONEY_HELMET }
    private static final long SIX_DAYS_MS = 6L * 24L * 60L * 60L * 1000L;
    private static final long HELMET_MS = 24L * 60L * 60L * 1000L;
    private final EmeraldSMP plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey issuedKey;
    private final NamespacedKey tokenKey;
    private final NamespacedKey receivedAtKey;
    private final NamespacedKey expiryKey;
    private final BukkitTask expiryTask;

    private static final Set<Material> LOGS = EnumSet.of(
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
        Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
        Material.CRIMSON_STEM, Material.WARPED_STEM,
        Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD, Material.JUNGLE_WOOD,
        Material.ACACIA_WOOD, Material.DARK_OAK_WOOD, Material.MANGROVE_WOOD, Material.CHERRY_WOOD,
        Material.CRIMSON_HYPHAE, Material.WARPED_HYPHAE,
        Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_BIRCH_LOG,
        Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
        Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG,
        Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_WARPED_STEM,
        Material.STRIPPED_OAK_WOOD, Material.STRIPPED_SPRUCE_WOOD, Material.STRIPPED_BIRCH_WOOD,
        Material.STRIPPED_JUNGLE_WOOD, Material.STRIPPED_ACACIA_WOOD, Material.STRIPPED_DARK_OAK_WOOD,
        Material.STRIPPED_MANGROVE_WOOD, Material.STRIPPED_CHERRY_WOOD,
        Material.STRIPPED_CRIMSON_HYPHAE, Material.STRIPPED_WARPED_HYPHAE
    );

    public EmeraldToolsManager(EmeraldSMP plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "emerald-custom-tool");
        this.issuedKey = new NamespacedKey(plugin, "emerald-crate-issued");
        this.tokenKey = new NamespacedKey(plugin, "emerald-crate-token");
        this.receivedAtKey = new NamespacedKey(plugin, "emerald-item-received-at");
        this.expiryKey = new NamespacedKey(plugin, "emerald-item-expires");
        this.expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAllExpiryLore, 20L, 20L);
    }

    public void stop() { if (expiryTask != null) expiryTask.cancel(); }

    public ItemStack createItem(ToolType type) {
        Material material = type == ToolType.MONEY_HELMET ? Material.NETHERITE_HELMET : Material.NETHERITE_AXE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name;
        List<String> lore = new ArrayList<>();
        switch (type) {
            case SELL_AXE -> {
                name = "§a§l💚 EMERALD SELL AXE";
                lore.add("§7Netherite-grade sell tool");
                lore.add("§7Break a block and instantly sell it");
                lore.add("§7Money goes directly to your balance");
            }
            case TREE_CHOPPER -> {
                name = "§a§l🌳 EMERALD TREE CHOPPER";
                lore.add("§7Netherite-grade tree tool");
                lore.add("§7Break one log to chop the connected tree");
                lore.add("§7Up to §f256 §7connected logs per swing");
            }
            default -> {
                name = "§a§l🪖 EMERALD MONEY HELMET";
                lore.add("§7Netherite helmet");
                lore.add("§a§l2× MONEY §7when selling items");
                lore.add("§7Works with /sell and the Emerald Sell Axe");
            }
        }
        // This creates the item definition only. It is deliberately NOT crate-authenticated
        // and therefore must never activate special abilities until activateCrateReward().
        lore.add("");
        lore.add("§2§lEMERALD SMP");
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
        meta.setCustomModelData(type == ToolType.MONEY_HELMET ? 7001 : (type == ToolType.SELL_AXE ? 7002 : 7003));
        item.setItemMeta(meta);
        return item;
    }

    /** Starts the expiry timer when an Emerald tool is actually received from a crate. */
    public ItemStack activateCrateReward(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        String raw = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (raw == null) return item;
        ToolType type;
        try { type = ToolType.valueOf(raw); } catch (IllegalArgumentException ex) { return item; }
        if (meta.getPersistentDataContainer().has(issuedKey, PersistentDataType.BYTE)
                && meta.getPersistentDataContainer().has(expiryKey, PersistentDataType.LONG)) return item;
        long issuedAt = System.currentTimeMillis();
        long expiresAt = issuedAt + (type == ToolType.MONEY_HELMET ? HELMET_MS : SIX_DAYS_MS);
        meta.getPersistentDataContainer().set(issuedKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(expiryKey, PersistentDataType.LONG, expiresAt);
        meta.getPersistentDataContainer().set(receivedAtKey, PersistentDataType.LONG, issuedAt);
        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(0, "§e⏳ Expires in: §f" + formatRemaining(expiresAt));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public boolean is(ItemStack item, ToolType type) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        String value = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (!type.name().equals(value)) return false;
        Byte issued = meta.getPersistentDataContainer().get(issuedKey, PersistentDataType.BYTE);
        String token = meta.getPersistentDataContainer().get(tokenKey, PersistentDataType.STRING);
        Long expiresAt = meta.getPersistentDataContainer().get(expiryKey, PersistentDataType.LONG);
        // Special abilities require BOTH crate authentication and an unexpired timestamp.
        // Name, lore, material, enchantments, /give and admin-created definitions alone are insufficient.
        return issued != null && issued == (byte) 1 && token != null && !token.isBlank()
                && expiresAt != null && System.currentTimeMillis() < expiresAt;
    }

    public String getExpiryDisplay(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        Long expiresAt = item.getItemMeta().getPersistentDataContainer().get(expiryKey, PersistentDataType.LONG);
        if (expiresAt == null) return null;
        if (System.currentTimeMillis() >= expiresAt) return "Expired";
        return formatRemaining(expiresAt);
    }

    public void refreshExpiryLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        Long expiresAt = meta.getPersistentDataContainer().get(expiryKey, PersistentDataType.LONG);
        if (expiresAt == null) return;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line != null && (line.startsWith("§e⏳ Expires in:") || line.startsWith("§c⏳ Expired")));
        int insert = lore.size();
        for (int i = 0; i < lore.size(); i++) if (lore.get(i).isEmpty()) { insert = i; break; }
        String line = System.currentTimeMillis() >= expiresAt ? "§c⏳ Expired" : "§e⏳ Expires in: §f" + formatRemaining(expiresAt);
        lore.add(insert, line);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private void refreshAllExpiryLore() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getStorageContents()) refreshExpiryLore(item);
            refreshExpiryLore(player.getInventory().getItemInMainHand());
            refreshExpiryLore(player.getInventory().getItemInOffHand());
            refreshExpiryLore(player.getInventory().getHelmet());
            refreshExpiryLore(player.getInventory().getChestplate());
            refreshExpiryLore(player.getInventory().getLeggings());
            refreshExpiryLore(player.getInventory().getBoots());
        }
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

    public boolean hasMoneyHelmet(Player player) {
        return player != null && is(player.getInventory().getHelmet(), ToolType.MONEY_HELMET);
    }

    public long multiplier(Player player, long base) {
        if (base <= 0L || !hasMoneyHelmet(player)) return base;
        if (base > Long.MAX_VALUE / 2L) return Long.MAX_VALUE;
        return base * 2L;
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (is(held, ToolType.SELL_AXE)) {
            event.setCancelled(true);
            sellBlock(player, event.getBlock(), held);
        } else if (is(held, ToolType.TREE_CHOPPER)) {
            event.setCancelled(true);
            chopTree(player, event.getBlock(), held);
        }
    }

    private void sellBlock(Player player, Block block, ItemStack tool) {
        Material material = block.getType();
        long base = plugin.getWorthManager().sellValue(material, 1);
        if (base <= 0L) {
            player.sendMessage("§c❌ This block has no sell value.");
            return;
        }
        long amount = multiplier(player, base);
        if (!plugin.getEconomyManager().deposit(player.getUniqueId(), amount)) {
            player.sendMessage("§c❌ The money transaction failed. The block was not removed.");
            return;
        }
        block.setType(Material.AIR, false);
        damageTool(player, tool, 1);
        player.sendMessage("§a💚 Sold §f1× " + pretty(material) + " §afor §f" + plugin.getEconomyManager().format(amount) + "§a.");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .55f, 1.35f);
    }

    private void chopTree(Player player, Block origin, ItemStack tool) {
        if (!LOGS.contains(origin.getType())) {
            player.sendMessage("§c❌ Start on a tree log.");
            return;
        }
        Set<Block> visited = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        List<Block> logs = new ArrayList<>();

        while (!queue.isEmpty() && logs.size() < 256) {
            Block current = queue.removeFirst();
            if (!visited.add(current) || !LOGS.contains(current.getType())) continue;
            logs.add(current);
            for (int dx=-1; dx<=1; dx++)
                for (int dy=-1; dy<=1; dy++)
                    for (int dz=-1; dz<=1; dz++)
                        if (dx != 0 || dy != 0 || dz != 0) {
                            Block next=current.getRelative(dx,dy,dz);
                            if (!visited.contains(next) && LOGS.contains(next.getType())) queue.addLast(next);
                        }
        }

        int broken=0;
        for (Block log : logs) {
            if (!LOGS.contains(log.getType())) continue;
            log.breakNaturally(tool);
            broken++;
        }
        if (broken > 0) {
            damageTool(player, tool, broken);
            player.sendMessage("§a🌳 Emerald Tree Chopper cut §f" + broken + " §alog blocks.");
            player.playSound(player.getLocation(),Sound.BLOCK_WOOD_BREAK,.7f,1.15f);
        }
    }

    private void damageTool(Player player, ItemStack item, int damage) {
        if (item == null || damage <= 0) return;
        ItemMeta meta=item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;
        int next=damageable.getDamage()+damage;
        if (next >= item.getType().getMaxDurability()) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return;
        }
        damageable.setDamage(next);
        item.setItemMeta(meta);
    }

    private String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_',' ');
    }
}
