package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.NumberFormat;
import java.util.*;

public final class SellMenu implements Listener {
    private static final String TITLE = ChatColor.DARK_GREEN + "💚 EMERALD SELL";
    private static final int SIZE = 54;

    private static final int[] INPUT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int TOTAL_SLOT = 47;
    private static final int SELL_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private final EmeraldSMP plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> transactionLocks = new HashSet<>();

    public SellMenu(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        close(player);

        Session session = new Session(UUID.randomUUID());
        Inventory inventory = Bukkit.createInventory(session, SIZE, TITLE);
        session.inventory = inventory;

        ItemStack dark = button(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack emerald = button(Material.GREEN_STAINED_GLASS_PANE, ChatColor.GREEN + "💚");

        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, dark);
        }

        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, emerald);
            inventory.setItem(45 + slot, emerald);
        }

        for (int row = 1; row <= 4; row++) {
            inventory.setItem(row * 9, emerald);
            inventory.setItem(row * 9 + 8, emerald);
        }

        inventory.setItem(TOTAL_SLOT, button(
                Material.EMERALD,
                ChatColor.GREEN + "§l💵 TOTAL VALUE",
                List.of("", ChatColor.GRAY + "Current sell value", ChatColor.WHITE + "$0")
        ));

        inventory.setItem(SELL_SLOT, button(
                Material.EMERALD_BLOCK,
                ChatColor.GREEN + "§l💰 SELL ALL",
                List.of("", ChatColor.GRAY + "Sell all valid items", ChatColor.GRAY + "Shulker contents included", "", ChatColor.YELLOW + "▶ Click to sell")
        ));

        inventory.setItem(CLOSE_SLOT, button(
                Material.BARRIER,
                ChatColor.WHITE + "§l✖ CLOSE",
                List.of(ChatColor.GRAY + "Unsold items are returned")
        ));

        sessions.put(player.getUniqueId(), session);
        player.openInventory(inventory);
        refresh(player, session);
    }

    private boolean isInputSlot(int rawSlot) {
        for (int slot : INPUT_SLOTS) {
            if (slot == rawSlot) return true;
        }
        return false;
    }

    private ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = button(material, name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String money(long amount) {
        return NumberFormat.getNumberInstance(Locale.US).format(amount);
    }

    private long value(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0L;

        if (item.getType().name().endsWith("_SHULKER_BOX")
                && item.getItemMeta() instanceof BlockStateMeta meta
                && meta.hasBlockState()
                && meta.getBlockState() instanceof ShulkerBox box) {
            long total = 0L;
            for (ItemStack inside : box.getInventory().getContents()) {
                total = Math.addExact(total, value(inside));
            }
            return total;
        }

        return Math.multiplyExact(
                plugin.getWorthManager().sellValue(item.getType(), 1),
                item.getAmount()
        );
    }

    private long total(Inventory inventory) {
        long total = 0L;
        for (int slot : INPUT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            try {
                total = Math.addExact(total, value(item));
            } catch (ArithmeticException ex) {
                return Long.MAX_VALUE;
            }
        }
        return total;
    }

    private void refresh(Player player, Session session) {
        if (sessions.get(player.getUniqueId()) != session
                || session.inventory == null
                || session.processing) return;

        long total = total(session.inventory);
        session.inventory.setItem(TOTAL_SLOT, button(
                Material.EMERALD,
                ChatColor.GREEN + "§l💵 TOTAL VALUE",
                List.of("", ChatColor.GRAY + "Current sell value", ChatColor.WHITE + "$" + money(total))
        ));
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.inventory != event.getView().getTopInventory()) return;

        int raw = event.getRawSlot();

        if (raw >= SIZE || raw < 0) {
            Bukkit.getScheduler().runTask(plugin, () -> refresh(player, session));
            return;
        }

        if (raw == SELL_SLOT) {
            event.setCancelled(true);
            sell(player, session);
            return;
        }

        if (raw == CLOSE_SLOT) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        if (!isInputSlot(raw)) {
            event.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> refresh(player, session));
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.inventory != event.getView().getTopInventory()) return;

        for (int raw : event.getRawSlots()) {
            if (raw < SIZE && !isInputSlot(raw)) {
                event.setCancelled(true);
                return;
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> refresh(player, session));
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.inventory != event.getInventory()) return;
        if (session.processing) return;

        sessions.remove(player.getUniqueId());
        returnItems(player, session.inventory);
    }

    @EventHandler
    public void quit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.remove(player.getUniqueId());
        if (session != null && !session.processing) {
            returnItems(player, session.inventory);
        }
    }

    private void sell(Player player, Session session) {
        UUID uuid = player.getUniqueId();
        if (!transactionLocks.add(uuid)) {
            player.sendMessage(ChatColor.YELLOW + "💰 Sell transaction is already processing.");
            return;
        }

        try {
            if (sessions.get(uuid) != session
                    || session.inventory != player.getOpenInventory().getTopInventory()
                    || session.processing) return;

            long total = 0L;
            long itemCount = 0L;
            List<Integer> sellSlots = new ArrayList<>();
            List<ItemStack> soldItems = new ArrayList<>();

            for (int slot : INPUT_SLOTS) {
                ItemStack item = session.inventory.getItem(slot);
                if (item == null || item.getType().isAir()) continue;

                long value;
                try {
                    value = value(item);
                    total = Math.addExact(total, value);
                    itemCount = Math.addExact(itemCount, item.getAmount());
                } catch (ArithmeticException ex) {
                    player.sendMessage(ChatColor.RED + "The sale is too large to process safely. Nothing was sold.");
                    return;
                }

                if (value > 0) {
                    sellSlots.add(slot);
                    soldItems.add(item.clone());
                }
            }

            if (sellSlots.isEmpty() || total <= 0L) {
                player.sendMessage(ChatColor.RED + "❌ There are no sellable items in the sell area.");
                return;
            }

            session.processing = true;

            for (int slot : sellSlots) {
                session.inventory.setItem(slot, null);
            }

            boolean deposited;
            try {
                deposited = plugin.getEconomyManager().deposit(uuid, total);
            } catch (RuntimeException ex) {
                deposited = false;
                plugin.getLogger().warning("Sell transaction failed for " + player.getName() + ": " + ex.getMessage());
            }

            if (!deposited) {
                for (int i = 0; i < sellSlots.size(); i++) {
                    int slot = sellSlots.get(i);
                    ItemStack existing = session.inventory.getItem(slot);
                    ItemStack original = soldItems.get(i).clone();

                    if (existing == null || existing.getType().isAir()) {
                        session.inventory.setItem(slot, original);
                    } else {
                        Map<Integer, ItemStack> leftovers = session.inventory.addItem(original);
                        for (ItemStack leftover : leftovers.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                        }
                    }
                }

                session.processing = false;
                refresh(player, session);
                player.sendMessage(ChatColor.RED + "The economy rejected the transaction. Your items were restored.");
                return;
            }

            sessions.remove(uuid);
            session.processing = false;

            player.sendMessage(
                    ChatColor.GREEN + "💚 Sold "
                            + ChatColor.WHITE + String.format(Locale.US, "%,d", itemCount)
                            + ChatColor.GREEN + " items for "
                            + ChatColor.WHITE + plugin.getEconomyManager().format(total)
                            + ChatColor.GREEN + "!"
            );
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.25f);
            player.closeInventory();
        } finally {
            transactionLocks.remove(uuid);
        }
    }

    private void returnItems(Player player, Inventory inventory) {
        if (inventory == null) return;

        for (int slot : INPUT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            inventory.setItem(slot, null);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            for (ItemStack remaining : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
        }
    }

    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Session session = sessions.get(player.getUniqueId());
            if (session != null && !session.processing) {
                player.closeInventory();
            }
        }
        sessions.clear();
    }

    private static final class Session implements InventoryHolder {
        private final UUID sessionId;
        private Inventory inventory;
        private boolean processing;

        private Session(UUID sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
