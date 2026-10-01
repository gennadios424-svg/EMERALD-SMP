package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class CleanSellManager {
    private static final int INPUT_SLOTS = 45;
    private static final int TOTAL_SLOT = 47;
    private static final int SELL_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private final EmeraldSMP plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> transactionLocks = new HashSet<>();

    public CleanSellManager(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        close(player);

        Session session = new Session(UUID.randomUUID());
        Inventory inventory = Bukkit.createInventory(session, 54, "§2§l💚 SELL ITEMS");
        session.inventory = inventory;

        for (int slot = INPUT_SLOTS; slot < 54; slot++) {
            inventory.setItem(slot, button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }

        inventory.setItem(TOTAL_SLOT, button(
                Material.EMERALD,
                "§a§l💵 TOTAL VALUE",
                List.of("§7Current sell value", "§f$0")
        ));
        inventory.setItem(SELL_SLOT, button(
                Material.EMERALD_BLOCK,
                "§a§l💰 SELL",
                List.of("§7Sell every valid item in the input area", "", "§e▶ Click to sell")
        ));
        inventory.setItem(CLOSE_SLOT, button(
                Material.BARRIER,
                "§c§l✖ CLOSE",
                List.of("§7Return unsold items")
        ));

        sessions.put(player.getUniqueId(), session);
        player.openInventory(inventory);
        refresh(session);
    }

    public boolean isOpen(Player player, Inventory inventory) {
        Session session = sessions.get(player.getUniqueId());
        return session != null
                && session.inventory == inventory
                && session.sessionId != null
                && !session.processing;
    }

    public boolean isControl(int rawSlot) {
        return rawSlot >= INPUT_SLOTS && rawSlot < 54;
    }

    public void handleClick(Player player, InventoryClickEvent event) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.inventory != event.getView().getTopInventory() || session.processing) {
            return;
        }

        int raw = event.getRawSlot();

        // Only the nine control slots are protected. The 45 input slots and the
        // player's inventory are deliberately left as normal Bukkit container slots.
        if (raw >= INPUT_SLOTS && raw < 54) {
            event.setCancelled(true);

            if (raw == SELL_SLOT) {
                sell(player, session);
            } else if (raw == CLOSE_SLOT) {
                player.closeInventory();
            }
            return;
        }

        // Bukkit can handle ordinary clicks directly. Shift-click from the
        // player's inventory is handled explicitly so only the 45 sell slots
        // are considered and no protected button can ever receive an item.
        if (event.isShiftClick()
                && event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getBottomInventory())
                && raw >= event.getView().getTopInventory().getSize()) {
            event.setCancelled(true);
            moveShiftClickedItem(player, session, event.getSlot());
            return;
        }

        refreshNextTick(player, session);
    }

    private void moveShiftClickedItem(Player player, Session session, int playerSlot) {
        if (playerSlot < 0 || playerSlot >= player.getInventory().getSize()) return;

        ItemStack source = player.getInventory().getItem(playerSlot);
        if (source == null || source.getType().isAir()) return;

        // Shift-click only transfers items that can actually be sold.
        if (!isSellable(source)) return;

        ItemStack moving = source.clone();
        Map<Integer, ItemStack> leftovers = session.inventory.addItem(moving);

        int remaining = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        int moved = source.getAmount() - remaining;

        if (moved <= 0) return;

        if (remaining <= 0) {
            player.getInventory().setItem(playerSlot, null);
        } else {
            ItemStack remainder = source.clone();
            remainder.setAmount(remaining);
            player.getInventory().setItem(playerSlot, remainder);
        }

        refresh(session);
        player.updateInventory();
    }

    public void handleDrag(Player player, InventoryDragEvent event) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.inventory != event.getView().getTopInventory() || session.processing) {
            return;
        }

        // A drag is allowed if every affected top slot is a real sell slot.
        // Any control-slot involvement cancels the whole drag; player-inventory
        // slots may participate normally.
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= INPUT_SLOTS && rawSlot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }

        refreshNextTick(player, session);
    }

    private void refreshNextTick(Player player, Session session) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Session current = sessions.get(player.getUniqueId());
            if (current == session && player.isOnline() && !session.processing) {
                refresh(session);
            }
        });
    }

    private void refresh(Session session) {
        if (session.inventory == null) return;

        long total = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack item = session.inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            try {
                long value = plugin.getWorthManager().sellValue(item.getType(), item.getAmount());
                total = Math.addExact(total, value);
            } catch (ArithmeticException ex) {
                total = Long.MAX_VALUE;
                break;
            }
        }

        session.inventory.setItem(TOTAL_SLOT, button(
                Material.EMERALD,
                "§a§l💵 TOTAL VALUE",
                List.of("§7Current sell value", "§f" + plugin.getEconomyManager().format(total))
        ));
    }

    private boolean isSellable(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return plugin.getWorthManager().sellValue(item.getType(), 1) > 0;
    }

    private void sell(Player player, Session session) {
        UUID uuid = player.getUniqueId();

        if (!transactionLocks.add(uuid)) {
            player.sendMessage("§e💰 Sell transaction is already processing.");
            return;
        }

        try {
            if (sessions.get(uuid) != session
                    || session.inventory != player.getOpenInventory().getTopInventory()
                    || session.processing) {
                return;
            }

            long total = 0;
            long itemCount = 0;
            List<Integer> soldSlots = new ArrayList<>();
            List<ItemStack> soldItems = new ArrayList<>();

            // Complete validation pass. Nothing is removed until every sellable
            // stack has a valid value and the total fits in a long.
            for (int slot = 0; slot < INPUT_SLOTS; slot++) {
                ItemStack item = session.inventory.getItem(slot);
                if (item == null || item.getType().isAir()) continue;

                long value;
                try {
                    value = plugin.getWorthManager().sellValue(item.getType(), item.getAmount());
                    if (value <= 0) continue;

                    total = Math.addExact(total, value);
                    itemCount = Math.addExact(itemCount, item.getAmount());
                } catch (ArithmeticException ex) {
                    player.sendMessage("§cThe sale is too large to process safely. Nothing was sold.");
                    return;
                }

                soldSlots.add(slot);
                soldItems.add(item.clone());
            }

            if (soldSlots.isEmpty() || total <= 0) {
                player.sendMessage("§c❌ There are no sellable items in the sell area.");
                return;
            }

            session.processing = true;

            // Remove only after the full transaction has been validated.
            for (int slot : soldSlots) {
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
                for (int i = 0; i < soldSlots.size(); i++) {
                    int slot = soldSlots.get(i);
                    ItemStack item = soldItems.get(i).clone();
                    ItemStack existing = session.inventory.getItem(slot);

                    if (existing == null || existing.getType().isAir()) {
                        session.inventory.setItem(slot, item);
                    } else {
                        Map<Integer, ItemStack> left = session.inventory.addItem(item);
                        for (ItemStack remaining : left.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), remaining);
                        }
                    }
                }

                session.processing = false;
                refresh(session);
                player.sendMessage("§cThe economy rejected the transaction. Your items were restored.");
                return;
            }

            // Commit succeeded. The session is consumed exactly once.
            sessions.remove(uuid);
            session.processing = false;

            player.sendMessage(
                    "§a💰 Sold §f" + String.format(Locale.US, "%,d", itemCount)
                            + " §aitems for §f" + plugin.getEconomyManager().format(total) + "§a."
            );
            player.closeInventory();
        } finally {
            transactionLocks.remove(uuid);
        }
    }

    public void close(Player player) {
        UUID uuid = player.getUniqueId();

        if (transactionLocks.contains(uuid)) return;

        Session session = sessions.remove(uuid);
        if (session == null || session.processing || session.inventory == null) return;

        returnItems(player, session.inventory);
    }

    public void quit(Player player) {
        close(player);
    }

    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            close(player);
        }
        sessions.clear();
    }

    private void returnItems(Player player, Inventory inventory) {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            inventory.setItem(slot, null);

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            for (ItemStack remaining : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
        }
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
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
