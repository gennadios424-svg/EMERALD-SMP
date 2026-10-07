package net.emeraldsmp;

import net.emeraldsmp.afk.AfkManager;
import net.emeraldsmp.crates.CrateManager;
import net.emeraldsmp.roles.RoleManager;
import org.bukkit.*;
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

    public static void install(EmeraldSMP p) {
        // IMPORTANT: do not replace, stop, or rebuild the base plugin UI.
        // The restoration patch must never take ownership of movement,
        // scoreboards, tab, or existing command executors.
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
