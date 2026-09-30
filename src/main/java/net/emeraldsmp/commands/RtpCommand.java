package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class RtpCommand implements org.bukkit.command.CommandExecutor, Listener {
    private static final String GUI_TITLE = "§2💚 Emerald SMP §8» §aRTP";
    private static final int MAX_SEARCH_ATTEMPTS = 24;
    private final EmeraldSMP plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    private final Map<UUID, Location> countdownStarts = new HashMap<>();

    public RtpCommand(EmeraldSMP plugin) { this.plugin = plugin; }

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
        long cooldown = Math.max(0L, plugin.getConfig().getLong("rtp.cooldown", 60L));
        long last = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        long remainingMs = last + cooldown * 1000L - System.currentTimeMillis();
        if (!p.hasPermission("emerald.rtp.bypass") && remainingMs > 0) {
            long sec = (remainingMs + 999L) / 1000L;
            plugin.getMessageService().send(p,
                plugin.getConfig().getString("messages.rtp.cooldown", "&cYou must wait &f%time%&c.")
                    .replace("%time%", formatTime(sec)));
            return true;
        }
        openWorldGui(p);
        return true;
    }

    private void openWorldGui(Player p) {
        Inventory inv = Bukkit.createInventory(new RtpHolder(), 27, GUI_TITLE);
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "§8");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);
        addWorldOption(inv, 11, Material.GRASS_BLOCK, "§a🌎 Overworld");
        addWorldOption(inv, 13, Material.NETHERRACK, "§c🔥 Nether");
        addWorldOption(inv, 15, Material.END_STONE, "§5🟣 End");
        p.openInventory(inv);
    }

    private void addWorldOption(Inventory inv, int slot, Material mat, String name) {
        ItemStack i = item(mat, name);
        ItemMeta m = i.getItemMeta();
        if (m != null) m.setLore(List.of("§7Click to choose this world"));
        i.setItemMeta(m);
        inv.setItem(slot, i);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof RtpHolder)) return;
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        String name = switch (slot) {
            case 11 -> configuredWorld("overworld", "world", 0);
            case 13 -> configuredWorld("nether", "world_nether", 1);
            case 15 -> configuredWorld("end", "world_the_end", 2);
            default -> null;
        };
        if (name == null) return;

        World world = Bukkit.getWorld(name);
        if (world == null) {
            plugin.getMessageService().send(p, "&cThat RTP world is not loaded.");
            return;
        }
        if (!isWorldAllowed(world)) {
            plugin.getMessageService().send(p, plugin.getConfig().getString("messages.rtp.not-allowed", "&cRTP is not allowed in this world."));
            return;
        }
        p.closeInventory();
        startSearch(p, world);
    }

    private String configuredWorld(String key, String fallback, int listIndex) {
        String direct = plugin.getConfig().getString("rtp.worlds." + key, "");
        if (direct != null && !direct.isBlank()) return direct;
        List<String> list = plugin.getConfig().getStringList("rtp.worlds");
        if (listIndex >= 0 && listIndex < list.size() && !list.get(listIndex).isBlank()) return list.get(listIndex);
        return fallback;
    }

    private boolean isWorldAllowed(World w) {
        List<String> list = plugin.getConfig().getStringList("rtp.worlds");
        if (list.isEmpty()) return true;
        return list.stream().anyMatch(s -> s.equalsIgnoreCase(w.getName()));
    }

    private void startSearch(Player p, World world) {
        UUID u = p.getUniqueId();
        int seconds = Math.max(1, plugin.getConfig().getInt("rtp.countdown", 5));
        cancelPending(u, false);

        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            int attempts = 0;

            @Override public void run() {
                Player player = Bukkit.getPlayer(u);
                if (player == null || !player.isOnline()) {
                    cancelPending(u, false);
                    cancel();
                    return;
                }

                if (attempts++ >= MAX_SEARCH_ATTEMPTS) {
                    cancelPending(u, false);
                    plugin.getMessageService().send(player,
                        plugin.getConfig().getString("messages.rtp.failed", "&cCould not find a safe RTP location. Please try again."));
                    cancel();
                    return;
                }

                Location destination = findQuickLocation(world);
                if (destination != null) {
                    cancel();
                    pending.remove(u);
                    beginCountdown(player, u, destination, seconds);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        pending.put(u, task);
        String searching = plugin.getConfig().getString("messages.rtp.searching", "");
        if (searching != null && !searching.isBlank()) plugin.getMessageService().send(p, searching);
    }

    private Location findQuickLocation(World w) {
        WorldBorder border = w.getWorldBorder();
        int min = Math.max(0, plugin.getConfig().getInt("rtp.min-distance", 500));
        int max = Math.max(min + 1, plugin.getConfig().getInt("rtp.max-distance", 5000));
        double borderRadius = border.getSize() / 2.0 - 16.0;
        max = (int) Math.min(max, Math.max(1, borderRadius));
        if (max <= min) min = Math.max(0, max / 2);

        ThreadLocalRandom r = ThreadLocalRandom.current();
        double angle = r.nextDouble(0, Math.PI * 2);
        double dist = Math.sqrt(r.nextDouble((double) min * min, (double) max * max));
        int x = (int) Math.round(border.getCenter().getX() + Math.cos(angle) * dist);
        int z = (int) Math.round(border.getCenter().getZ() + Math.sin(angle) * dist);
        if (!border.isInside(new Location(w, x, 64, z))) return null;

        int cx = x >> 4, cz = z >> 4;
        if (!w.isChunkLoaded(cx, cz)) w.loadChunk(cx, cz, true);
        return findQuickSafeAt(w, x, z);
    }

    private Location findQuickSafeAt(World w, int x, int z) {
        int highest = w.getHighestBlockYAt(x, z);
        int minY = w.getMinHeight() + 1;
        int maxY = w.getMaxHeight() - 3;
        if (w.getEnvironment() == World.Environment.NETHER) maxY = Math.min(maxY, 123);

        int y = Math.min(highest, maxY);
        if (y < minY) return null;

        Block floor = w.getBlockAt(x, y, z);
        Block feet = w.getBlockAt(x, y + 1, z);
        Block head = w.getBlockAt(x, y + 2, z);

        if (!isSafeFloor(floor) || !feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid()) return null;
        Location loc = new Location(w, x + .5, y + 1, z + .5);
        loc.setYaw(ThreadLocalRandom.current().nextFloat() * 360f);
        loc.setPitch(0);
        return loc;
    }

    private void beginCountdown(Player p, UUID u, Location destination, int seconds) {
        countdownStarts.put(u, p.getLocation().clone());

        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = seconds;

            @Override public void run() {
                Player player = Bukkit.getPlayer(u);
                if (player == null || !player.isOnline()) {
                    cancelPending(u, false);
                    cancel();
                    return;
                }

                if (remaining <= 0) {
                    pending.remove(u);
                    countdownStarts.remove(u);
                    cancel();
                    if (!isStillSafe(destination)) {
                        plugin.getMessageService().send(player, "&cRTP location became unsafe. Please try again.");
                        return;
                    }
                    if (!player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                        plugin.getMessageService().send(player,
                            plugin.getConfig().getString("messages.rtp.failed", "&cCould not teleport you."));
                        return;
                    }
                    if (!player.hasPermission("emerald.rtp.bypass") && plugin.getConfig().getLong("rtp.cooldown", 60L) > 0)
                        cooldowns.put(u, System.currentTimeMillis());
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    plugin.getMessageService().send(player,
                        plugin.getConfig().getString("messages.rtp.success", "&aRTP complete! You have been safely teleported."));
                    return;
                }

                String msg = plugin.getConfig().getString("messages.rtp.countdown", "&aTeleporting in &f%time%&a...")
                    .replace("%time%", String.valueOf(remaining));
                player.sendActionBar(msg);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, .7f, 1f + remaining * .05f);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        pending.put(u, task);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        Location start = countdownStarts.get(u);
        if (start == null || e.getTo() == null) return;
        if (start.getWorld() != e.getTo().getWorld() || start.distanceSquared(e.getTo()) > 0.01D) {
            cancelPending(u, true);
        }
    }

    private void cancelPending(UUID u, boolean notify) {
        BukkitTask t = pending.remove(u);
        if (t != null) t.cancel();
        countdownStarts.remove(u);
        Player p = Bukkit.getPlayer(u);
        if (notify && p != null)
            plugin.getMessageService().send(p, plugin.getConfig().getString("messages.rtp.cancelled", "&cRTP cancelled because you moved."));
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        cancelPending(e.getPlayer().getUniqueId(), false);
        cooldowns.remove(e.getPlayer().getUniqueId());
    }

    private boolean isStillSafe(Location l) {
        World w = l.getWorld();
        if (w == null) return false;
        int x = l.getBlockX(), y = l.getBlockY() - 1, z = l.getBlockZ();
        return isQuickSafeAt(w, x, y, z);
    }

    private boolean isQuickSafeAt(World w, int x, int floorY, int z) {
        if (floorY < w.getMinHeight() || floorY >= w.getMaxHeight() - 2) return false;
        if (w.getEnvironment() == World.Environment.NETHER && floorY > 123) return false;
        Block floor = w.getBlockAt(x, floorY, z);
        Block feet = w.getBlockAt(x, floorY + 1, z);
        Block head = w.getBlockAt(x, floorY + 2, z);
        return isSafeFloor(floor) && feet.isPassable() && head.isPassable() && !feet.isLiquid() && !head.isLiquid();
    }

    private boolean isSafeFloor(Block b) {
        Material m = b.getType();
        if (!m.isSolid() || b.isLiquid()) return false;
        return switch (m) {
            case BEDROCK, LAVA, WATER, MAGMA_BLOCK, CACTUS, FIRE, SOUL_FIRE,
                 CAMPFIRE, SOUL_CAMPFIRE, POWDER_SNOW, SWEET_BERRY_BUSH, POINTED_DRIPSTONE,
                 WITHER_ROSE, TNT, END_PORTAL, END_GATEWAY, NETHER_PORTAL -> false;
            default -> true;
        };
    }

    private ItemStack item(Material m, String name) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            i.setItemMeta(meta);
        }
        return i;
    }

    private String formatTime(long s) {
        if (s < 60) return s + "s";
        long m = s / 60, r = s % 60;
        return r == 0 ? m + "m" : m + "m " + r + "s";
    }

    private static final class RtpHolder implements InventoryHolder {
        public Inventory getInventory() { return null; }
    }
}
