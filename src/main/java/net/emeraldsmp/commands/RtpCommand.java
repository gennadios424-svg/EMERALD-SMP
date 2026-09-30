package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class RtpCommand implements org.bukkit.command.CommandExecutor, Listener {
    private static final String GUI_TITLE = "§2💚 Emerald SMP §8» §aRTP";
    private static final int MAX_ATTEMPTS = 60;

    private final EmeraldSMP plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    private final Map<UUID, Location> pendingOrigin = new HashMap<>();

    public RtpCommand(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.getMessageService().send(sender, "&cOnly players can use /rtp.");
            return true;
        }

        if (pending.containsKey(p.getUniqueId())) {
            plugin.getMessageService().send(p, "&eYou already have an RTP teleport pending.");
            return true;
        }

        long cooldownSeconds = Math.max(0L, plugin.getConfig().getLong("rtp.cooldown", 60L));
        long last = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        long remainingMs = (last + cooldownSeconds * 1000L) - System.currentTimeMillis();

        if (!p.hasPermission("emerald.rtp.bypass") && remainingMs > 0) {
            long remaining = (remainingMs + 999L) / 1000L;
            plugin.getMessageService().send(p, plugin.getConfig().getString(
                    "messages.rtp.cooldown", "&cYou must wait &f%time%&c before using RTP again.")
                    .replace("%time%", formatTime(remaining)));
            return true;
        }

        openWorldGui(p);
        return true;
    }

    private void openWorldGui(Player p) {
        Inventory inv = Bukkit.createInventory(new RtpHolder(), 27, GUI_TITLE);

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "§8");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        addWorldOption(inv, 11, Material.GRASS_BLOCK, "§a🌎 Overworld", "world");
        addWorldOption(inv, 13, Material.NETHERRACK, "§c🔥 Nether", "nether");
        addWorldOption(inv, 15, Material.END_STONE, "§5🟣 End", "end");

        p.openInventory(inv);
    }

    private void addWorldOption(Inventory inv, int slot, Material material, String name, String key) {
        ItemStack item = item(material, name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) meta.setLore(List.of("§7Click to choose this world"));
        item.setItemMeta(meta);
        item.setAmount(1);
        inv.setItem(slot, item);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof RtpHolder)) return;
        event.setCancelled(true);

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        String targetName = switch (event.getRawSlot()) {
            case 11 -> configuredWorld("world", "world");
            case 13 -> configuredWorld("nether", "world_nether");
            case 15 -> configuredWorld("end", "world_the_end");
            default -> null;
        };
        if (targetName == null) return;

        World target = Bukkit.getWorld(targetName);
        if (target == null) {
            plugin.getMessageService().send(p, "&cThat RTP world is not loaded.");
            return;
        }
        if (!isWorldAllowed(target)) {
            plugin.getMessageService().send(p, "&cRTP is not enabled for that world.");
            return;
        }

        p.closeInventory();
        startTeleport(p, target);
    }

    private String configuredWorld(String key, String fallback) {
        String value = plugin.getConfig().getString("rtp.worlds." + key, "");
        if (value == null || value.isBlank()) {
            List<String> worlds = plugin.getConfig().getStringList("rtp.worlds");
            if (!worlds.isEmpty()) {
                int index = key.equals("world") ? 0 : key.equals("nether") ? 1 : 2;
                if (index < worlds.size()) return worlds.get(index);
            }
            return fallback;
        }
        return value;
    }

    private boolean isWorldAllowed(World world) {
        List<String> worlds = plugin.getConfig().getStringList("rtp.worlds");
        if (worlds.isEmpty()) return true;
        for (String name : worlds) if (name.equalsIgnoreCase(world.getName())) return true;
        return false;
    }

    private void startTeleport(Player p, World world) {
        Location destination = findSafeLocation(world, p.getLocation());
        if (destination == null) {
            plugin.getMessageService().send(p, plugin.getConfig().getString(
                    "messages.rtp.failed", "&cCould not find a safe RTP location. Please try again."));
            return;
        }

        UUID uuid = p.getUniqueId();
        int seconds = Math.max(1, plugin.getConfig().getInt("rtp.countdown", 5));
        Location origin = p.getLocation().clone();

        pendingOrigin.put(uuid, origin);
        final int[] remaining = {seconds};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            BukkitTask self;

            @Override public void run() {
                self = pending.get(uuid);
                if (!p.isOnline()) {
                    cancelPending(uuid, false);
                    return;
                }
                if (remaining[0] <= 0) {
                    pending.remove(uuid);
                    pendingOrigin.remove(uuid);

                    if (!p.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                        plugin.getMessageService().send(p, plugin.getConfig().getString(
                                "messages.rtp.failed", "&cCould not teleport you. Please try again."));
                        return;
                    }

                    if (!p.hasPermission("emerald.rtp.bypass") && plugin.getConfig().getLong("rtp.cooldown", 60L) > 0) {
                        cooldowns.put(uuid, System.currentTimeMillis());
                    }
                    plugin.getMessageService().send(p, plugin.getConfig().getString(
                            "messages.rtp.success", "&aTeleported safely to your random location."));
                    return;
                }

                String message = plugin.getConfig().getString(
                        "messages.rtp.countdown", "&aTeleporting in &f%time%&a...")
                        .replace("%time%", Integer.toString(remaining[0]));
                plugin.getMessageService().send(p, message);
                remaining[0]--;
            }
        }, 0L, 20L);

        pending.put(uuid, task);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!pending.containsKey(p.getUniqueId()) || event.getTo() == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            cancelPending(p.getUniqueId(), true);
        }
    }

    private void cancelPending(UUID uuid, boolean notify) {
        BukkitTask task = pending.remove(uuid);
        pendingOrigin.remove(uuid);
        if (task != null) task.cancel();

        Player p = Bukkit.getPlayer(uuid);
        if (notify && p != null) {
            plugin.getMessageService().send(p, plugin.getConfig().getString(
                    "messages.rtp.cancelled", "&cRTP cancelled because you moved."));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // Closing the world-selection GUI does not cancel a teleport because none has started yet.
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer().getUniqueId(), false);
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    private Location findSafeLocation(World world, Location center) {
        int min = Math.max(0, plugin.getConfig().getInt("rtp.min-distance", 500));
        int max = Math.max(min + 1, plugin.getConfig().getInt("rtp.max-distance", 5000));
        int borderLimit = (int) Math.max(1, Math.floor(world.getWorldBorder().getSize() / 2.0) - 16);
        max = Math.min(max, borderLimit);
        if (max <= min) min = Math.max(0, max / 2);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = random.nextDouble(0, Math.PI * 2.0);
            double distance = Math.sqrt(random.nextDouble((double) min * min, (double) max * max));
            int x = (int) Math.round(center.getX() + Math.cos(angle) * distance);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * distance);

            if (!world.getWorldBorder().isInside(new Location(world, x, 64, z))) continue;
            Location safe = findSafeAt(world, x, z);
            if (safe != null) return safe;
        }
        return null;
    }

    private Location findSafeAt(World world, int x, int z) {
        int highest = world.getHighestBlockYAt(x, z);
        int minY = world.getMinHeight() + 1;
        int maxY = Math.min(highest, world.getMaxHeight() - 3);

        for (int y = maxY; y >= minY && y >= maxY - 96; y--) {
            Block floor = world.getBlockAt(x, y, z);
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);

            if (!isSafeFloor(floor) || !feet.isPassable() || !head.isPassable()) continue;
            if (feet.isLiquid() || head.isLiquid()) continue;

            if (!hasSafeNeighbors(world, x, y, z)) continue;
            if (world.getEnvironment() == World.Environment.NETHER && y >= world.getMaxHeight() - 10) continue;
            if (world.getEnvironment() == World.Environment.THE_END && y <= world.getMinHeight() + 4) continue;

            Location loc = new Location(world, x + 0.5, y + 1.0, z + 0.5);
            loc.setYaw(ThreadLocalRandom.current().nextFloat() * 360.0f);
            loc.setPitch(0.0f);
            return loc;
        }
        return null;
    }

    private boolean hasSafeNeighbors(World world, int x, int y, int z) {
        int[][] offsets = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] o : offsets) {
            Block floor = world.getBlockAt(x + o[0], y, z + o[1]);
            Block feet = world.getBlockAt(x + o[0], y + 1, z + o[1]);
            if (!isSafeFloor(floor) || !feet.isPassable() || feet.isLiquid()) return false;
        }
        return true;
    }

    private boolean isSafeFloor(Block block) {
        Material m = block.getType();
        if (!m.isSolid() || block.isLiquid()) return false;
        return switch (m) {
            case LAVA, MAGMA_BLOCK, CACTUS, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE,
                 POWDER_SNOW, SWEET_BERRY_BUSH, POINTED_DRIPSTONE, WITHER_ROSE,
                 TNT, END_PORTAL, END_GATEWAY, NETHER_PORTAL, WATER -> false;
            default -> true;
        };
    }

    private ItemStack item(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatTime(long seconds) {
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        return remainder == 0 ? minutes + "m" : minutes + "m " + remainder + "s";
    }

    private static final class RtpHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
