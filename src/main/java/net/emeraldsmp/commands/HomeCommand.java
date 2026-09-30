package net.emeraldsmp.commands;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class HomeCommand implements org.bukkit.command.CommandExecutor, org.bukkit.command.TabCompleter, Listener {
    private static final String GUI_TITLE = "§2💚 Emerald SMP §8» §aYour Homes";
    private final EmeraldSMP plugin;
    private final Map<UUID, LinkedHashMap<String, Location>> homes = new HashMap<>();
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    private final Map<UUID, Location> countdownStarts = new HashMap<>();
    private final File file;

    public HomeCommand(EmeraldSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        load();
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            p.sendMessage("§cOnly players can use home commands.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sethome" -> setHome(p, args.length == 0 ? "Home" : String.join("_", args));
            case "home" -> {
                if (args.length == 0) open(p);
                else teleportHome(p, args[0]);
            }
            case "delhome" -> {
                if (args.length != 1) p.sendMessage("§cUsage: /delhome <name>");
                else deleteHome(p, args[0]);
            }
            default -> {}
        }
        return true;
    }

    private void setHome(Player p, String requested) {
        String name = normalizeName(requested);
        if (name.isBlank()) { p.sendMessage("§cHome name cannot be empty."); return; }

        LinkedHashMap<String, Location> map = homes.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>());
        String existing = findKey(map, name);
        if (existing == null && map.size() >= maxHomes()) {
            p.sendMessage("§cYou have reached the maximum of §f" + maxHomes() + " §chomes.");
            return;
        }
        if (existing != null) name = existing;

        map.put(name, p.getLocation().clone());
        save();
        p.sendMessage("§a§lHOMES §8» §aSaved home §f" + name + "§a.");
    }

    private void open(Player p) {
        LinkedHashMap<String, Location> map = homes.getOrDefault(p.getUniqueId(), new LinkedHashMap<>());
        Inventory inv = Bukkit.createInventory(new HomeHolder(), 27, GUI_TITLE);
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, "§8");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        int slot = 10;
        for (Map.Entry<String, Location> entry : map.entrySet()) {
            if (slot >= 17) break;
            Location l = entry.getValue();
            String world = l.getWorld() == null ? "Unknown" : l.getWorld().getName();
            inv.setItem(slot++, item(Material.RED_BED, "§a§l🏠 " + entry.getKey(),
                List.of("§7Location saved", "§8World: §f" + world,
                    String.format(Locale.US, "§8XYZ: §f%.0f, %.0f, %.0f", l.getX(), l.getY(), l.getZ()),
                    "", "§aClick to teleport")));
        }

        if (slot < 17) {
            if (map.size() < maxHomes()) {
                inv.setItem(slot, item(Material.EMERALD, "§a§l➕ Create Home",
                    List.of("§7Save your current location", "§7as the default §fHome§7.", "",
                        "§aClick to create")));
            } else {
                inv.setItem(slot, item(Material.BARRIER, "§c§lHome Limit Reached",
                    List.of("§7Maximum homes: §f" + maxHomes())));
            }
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof HomeHolder)) return;
        e.setCancelled(true);

        int slot = e.getRawSlot();
        if (slot < 10 || slot >= 17) return;

        LinkedHashMap<String, Location> map = homes.getOrDefault(p.getUniqueId(), new LinkedHashMap<>());
        List<String> names = new ArrayList<>(map.keySet());
        int index = slot - 10;

        if (index < names.size()) {
            teleportHome(p, names.get(index));
            return;
        }

        if (index == names.size() && map.size() < maxHomes()) {
            if (findKey(map, "Home") == null) {
                setHome(p, "Home");
            } else {
                p.closeInventory();
                p.sendMessage("§e§lHOMES §8» §7Default Home already exists. Use §f/sethome <name>§7 for another home.");
            }
        }
    }

    private void teleportHome(Player p, String requested) {
        LinkedHashMap<String, Location> map = homes.get(p.getUniqueId());
        if (map == null) {
            p.sendMessage("§cYou have no saved homes.");
            return;
        }

        String key = findKey(map, normalizeName(requested));
        if (key == null) {
            p.sendMessage("§cHome §f" + requested + " §cdoes not exist.");
            return;
        }

        Location destination = map.get(key);
        if (destination == null || destination.getWorld() == null) {
            p.sendMessage("§cThat home is no longer valid.");
            return;
        }

        UUID u = p.getUniqueId();
        cancelTeleport(u, false);
        int seconds = Math.max(1, plugin.getConfig().getInt("homes.teleport-countdown", 3));
        countdownStarts.put(u, p.getLocation().clone());

        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = seconds;

            @Override public void run() {
                Player player = Bukkit.getPlayer(u);
                if (player == null || !player.isOnline()) {
                    cancelTeleport(u, false);
                    cancel();
                    return;
                }

                if (remaining <= 0) {
                    pending.remove(u);
                    countdownStarts.remove(u);
                    cancel();

                    Location current = homes.getOrDefault(u, new LinkedHashMap<>()).get(key);
                    if (current == null || current.getWorld() == null) {
                        player.sendMessage("§cThat home is no longer valid.");
                        return;
                    }

                    if (!player.teleport(current.clone(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                        player.sendMessage("§cTeleportation failed.");
                        return;
                    }

                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    player.sendMessage("§a§lHOMES §8» §aTeleported to §f" + key + "§a.");
                    return;
                }

                String msg = plugin.getConfig().getString("messages.homes.countdown",
                    "&aTeleporting to &f%home% &ain &f%time%&a...")
                    .replace("%home%", key)
                    .replace("%time%", String.valueOf(remaining));
                player.sendActionBar(msg);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, .7f, 1f + remaining * .08f);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        pending.put(u, task);
        p.closeInventory();
    }

    @EventHandler
    public void move(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location start = countdownStarts.get(p.getUniqueId());
        if (start == null || e.getTo() == null) return;

        if (start.getWorld() != e.getTo().getWorld() || start.distanceSquared(e.getTo()) > 0.01D) {
            cancelTeleport(p.getUniqueId(), true);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        cancelTeleport(e.getPlayer().getUniqueId(), false);
    }

    private void deleteHome(Player p, String requested) {
        LinkedHashMap<String, Location> map = homes.get(p.getUniqueId());
        if (map == null) {
            p.sendMessage("§cYou have no saved homes.");
            return;
        }

        String key = findKey(map, normalizeName(requested));
        if (key == null) {
            p.sendMessage("§cHome §f" + requested + " §cdoes not exist.");
            return;
        }

        map.remove(key);
        if (map.isEmpty()) homes.remove(p.getUniqueId());
        save();
        p.sendMessage("§a§lHOMES §8» §aDeleted home §f" + key + "§a.");
    }

    private void cancelTeleport(UUID u, boolean notify) {
        BukkitTask task = pending.remove(u);
        if (task != null) task.cancel();
        countdownStarts.remove(u);

        if (notify) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage("§cHome teleport cancelled because you moved.");
        }
    }

    private int maxHomes() {
        return Math.max(1, plugin.getConfig().getInt("homes.max", 3));
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().replace(' ', '_');
    }

    private String findKey(Map<String, Location> map, String wanted) {
        for (String key : map.keySet()) {
            if (key.equalsIgnoreCase(wanted)) return key;
        }
        return null;
    }

    private ItemStack item(Material material, String name) {
        return item(material, name, Collections.emptyList());
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack i = new ItemStack(material);
        ItemMeta meta = i.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            i.setItemMeta(meta);
        }
        return i;
    }

    @Override
    public List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (!(sender instanceof Player p)) return Collections.emptyList();
        if (command.getName().equalsIgnoreCase("sethome") && args.length == 1) return List.of("Home");
        if ((command.getName().equalsIgnoreCase("home") || command.getName().equalsIgnoreCase("delhome")) && args.length == 1) {
            LinkedHashMap<String, Location> map = homes.get(p.getUniqueId());
            if (map == null) return Collections.emptyList();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return map.keySet().stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return Collections.emptyList();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("homes");
        if (root == null) return;

        for (String uuidText : root.getKeys(false)) {
            try {
                UUID u = UUID.fromString(uuidText);
                ConfigurationSection section = root.getConfigurationSection(uuidText);
                if (section == null) continue;

                LinkedHashMap<String, Location> map = new LinkedHashMap<>();
                for (String name : section.getKeys(false)) {
                    String worldName = section.getString(name + ".world");
                    World world = worldName == null ? null : Bukkit.getWorld(worldName);
                    if (world == null) continue;

                    Location l = new Location(world,
                        section.getDouble(name + ".x"),
                        section.getDouble(name + ".y"),
                        section.getDouble(name + ".z"),
                        (float) section.getDouble(name + ".yaw"),
                        (float) section.getDouble(name + ".pitch"));
                    map.put(name, l);
                }

                if (!map.isEmpty()) homes.put(u, map);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<UUID, LinkedHashMap<String, Location>> player : homes.entrySet()) {
            for (Map.Entry<String, Location> home : player.getValue().entrySet()) {
                Location l = home.getValue();
                if (l == null || l.getWorld() == null) continue;

                String path = "homes." + player.getKey() + "." + home.getKey();
                yaml.set(path + ".world", l.getWorld().getName());
                yaml.set(path + ".x", l.getX());
                yaml.set(path + ".y", l.getY());
                yaml.set(path + ".z", l.getZ());
                yaml.set(path + ".yaw", l.getYaw());
                yaml.set(path + ".pitch", l.getPitch());
            }
        }

        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save homes.yml: " + ex.getMessage());
        }
    }

    private static final class HomeHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
