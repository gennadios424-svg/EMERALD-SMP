package net.emeraldsmp;

import net.emeraldsmp.afk.AfkManager;
import net.emeraldsmp.crates.CrateManager;
import net.emeraldsmp.roles.RoleManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.persistence.PersistentDataType;
import java.io.File;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FinalRestorationPatch {
    private static final Map<UUID, Long> afkRewards = new ConcurrentHashMap<>();
    private static NamespacedKey SELL_KEY;

    public static void install(EmeraldSMP p) {
        // IMPORTANT: do not replace, stop, or rebuild the base plugin UI.
        // The restoration patch must never take ownership of movement,
        // scoreboards, tab, or existing command executors.
        SELL_KEY = new NamespacedKey(p, "spawner_sell_all");
        PatchListener listener = new PatchListener(p);
        p.getServer().getPluginManager().registerEvents(listener, p);

        p.getServer().getScheduler().runTaskTimer(p, () -> rewardAfk(p), 20L, 20L);
    }

    private static void rewardAfk(EmeraldSMP p) {
        AfkManager a = p.getAfkManager();
        if (a == null || p.getPlayerDataManager() == null) return;

        long now = System.currentTimeMillis();
        for (Player x : Bukkit.getOnlinePlayers()) {
            if (!a.isAfk(x.getUniqueId())) continue;

            long last = afkRewards.getOrDefault(x.getUniqueId(), now);
            if (now - last < 300000L) continue;

            long intervals = (now - last) / 300000L;
            afkRewards.put(x.getUniqueId(), last + intervals * 300000L);

            long current = p.getPlayerDataManager().getEmeraldShards(x.getUniqueId());
            p.getPlayerDataManager().setEmeraldShards(x.getUniqueId(), current + 3L * intervals);
            x.sendMessage("§a💚 AFK reward: §b+" + (3L * intervals) + " Emerald Shards");
        }
    }

    static final class PatchListener implements Listener, CommandExecutor, TabCompleter {
        private final EmeraldSMP p;

        PatchListener(EmeraldSMP p) {
            this.p = p;

            // Only install executors for the two commands introduced by the
            // restoration patch. Never overwrite any existing base command.
            PluginCommand role = p.getCommand("role");
            if (role != null) {
                role.setExecutor(this);
                role.setTabCompleter(this);
            }

            PluginCommand banlist = p.getCommand("banlist");
            if (banlist != null) banlist.setExecutor(this);
        }

        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        public void spawner(PlayerInteractEvent e) {
            if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
            Material m = e.getClickedBlock().getType();
            if (!m.name().endsWith("_SPAWNER") && m != Material.SPAWNER) return;

            Player player = e.getPlayer();
            // Let the original Emerald SMP spawner GUI open first, then add
            // our button without replacing or rebuilding that GUI.
            Bukkit.getScheduler().runTask(p, () -> addSpawnerSellButton(player));
        }

        private void addSpawnerSellButton(Player player) {
            Inventory inv = player.getOpenInventory().getTopInventory();
            if (inv == null || inv.getSize() < 18) return;

            ItemStack button = new ItemStack(Material.EMERALD_BLOCK);
            ItemMeta meta = button.getItemMeta();
            if (meta == null) return;
            meta.setDisplayName("§a§lSELL ALL");
            meta.setLore(List.of(
                "§7Sell all stored drops/items",
                "§7using the matching §f/worth §7value.",
                "",
                "§a▶ Click to sell"
            ));
            meta.getPersistentDataContainer().set(SELL_KEY, PersistentDataType.BYTE, (byte) 1);
            button.setItemMeta(meta);

            int bottomStart = Math.max(0, inv.getSize() - 9);
            int slot = bottomStart + Math.min(4, inv.getSize() - bottomStart - 1);
            // Prefer the center of the bottom row; do not overwrite an existing
            // Emerald SMP control.
            if (inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR) {
                inv.setItem(slot, button);
            } else {
                for (int i = bottomStart; i < inv.getSize(); i++) {
                    ItemStack current = inv.getItem(i);
                    if (current == null || current.getType() == Material.AIR) {
                        inv.setItem(i, button);
                        break;
                    }
                }
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
        public void spawnerSell(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player player)) return;
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR || SELL_KEY == null) return;

            ItemMeta meta = clicked.getItemMeta();
            if (meta == null || !meta.getPersistentDataContainer().has(SELL_KEY, PersistentDataType.BYTE)) return;

            e.setCancelled(true);

            Inventory inv = e.getView().getTopInventory();
            double total = 0.0;
            int soldStacks = 0;

            // Contents live above the bottom control row. This keeps Drop All
            // and other existing spawner controls untouched.
            int contentEnd = Math.max(0, inv.getSize() - 9);
            for (int slot = 0; slot < contentEnd; slot++) {
                ItemStack item = inv.getItem(slot);
                if (item == null || item.getType() == Material.AIR) continue;

                double unit = findWorth(p, item.getType());
                if (unit <= 0) continue;

                total += unit * item.getAmount();
                soldStacks++;
            }

            if (total <= 0 || soldStacks == 0) {
                player.sendMessage("§cThere are no sellable items in the spawner.");
                return;
            }

            // Deposit first. Items are only removed after the money operation
            // succeeds, so a missing economy integration cannot delete drops.
            if (!addMoney(p, player, total)) {
                player.sendMessage("§cCould not deposit the sale. Nothing was removed.");
                return;
            }

            for (int slot = 0; slot < contentEnd; slot++) {
                ItemStack item = inv.getItem(slot);
                if (item == null || item.getType() == Material.AIR) continue;
                if (findWorth(p, item.getType()) > 0) inv.setItem(slot, null);
            }

            player.sendMessage("§a§lSOLD §7» §a+$" + format(total) + " §7from spawner drops.");
        }

        private double findWorth(EmeraldSMP plugin, Material material) {
            String wanted = material.name().toLowerCase(Locale.ROOT);
            List<File> files = new ArrayList<>();
            collectWorthFiles(plugin.getDataFolder(), files);

            for (File file : files) {
                try {
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
                    Double direct = findNumeric(y, wanted);
                    if (direct != null && direct > 0) return direct;
                } catch (Throwable ignored) {
                }
            }
            return 0.0;
        }

        private void collectWorthFiles(File dir, List<File> out) {
            if (dir == null || !dir.exists()) return;
            File[] children = dir.listFiles();
            if (children == null) return;
            for (File f : children) {
                if (f.isDirectory()) collectWorthFiles(f, out);
                else if (f.getName().toLowerCase(Locale.ROOT).contains("worth")
                        && (f.getName().endsWith(".yml") || f.getName().endsWith(".yaml"))) out.add(f);
            }
        }

        private Double findNumeric(ConfigurationSection section, String wanted) {
            for (String key : section.getKeys(false)) {
                Object value = section.get(key);
                String normalized = key.toLowerCase(Locale.ROOT).replace("minecraft:", "");
                if (normalized.equals(wanted) && value instanceof Number n) return n.doubleValue();
                if (value instanceof ConfigurationSection child) {
                    Double result = findNumeric(child, wanted);
                    if (result != null) return result;
                }
            }
            return null;
        }

        private boolean addMoney(EmeraldSMP plugin, Player player, double amount) {
            Object data = plugin.getPlayerDataManager();
            if (data == null) return false;

            for (String name : List.of("addMoney", "giveMoney", "deposit", "setMoney")) {
                for (Method m : data.getClass().getMethods()) {
                    if (!m.getName().equalsIgnoreCase(name)) continue;
                    try {
                        Class<?>[] t = m.getParameterTypes();
                        if (t.length == 2 && t[0] == UUID.class) {
                            Object old = getMoney(data, player.getUniqueId());
                            Object value = name.equalsIgnoreCase("setMoney") ? amount : ((Number) old).doubleValue() + amount;
                            Object converted = convertNumber(value, t[1]);
                            m.invoke(data, player.getUniqueId(), converted);
                            return true;
                        }
                        if (t.length == 2 && t[0] == Player.class) {
                            Object old = getMoney(data, player.getUniqueId());
                            Object value = name.equalsIgnoreCase("setMoney") ? amount : ((Number) old).doubleValue() + amount;
                            m.invoke(data, player, convertNumber(value, t[1]));
                            return true;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            return false;
        }

        private Object getMoney(Object data, UUID uuid) {
            for (Method m : data.getClass().getMethods()) {
                if (!m.getName().equalsIgnoreCase("getMoney")) continue;
                try {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == UUID.class)
                        return m.invoke(data, uuid);
                } catch (Throwable ignored) {}
            }
            return 0.0;
        }

        private Object convertNumber(Object value, Class<?> type) {
            double d = ((Number)value).doubleValue();
            if (type == double.class || type == Double.class) return d;
            if (type == float.class || type == Float.class) return (float)d;
            if (type == long.class || type == Long.class) return (long)d;
            if (type == int.class || type == Integer.class) return (int)d;
            return value;
        }

        private String format(double value) {
            if (value >= 1_000_000_000) return trim(value / 1_000_000_000) + "B";
            if (value >= 1_000_000) return trim(value / 1_000_000) + "M";
            if (value >= 1_000) return trim(value / 1_000) + "K";
            return trim(value);
        }

        private String trim(double value) {
            if (Math.abs(value - Math.rint(value)) < 0.000001) return String.valueOf((long)Math.rint(value));
            return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void join(PlayerJoinEvent e) {
            Player x = e.getPlayer();
            try {
                p.getRoleManager().ensureMember(x);
            } catch (Throwable ignored) {
            }
        }

        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        public void crate(PlayerInteractEvent e) {
            if (e.getClickedBlock() == null) return;

            CrateManager c = p.getCrateManager();
            if (c == null || !c.isCrate(e.getClickedBlock())) return;

            e.setCancelled(true);
            try {
                String type = (String) call(
                    c, "type",
                    new Class[]{org.bukkit.block.Block.class},
                    e.getClickedBlock()
                );

                if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                    call(c, "openPreview",
                        new Class[]{Player.class, String.class},
                        e.getPlayer(), type);
                } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    call(c, "open",
                        new Class[]{Player.class, String.class},
                        e.getPlayer(), type);
                }
            } catch (Throwable ignored) {
            }
        }

        @EventHandler
        public void quit(PlayerQuitEvent e) {
            afkRewards.remove(e.getPlayer().getUniqueId());
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (command.getName().equalsIgnoreCase("role")) return role(sender, args);
            if (command.getName().equalsIgnoreCase("banlist")) return banlist(sender);
            return false;
        }

        private boolean role(CommandSender sender, String[] args) {
            if (!(sender instanceof Player x)) return true;

            RoleManager.Role actor = p.getRoleManager().get(x);
            if (actor.weight() < RoleManager.Role.MOD.weight()) {
                x.sendMessage("§cYou do not have permission.");
                return true;
            }

            if (args.length != 2) {
                x.sendMessage("§cUsage: /role <player> <role>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                x.sendMessage("§cPlayer must be online.");
                return true;
            }

            RoleManager.Role newRole;
            try {
                newRole = RoleManager.Role.valueOf(args[1].toUpperCase(Locale.ROOT));
            } catch (Exception ex) {
                x.sendMessage("§cInvalid role.");
                return true;
            }

            if (target.getUniqueId().equals(x.getUniqueId())
                    || newRole.weight() >= actor.weight()
                    || !p.getRoleManager().canPunish(x, target)) {
                x.sendMessage("§cYou cannot assign that role.");
                return true;
            }

            p.getRoleManager().set(target, newRole);
            x.sendMessage("§aRole updated: §f" + target.getName() + " §7→ §f" + newRole.label());
            return true;
        }

        private boolean banlist(CommandSender sender) {
            if (!(sender instanceof Player x)
                    || p.getRoleManager().get(x).weight() < RoleManager.Role.MOD.weight()) {
                sender.sendMessage("§cYou do not have permission.");
                return true;
            }

            Inventory inv = Bukkit.createInventory(null, 54, "§2§l💚 BAN LIST");
            int slot = 0;

            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (slot >= 45) break;

                ItemStack item = new ItemStack(Material.PLAYER_HEAD);
                ItemMeta meta = item.getItemMeta();

                if (meta != null) {
                    meta.setDisplayName("§c🔨 " + (op.getName() == null ? "Unknown" : op.getName()));
                    meta.setLore(List.of(
                        "§7Click for ban details",
                        "§8IP/UUID/network data is never shown."
                    ));
                    item.setItemMeta(meta);
                }

                inv.setItem(slot++, item);
            }

            x.openInventory(inv);
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
            if (command.getName().equalsIgnoreCase("role")) {
                if (args.length == 1) {
                    String prefix = args[0].toLowerCase(Locale.ROOT);
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted()
                        .toList();
                }

                if (args.length == 2) {
                    String prefix = args[1].toLowerCase(Locale.ROOT);
                    return Arrays.stream(RoleManager.Role.values())
                        .map(Enum::name)
                        .filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
                }
            }

            return List.of();
        }

        private Object call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
            Method method = object.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(object, args);
        }
    }
}
