package net.emeraldsmp.spawner;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SpawnerManager implements Listener {
    public static final String TYPE_SKELETON = "skeleton";
    public static final String TYPE_ZOMBIE = "zombie";
    public static final String TYPE_SPIDER = "spider";
    public static final String TYPE_CREEPER = "creeper";
    private static final List<String> TYPES = List.of(TYPE_SKELETON, TYPE_ZOMBIE, TYPE_SPIDER, TYPE_CREEPER);

    private final EmeraldSMP plugin;
    private final File file;
    private final Map<String, Data> spawners = new ConcurrentHashMap<>();
    private final Map<UUID, String> open = new HashMap<>();
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private final Set<String> collecting = ConcurrentHashMap.newKeySet();
    private final NamespacedKey typeKey;
    private final NamespacedKey stackKey;
    private final NamespacedKey hologramKey;
    private BukkitTask task;

    public static final class Data {
        String world;
        int x, y, z;
        UUID owner;
        String type;
        int amount = 1;
        long lastCycle;
        final Map<Material, Long> stored = new EnumMap<>(Material.class);
        final Set<Material> enabled = EnumSet.noneOf(Material.class);

        Data(String world, int x, int y, int z, UUID owner, String type) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.owner = owner;
            this.type = normalizeType(type);
            this.lastCycle = System.currentTimeMillis();
            this.enabled.addAll(defaultDrops(this.type));
            for (Material m : defaultDrops(this.type)) this.stored.put(m, 0L);
        }

        String key() { return world + ":" + x + ":" + y + ":" + z; }
        Location loc() { World w = Bukkit.getWorld(world); return w == null ? null : new Location(w, x, y, z); }
        Location holoLoc() { World w = Bukkit.getWorld(world); return w == null ? null : new Location(w, x + 0.5, y + 1.65, z + 0.5); }
    }

    public SpawnerManager(EmeraldSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawners.yml");
        this.typeKey = new NamespacedKey(plugin, "emerald-spawner");
        this.stackKey = new NamespacedKey(plugin, "emerald-spawner-stack");
        this.hologramKey = new NamespacedKey(plugin, "emerald-spawner-hologram");
    }

    public void load() {
        spawners.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("spawners");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            try {
                String p = "spawners." + k;
                String ownerString = y.getString(p + ".owner");
                if (ownerString == null) continue;
                String type = normalizeType(y.getString(p + ".type", TYPE_SKELETON));
                Data d = new Data(y.getString(p + ".world", "world"), y.getInt(p + ".x"), y.getInt(p + ".y"), y.getInt(p + ".z"), UUID.fromString(ownerString), type);
                d.amount = clamp(y.getInt(p + ".amount", 1), 1, 64);
                d.lastCycle = Math.max(0L, y.getLong(p + ".last-cycle", System.currentTimeMillis()));
                for (Material m : defaultDrops(type)) {
                    d.stored.put(m, nonNegative(y.getLong(p + ".drops." + m.name().toLowerCase(Locale.ROOT), 0L)));
                    d.enabled.remove(m);
                    if (y.getBoolean(p + ".drops-enabled." + m.name().toLowerCase(Locale.ROOT), true)) d.enabled.add(m);
                }
                spawners.put(d.key(), d);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipped invalid spawner entry: " + k);
            }
        }
        Bukkit.getScheduler().runTask(plugin, this::removeAllHolograms);
    }

    public synchronized boolean save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Data d : spawners.values()) {
            String p = "spawners." + d.key();
            y.set(p + ".world", d.world);
            y.set(p + ".x", d.x);
            y.set(p + ".y", d.y);
            y.set(p + ".z", d.z);
            y.set(p + ".owner", d.owner.toString());
            y.set(p + ".type", d.type);
            y.set(p + ".amount", d.amount);
            y.set(p + ".last-cycle", d.lastCycle);
            for (Material m : defaultDrops(d.type)) {
                String key = m.name().toLowerCase(Locale.ROOT);
                y.set(p + ".drops." + key, Math.max(0L, d.stored.getOrDefault(m, 0L)));
                y.set(p + ".drops-enabled." + key, d.enabled.contains(m));
            }
        }
        try { y.save(file); return true; } catch (IOException e) { plugin.getLogger().warning("Could not save spawners.yml: " + e.getMessage()); return false; }
    }

    public void start() { task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L); }
    public void stop() { if (task != null) task.cancel(); save(); }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Data d : new ArrayList<>(spawners.values())) {
            World w = Bukkit.getWorld(d.world);
            if (w == null || !w.isChunkLoaded(d.x >> 4, d.z >> 4)) continue;
            long interval = intervalMillis(d.type);
            long elapsed = Math.max(0L, now - d.lastCycle);
            long cycles = Math.min(1000L, elapsed / interval);
            if (cycles <= 0) continue;
            d.lastCycle += cycles * interval;
            produce(d, productionPerCycle(d.type, d.amount), cycles);
            changed = true;
        }
        if (changed) save();
    }

    private void produce(Data d, long mobs, long cycles) {
        long total = safeMultiply(mobs, cycles);
        if (total <= 0) return;
        for (Material m : defaultDrops(d.type)) {
            if (!d.enabled.contains(m)) continue;
            long generated = generatedDropCount(d.type, m, total);
            d.stored.put(m, safeAdd(d.stored.getOrDefault(m, 0L), generated));
        }
    }

    private long generatedDropCount(String type, Material material, long mobs) {
        Random r = ThreadLocalRandom.current();
        long total = 0;
        for (long i = 0; i < mobs; i++) {
            int amount = 0;
            if (type.equals(TYPE_SKELETON)) {
                if (material == Material.BONE || material == Material.ARROW) amount = 1 + r.nextInt(3);
                else if (material == Material.BOW && r.nextDouble() < 0.085D) amount = 1;
            } else if (type.equals(TYPE_ZOMBIE)) {
                if (material == Material.ROTTEN_FLESH) amount = 1 + r.nextInt(3);
                else if (material == Material.IRON_INGOT && r.nextDouble() < 0.025D) amount = 1;
                else if (material == Material.CARROT && r.nextDouble() < 0.025D) amount = 1;
                else if (material == Material.POTATO && r.nextDouble() < 0.025D) amount = 1;
            } else if (type.equals(TYPE_SPIDER)) {
                if (material == Material.STRING) amount = 1 + r.nextInt(3);
                else if (material == Material.SPIDER_EYE && r.nextDouble() < 0.333D) amount = 1;
            } else if (type.equals(TYPE_CREEPER)) {
                if (material == Material.GUNPOWDER) amount = 1 + r.nextInt(3);
            }
            total = safeAdd(total, amount);
            if (total == Long.MAX_VALUE) break;
        }
        return total;
    }

    private long productionPerCycle(String type, int stack) {
        return safeMultiply(Math.max(1L, plugin.getConfig().getLong("spawners." + type + ".amount", 8L)), stack);
    }

    private long intervalMillis(String type) {
        return Math.max(1000L, plugin.getConfig().getLong("spawners." + type + ".interval-seconds", 12L) * 1000L);
    }

    public ItemStack createItem(String type, int stack) {
        type = normalizeType(type);
        stack = clamp(stack, 1, 64);
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a§l💚 EMERALD " + pretty(type) + " SPAWNER");
        meta.setLore(List.of("§7Emerald SMP custom spawner", "§e⚡ " + productionPerCycle(type, stack) + " " + pretty(type) + "s / " + (intervalMillis(type) / 1000L) + "s", "§7Stack: §a" + stack + "x", "§8Right-click to manage"));
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(stackKey, PersistentDataType.INTEGER, stack);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createItem(int stack) { return createItem(TYPE_SKELETON, stack); }

    private boolean isItem(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) return false;
        String type = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return TYPES.contains(normalizeType(type));
    }

    private String itemType(ItemStack item) {
        if (!isItem(item)) return TYPE_SKELETON;
        String type = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return normalizeType(type);
    }

    private int itemStack(ItemStack item) {
        if (!isItem(item)) return 1;
        Integer value = item.getItemMeta().getPersistentDataContainer().get(stackKey, PersistentDataType.INTEGER);
        return clamp(value == null ? 1 : value, 1, 64);
    }

    private String key(Block b) { return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ(); }

    private void configurePhysicalSpawner(Data d) {
        Location l = d.loc();
        if (l == null || l.getBlock().getType() != Material.SPAWNER) return;
        BlockState state = l.getBlock().getState();
        if (!(state instanceof CreatureSpawner cs)) return;
        cs.setSpawnedType(entityType(d.type));
        cs.setMinSpawnDelay(Integer.MAX_VALUE);
        cs.setMaxSpawnDelay(Integer.MAX_VALUE);
        cs.setDelay(Integer.MAX_VALUE);
        cs.setMaxNearbyEntities(0);
        cs.update(true, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void place(BlockPlaceEvent e) {
        if (!isItem(e.getItemInHand()) || e.getBlockPlaced().getType() != Material.SPAWNER) return;
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();
        String type = itemType(e.getItemInHand());
        int incoming = itemStack(e.getItemInHand());
        Data existing = findAdjacentOwned(b, p.getUniqueId(), type);
        if (existing != null) {
            int old = existing.amount;
            existing.amount = clamp(existing.amount + incoming, 1, 64);
            b.setType(Material.AIR, false);
            if (existing.amount == old) { p.sendMessage("§cThis spawner stack is already at the 64x limit."); return; }
            configurePhysicalSpawner(existing); save(); updateHologram(existing);
            p.sendMessage("§a🧟 " + pretty(type) + " spawner stacked: §f" + existing.amount + "x");
            return;
        }
        Data d = new Data(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), p.getUniqueId(), type);
        d.amount = incoming;
        spawners.put(d.key(), d);
        configurePhysicalSpawner(d);
        save();
        open(p, d);
        p.sendMessage("§a🧟 Emerald " + pretty(type) + " Spawner placed.");
    }

    private Data findAdjacentOwned(Block b, UUID owner, String type) {
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Data d = spawners.get(key(b.getRelative(face)));
            if (d != null && d.owner.equals(owner) && d.type.equals(type)) return d;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.SPAWNER) return;
        Data d = spawners.get(key(b));
        if (d == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (isItem(e.getItem())) {
            if (!canManage(p, d)) { p.sendMessage("§cOnly the spawner owner can add to it."); return; }
            String incomingType = itemType(e.getItem());
            if (!incomingType.equals(d.type)) { p.sendMessage("§cOnly matching spawner types can be stacked."); return; }
            int newAmount = clamp(d.amount + itemStack(e.getItem()), 1, 64);
            int added = newAmount - d.amount;
            if (added <= 0) { p.sendMessage("§cSpawner stack is already at 64x."); return; }
            d.amount = newAmount;
            e.getItem().setAmount(Math.max(0, e.getItem().getAmount() - 1));
            configurePhysicalSpawner(d); save(); open(p, d);
            return;
        }
        if (!canManage(p, d)) { p.sendMessage("§cOnly the spawner owner can manage this spawner."); return; }
        open(p, d);
    }

    private boolean canManage(Player p, Data d) { return d.owner.equals(p.getUniqueId()) || p.isOp(); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void breakBlock(BlockBreakEvent e) {
        Data d = spawners.get(key(e.getBlock()));
        if (d == null) return;
        Player p = e.getPlayer();
        if (!canManage(p, d)) { e.setCancelled(true); p.sendMessage("§cOnly the spawner owner can break this spawner."); return; }
        if (!isPickaxe(p.getInventory().getItemInMainHand().getType())) { e.setCancelled(true); p.sendMessage("§cUse a pickaxe to break an Emerald Spawner."); return; }
        e.setDropItems(false);
        spawners.remove(d.key());
        removeHolograms(d);
        save();
        giveOrDrop(p, createItem(d.type, d.amount));
        for (Material m : defaultDrops(d.type)) giveStoredOrDrop(p, m, d.stored.getOrDefault(m, 0L));
        p.sendMessage("§a🧟 Spawner broken. Stored drops returned.");
    }

    private boolean isPickaxe(Material m) { return m.name().endsWith("_PICKAXE"); }

    private void giveStoredOrDrop(Player p, Material mat, long amount) {
        long left = Math.max(0L, amount);
        while (left > 0) {
            int n = (int) Math.min(mat.getMaxStackSize(), left);
            Map<Integer, ItemStack> rem = p.getInventory().addItem(new ItemStack(mat, n));
            long returned = rem.values().stream().mapToLong(ItemStack::getAmount).sum();
            long accepted = n - returned;
            left -= accepted;
            if (returned > 0) { for (ItemStack x : rem.values()) p.getWorld().dropItemNaturally(p.getLocation(), x); break; }
        }
    }

    private void giveOrDrop(Player p, ItemStack item) { if (item != null && item.getAmount() > 0) for (ItemStack x : p.getInventory().addItem(item).values()) p.getWorld().dropItemNaturally(p.getLocation(), x); }

    private void open(Player p, Data d) {
        Inventory inv = Bukkit.createInventory(null, 45, mainTitle(d.type));
        fill(inv);
        long rate = productionPerCycle(d.type, d.amount);
        inv.setItem(4, item(iconFor(d.type), "§a§l🧟 " + pretty(d.type) + " SPAWNER", List.of("§7Emerald SMP custom spawner", "§fStack: §a" + d.amount + "x", "§e⚡ Production: §f" + rate + " " + pretty(d.type) + "s / " + (intervalMillis(d.type) / 1000L) + "s")));
        inv.setItem(13, item(Material.CHEST, "§a§l📦 STORED DROPS", storedLore(d)));
        inv.setItem(22, item(Material.EMERALD_BLOCK, "§a§l📦 COLLECT", List.of("§7Collect every stored physical drop", "§7Only items that fit are removed", "§8No auto-sell • No automatic money")));
        inv.setItem(23, item(Material.CHEST, "§a§l📦 DROP ALL", List.of("§7Drop all stored items into the world", "§7Items appear safely in front of you", "§8Uses normal Minecraft stack sizes")));
        inv.setItem(31, item(Material.COMPARATOR, "§b§l⚙ SETTINGS", List.of("§7Choose which drops are stored", "§8Settings are secondary")));
        inv.setItem(40, item(Material.BARRIER, "§c§l✕ CLOSE", List.of()));
        p.openInventory(inv);
        // Register the new inventory only after openInventory() has fired the close event
        // for the previous GUI. This prevents that close event from erasing the new session.
        open.put(p.getUniqueId(), d.key());
        openInventories.put(p.getUniqueId(), inv);
    }

    private List<String> storedLore(Data d) {
        List<String> lore = new ArrayList<>();
        for (Material m : defaultDrops(d.type)) lore.add("§f" + dropIcon(m) + " " + prettyMaterial(m) + ": §a" + fmt(d.stored.getOrDefault(m, 0L)));
        return lore;
    }

    private void settings(Player p, Data d) {
        Inventory inv = Bukkit.createInventory(null, 27, settingsTitle(d.type));
        fill(inv);
        Material[] drops = defaultDrops(d.type).toArray(new Material[0]);
        for (int i = 0; i < drops.length; i++) {
            Material m = drops[i];
            inv.setItem(10 + i * 2, item(m, "§f" + dropIcon(m) + " " + prettyMaterial(m) + ": " + (d.enabled.contains(m) ? "§aON" : "§cOFF"), List.of("§7Click to toggle stored drop", "§7Stored: §f" + fmt(d.stored.getOrDefault(m, 0L)))));
        }
        inv.setItem(22, item(Material.ARROW, "§a§l← BACK", List.of("§7Return to spawner")));
        p.openInventory(inv);
        // Re-register after the previous GUI's close event has completed.
        open.put(p.getUniqueId(), d.key());
        openInventories.put(p.getUniqueId(), inv);
    }

    private void collect(Player p, Data d) {
        String k = d.key();
        if (!collecting.add(k)) { p.sendMessage("§e📦 Collection already processing."); return; }
        try {
            long moved = 0;
            for (Material m : defaultDrops(d.type)) {
                long before = d.stored.getOrDefault(m, 0L);
                long after = transfer(p, m, before);
                d.stored.put(m, after);
                moved += before - after;
            }
            save();
            open(p, d);
            if (moved > 0) p.sendMessage("§a📦 Collected §f" + fmt(moved) + " §aitem(s).");
            else p.sendMessage("§e📦 Inventory is full; stored drops remain untouched.");
        } finally { collecting.remove(k); }
    }

    private void dropAll(Player p, Data d) {
        String k = d.key();
        if (!collecting.add(k)) {
            p.sendMessage("§e📦 Spawner transaction already processing.");
            return;
        }

        Map<Material, Long> snapshot = new EnumMap<>(Material.class);
        List<org.bukkit.entity.Item> spawned = new ArrayList<>();
        try {
            boolean any = false;
            for (Material m : defaultDrops(d.type)) {
                long amount = d.stored.getOrDefault(m, 0L);
                if (amount > 0) any = true;
                snapshot.put(m, amount);
            }
            if (!any) {
                p.sendMessage("§e📦 There are no stored drops to release.");
                return;
            }

            Location drop = findSafeDropLocation(p);
            if (drop == null) {
                p.sendMessage("§c❌ No safe drop location was found in front of you. Nothing was removed.");
                return;
            }

            for (Material m : defaultDrops(d.type)) {
                long left = snapshot.getOrDefault(m, 0L);
                int max = Math.max(1, m.getMaxStackSize());
                while (left > 0) {
                    int amount = (int) Math.min((long) max, left);
                    org.bukkit.entity.Item entity = p.getWorld().dropItem(drop.clone(), new ItemStack(m, amount));
                    if (entity == null) throw new IllegalStateException("World rejected item spawn");
                    spawned.add(entity);
                    left -= amount;
                }
            }

            for (Material m : defaultDrops(d.type)) d.stored.put(m, 0L);
            if (!save()) {
                throw new IllegalStateException("Spawner storage could not be saved");
            }

            p.sendMessage("§a📦 Dropped all stored items in front of you.");
        } catch (Exception failure) {
            for (org.bukkit.entity.Item entity : spawned) {
                if (!entity.isDead()) entity.remove();
            }
            for (Map.Entry<Material, Long> entry : snapshot.entrySet()) {
                d.stored.put(entry.getKey(), entry.getValue());
            }
            plugin.getLogger().warning("DROP ALL rolled back for " + d.key() + ": " + failure.getMessage());
            p.sendMessage("§c❌ DROP ALL failed; stored drops were not removed.");
        } finally {
            collecting.remove(k);
        }
    }

    private Location findSafeDropLocation(Player p) {
        World world = p.getWorld();
        Vector forward = p.getLocation().getDirection().clone();
        forward.setY(0);
        if (forward.lengthSquared() < 0.0001) return null;
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
        int baseY = p.getLocation().getBlockY();

        double[] distances = {1.25, 1.5, 1.75, 2.0, 2.25};
        double[] laterals = {0.0, -0.35, 0.35, -0.65, 0.65};
        for (double distance : distances) {
            for (double lateral : laterals) {
                Vector offset = forward.clone().multiply(distance).add(right.clone().multiply(lateral));
                int bx = (int) Math.floor(p.getX() + offset.getX());
                int bz = (int) Math.floor(p.getZ() + offset.getZ());
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;

                for (int dy = -1; dy <= 1; dy++) {
                    int by = baseY + dy;
                    Block ground = world.getBlockAt(bx, by - 1, bz);
                    Block feet = world.getBlockAt(bx, by, bz);
                    Block head = world.getBlockAt(bx, by + 1, bz);
                    if (!ground.getType().isSolid() || ground.isLiquid()) continue;
                    if (!feet.isPassable() || !head.isPassable()) continue;
                    return new Location(world, bx + 0.5, by + 0.15, bz + 0.5);
                }
            }
        }
        return null;
    }

    private long transfer(Player p, Material mat, long amount) {
        long left = Math.max(0L, amount);
        while (left > 0) {
            int n = (int) Math.min(mat.getMaxStackSize(), left);
            Map<Integer, ItemStack> rem = p.getInventory().addItem(new ItemStack(mat, n));
            long returned = rem.values().stream().mapToLong(ItemStack::getAmount).sum();
            long accepted = n - returned;
            if (accepted <= 0) break;
            left -= accepted;
            if (returned > 0) break;
        }
        return left;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (!title.startsWith("§2§l🧟 ") && !title.startsWith("§2§l⚙ ")) return;
        e.setCancelled(true);
        if (e.getClick().isKeyboardClick() || e.isShiftClick() || e.getClick() == ClickType.DOUBLE_CLICK) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        String k = open.get(p.getUniqueId());
        Data d = k == null ? null : spawners.get(k);
        if (d == null || !canManage(p, d)) { p.closeInventory(); return; }

        if (title.equals(mainTitle(d.type))) {
            switch (e.getRawSlot()) {
                case 22 -> collect(p, d);
                case 23 -> dropAll(p, d);
                case 31 -> settings(p, d);
                case 40 -> p.closeInventory();
            }
        } else if (title.equals(settingsTitle(d.type))) {
            Material[] drops = defaultDrops(d.type).toArray(new Material[0]);
            for (int i = 0; i < drops.length; i++) if (e.getRawSlot() == 10 + i * 2) {
                Material m = drops[i];
                if (d.enabled.contains(m)) d.enabled.remove(m); else d.enabled.add(m);
                save(); settings(p, d); return;
            }
            if (e.getRawSlot() == 22) open(p, d);
        }
    }

    @EventHandler public void drag(InventoryDragEvent e) {
        String t = e.getView().getTitle();
        if (t.startsWith("§2§l🧟 ") || t.startsWith("§2§l⚙ ")) e.setCancelled(true);
    }

    @EventHandler public void close(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        UUID uuid = p.getUniqueId();
        Inventory current = openInventories.get(uuid);
        // Only close the active Emerald spawner GUI. Closing another inventory must
        // never invalidate the server-side spawner session.
        if (current != e.getInventory()) return;
        openInventories.remove(uuid);
        open.remove(uuid);
    }

    private void removeHolograms(Data d) {
        World w = Bukkit.getWorld(d.world); if (w == null) return;
        for (Entity entity : new ArrayList<>(w.getEntities())) if (entity instanceof ArmorStand as && d.key().equals(as.getPersistentDataContainer().get(hologramKey, PersistentDataType.STRING))) as.remove();
    }

    private void removeAllHolograms() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(w.getEntities())) {
                if (entity instanceof ArmorStand as && as.getPersistentDataContainer().has(hologramKey, PersistentDataType.STRING)) {
                    as.remove();
                }
            }
        }
    }

    private static String normalizeType(String type) { return TYPES.contains(type == null ? "" : type.toLowerCase(Locale.ROOT)) ? type.toLowerCase(Locale.ROOT) : TYPE_SKELETON; }
    private static Set<Material> defaultDrops(String type) {
        return switch (normalizeType(type)) {
            case TYPE_ZOMBIE -> EnumSet.of(Material.ROTTEN_FLESH, Material.IRON_INGOT, Material.CARROT, Material.POTATO);
            case TYPE_SPIDER -> EnumSet.of(Material.STRING, Material.SPIDER_EYE);
            case TYPE_CREEPER -> EnumSet.of(Material.GUNPOWDER);
            default -> EnumSet.of(Material.BONE, Material.ARROW, Material.BOW);
        };
    }
    private EntityType entityType(String type) { return switch (normalizeType(type)) { case TYPE_ZOMBIE -> EntityType.ZOMBIE; case TYPE_SPIDER -> EntityType.SPIDER; case TYPE_CREEPER -> EntityType.CREEPER; default -> EntityType.SKELETON; }; }
    private Material iconFor(String type) { return switch (normalizeType(type)) { case TYPE_ZOMBIE -> Material.ZOMBIE_HEAD; case TYPE_SPIDER -> Material.SPIDER_EYE; case TYPE_CREEPER -> Material.CREEPER_HEAD; default -> Material.SKELETON_SKULL; }; }
    private String mainTitle(String type) { return "§2§l🧟 " + normalizeType(type).toUpperCase(Locale.ROOT) + " SPAWNER"; }
    private String settingsTitle(String type) { return "§2§l⚙ " + normalizeType(type).toUpperCase(Locale.ROOT) + " SETTINGS"; }
    private String dropIcon(Material m) { return switch (m) { case BONE -> "🦴"; case ARROW, BOW -> "🏹"; case ROTTEN_FLESH -> "🥩"; case IRON_INGOT -> "⛓"; case CARROT -> "🥕"; case POTATO -> "🥔"; case STRING -> "🧵"; case SPIDER_EYE -> "👁"; case GUNPOWDER -> "💥"; default -> "•"; }; }
    private String pretty(String type) { String s = normalizeType(type); return Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    private String prettyMaterial(Material m) { String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' '); StringBuilder b = new StringBuilder(); for (String w : s.split(" ")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' '); return b.toString().trim(); }
    private String fmt(long n) { return String.format(Locale.US, "%,d", Math.max(0L, n)); }
    private long safeAdd(long a, long b) { if (b <= 0) return Math.max(0L, a); return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    private long safeMultiply(long a, long b) { if (a <= 0 || b <= 0) return 0; return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b; }
    private long nonNegative(long n) { return Math.max(0L, n); }
    private int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }
    private ItemStack item(Material material, String name, List<String> lore) { ItemStack i = new ItemStack(material); ItemMeta m = i.getItemMeta(); if (m != null) { m.setDisplayName(name); m.setLore(lore); i.setItemMeta(m); } return i; }
    private void fill(Inventory inv) { ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of()); for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, pane.clone()); }
}
