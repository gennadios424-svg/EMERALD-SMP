package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.NumberFormat;
import java.util.*;

public final class SellMenu implements Listener {
    private static final String TITLE = ChatColor.DARK_GREEN + "💚 EMERALD SELL";
    private static final int SIZE = 54;

    // These are the only slots the transaction is ever allowed to read/write as sell slots.
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
    private final NamespacedKey guiItemKey;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> transactionLocks = new HashSet<>();

    public SellMenu(EmeraldSMP plugin) {
        this.plugin = plugin;
        this.guiItemKey = new NamespacedKey(plugin, "sell_gui_item");
    }

    public void open(Player player) {
        if (player == null || !player.isOnline()) return;

        // Close/return any previous sell session first. The previous session is removed
        // before the new inventory is opened so InventoryCloseEvent cannot race the new one.
        returnExistingSession(player);

        Session session = new Session(UUID.randomUUID(), player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(session, SIZE, TITLE);
        session.inventory = inventory;

        inventory.setItem(TOTAL_SLOT, button(
                Material.EMERALD,
                ChatColor.GREEN + "§l💵 TOTAL VALUE",
                List.of("", ChatColor.GRAY + "Current sell value", ChatColor.WHITE + "$0")
        ));
        inventory.setItem(SELL_SLOT, button(
                Material.EMERALD,
                ChatColor.GREEN + "§l💚 SELL",
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

    private void returnExistingSession(Player player) {
        Session old = sessions.remove(player.getUniqueId());
        if (old == null || old.inventory == null || old.processing) return;

        List<ItemStack> items = captureAndClear(old.inventory);
        if (player.getOpenInventory().getTopInventory() == old.inventory) {
            player.closeInventory();
        }
        Bukkit.getScheduler().runTask(plugin, () -> returnItems(player, items));
    }

    private boolean isInputSlot(int rawSlot) {
        for (int slot : INPUT_SLOTS) if (slot == rawSlot) return true;
        return false;
    }

    private boolean isSellInventory(Inventory inventory) {
        if (inventory == null || !(inventory.getHolder() instanceof Session session)) return false;
        return sessions.get(session.owner) == session && session.inventory == inventory;
    }

    private ItemStack button(Material material, String name) {
        return button(material, name, Collections.emptyList());
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) meta.setLore(lore);
            meta.getPersistentDataContainer().set(guiItemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isGuiItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(guiItemKey, PersistentDataType.BYTE);
    }

    private String money(long amount) {
        return NumberFormat.getNumberInstance(Locale.US).format(amount);
    }

    /**
     * Reads ONLY the real active sell slots. No cached item list is used.
     */
    private long value(ItemStack item) {
        if (item == null || item.getType().isAir() || isGuiItem(item)) return 0L;

        if (item.getType().name().endsWith("_SHULKER_BOX")
                && item.getItemMeta() instanceof BlockStateMeta meta
                && meta.hasBlockState()
                && meta.getBlockState() instanceof ShulkerBox box) {

            long total = plugin.getWorthManager().sellValue(item.getType(), 1);
            for (ItemStack inside : box.getInventory().getContents()) {
                if (inside == null || inside.getType().isAir()) continue;
                total = Math.addExact(total, value(inside));
            }
            return Math.multiplyExact(total, item.getAmount());
        }

        return plugin.getWorthManager().sellValue(item.getType(), item.getAmount());
    }

    private long total(Inventory inventory) {
        long total = 0L;
        for (int slot : INPUT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || isGuiItem(item)) continue;
            try {
                total = Math.addExact(total, value(item));
            } catch (ArithmeticException ex) {
                return Long.MAX_VALUE;
            }
        }
        return total;
    }

    private void refresh(Player player, Session session) {
        if (!player.isOnline()
                || sessions.get(player.getUniqueId()) != session
                || session.inventory == null
                || session.processing) return;

        session.inventory.setItem(
                TOTAL_SLOT,
                button(
                        Material.EMERALD,
                        ChatColor.GREEN + "§l💵 TOTAL VALUE",
                        List.of("", ChatColor.GRAY + "Current sell value",
                                ChatColor.WHITE + "$" + money(total(session.inventory)))
                )
        );
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory active = event.getView().getTopInventory();
        if (!isSellInventory(active)) return;

        Session session = (Session) active.getHolder();
        if (session.processing) {
            event.setCancelled(true);
            return;
        }

        int raw = event.getRawSlot();

        // Explicitly handle shift-clicks from the player's inventory. Bukkit's generic
        // shift transfer is deliberately not trusted because it can target non-input
        // slots in a custom container.
        if (raw >= SIZE && (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && !clicked.getType().isAir()) {
                ItemStack remaining = moveToSellSlots(active, clicked);
                event.getWhoClicked().getInventory().setItem(event.getSlot(),
                        remaining == null || remaining.getType().isAir() ? null : remaining);
            }
            Bukkit.getScheduler().runTask(plugin, () -> refresh(player, session));
            return;
        }

        if (raw >= SIZE) {
            Bukkit.getScheduler().runTask(plugin, () -> refresh(player, session));
            return;
        }

        if (raw == SELL_SLOT) {
            event.setCancelled(true);
            sell(player, active, session);
            return;
        }

        if (raw == CLOSE_SLOT) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        // Every non-input top slot is decoration/button space and cannot be modified.
        if (!isInputSlot(raw)) {
            event.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> refresh(player, session));
    }

    private ItemStack moveToSellSlots(Inventory active, ItemStack source) {
        ItemStack remaining = source.clone();

        // First merge with existing compatible stacks.
        for (int slot : INPUT_SLOTS) {
            if (remaining.getAmount() <= 0) break;
            ItemStack existing = active.getItem(slot);
            if (existing == null || existing.getType().isAir()) continue;
            if (!existing.isSimilar(remaining)) continue;

            int room = Math.min(remaining.getMaxStackSize(), existing.getMaxStackSize()) - existing.getAmount();
            if (room <= 0) continue;

            int moved = Math.min(room, remaining.getAmount());
            existing.setAmount(existing.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
        }

        // Then use empty input slots only.
        for (int slot : INPUT_SLOTS) {
            if (remaining.getAmount() <= 0) break;
            ItemStack existing = active.getItem(slot);
            if (existing != null && !existing.getType().isAir()) continue;

            int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            active.setItem(slot, placed);
            remaining.setAmount(remaining.getAmount() - moved);
        }

        return remaining.getAmount() <= 0 ? null : remaining;
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory active = event.getView().getTopInventory();
        if (!isSellInventory(active)) return;

        Session session = (Session) active.getHolder();
        if (session.processing) {
            event.setCancelled(true);
            return;
        }

        // Dragging is allowed only into actual sell slots. Buttons/decorations remain locked.
        for (int raw : event.getRawSlots()) {
            if (raw < SIZE && !isInputSlot(raw)) {
                event.setCancelled(true);
                return;
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> refresh(player, session));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inventory = event.getInventory();
        if (!isSellInventory(inventory)) return;

        Session session = (Session) inventory.getHolder();
        if (session.processing) return;

        sessions.remove(player.getUniqueId(), session);
        List<ItemStack> items = captureAndClear(inventory);

        // Paper warns against changing/reopening inventory views from InventoryCloseEvent.
        // Only the return/drop operation is scheduled for the next tick.
        Bukkit.getScheduler().runTask(plugin, () -> returnItems(player, items));
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.remove(player.getUniqueId());
        if (session == null || session.processing || session.inventory == null) return;

        List<ItemStack> items = captureAndClear(session.inventory);
        // Player inventory/world are still valid during quit handling; schedule the
        // final return/drop so no close-view mutation is attempted from the event.
        Bukkit.getScheduler().runTask(plugin, () -> returnItems(player, items));
    }

    private List<ItemStack> captureAndClear(Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        if (inventory == null) return items;

        for (int slot : INPUT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || isGuiItem(item)) continue;
            items.add(item.clone());
            inventory.setItem(slot, null);
        }
        return items;
    }

    private void returnItems(Player player, List<ItemStack> items) {
        if (player == null || items == null) return;

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            for (ItemStack remaining : leftovers.values()) {
                if (remaining == null || remaining.getType().isAir()) continue;
                player.getWorld().dropItemNaturally(player.getLocation(), remaining.clone());
            }
        }
    }

    private void sell(Player player, Inventory active, Session session) {
        UUID uuid = player.getUniqueId();

        if (!transactionLocks.add(uuid)) {
            player.sendMessage(ChatColor.YELLOW + "💰 Sell transaction is already processing.");
            return;
        }

        try {
            if (sessions.get(uuid) != session
                    || active != player.getOpenInventory().getTopInventory()
                    || session.processing) {
                return;
            }

            long total = 0L;
            long itemCount = 0L;
            List<Integer> sellSlots = new ArrayList<>();
            List<ItemStack> soldItems = new ArrayList<>();

            // Snapshot the authoritative active container before changing anything.
            for (int slot : INPUT_SLOTS) {
                ItemStack item = active.getItem(slot);
                if (item == null || item.getType().isAir() || isGuiItem(item)) continue;

                try {
                    long itemValue = value(item);
                    if (itemValue <= 0L) continue; // unsupported item: leave it untouched

                    total = Math.addExact(total, itemValue);
                    itemCount = Math.addExact(itemCount, item.getAmount());
                    sellSlots.add(slot);
                    soldItems.add(item.clone());
                } catch (ArithmeticException ex) {
                    player.sendMessage(ChatColor.RED + "The sale is too large to process safely. Nothing was sold.");
                    return;
                }
            }

            if (sellSlots.isEmpty() || total <= 0L) {
                boolean hasUnsupported = false;
                for (int slot : INPUT_SLOTS) {
                    ItemStack item = active.getItem(slot);
                    if (item != null && !item.getType().isAir() && !isGuiItem(item)
                            && plugin.getWorthManager().sellValue(item.getType(), 1) <= 0L) {
                        hasUnsupported = true;
                        break;
                    }
                }

                if (hasUnsupported) {
                    player.sendMessage(ChatColor.YELLOW + "⚠ Some items cannot be sold. They have been left in the container.");
                } else {
                    player.sendMessage(ChatColor.RED + "❌ There are no sellable items in the sell area.");
                }
                return;
            }

            session.processing = true;

            // Remove only the exact slots we snapshotted. No player-inventory clearing,
            // no cache clearing, and no GUI metadata is involved.
            for (int slot : sellSlots) {
                active.setItem(slot, null);
            }

            boolean deposited;
            try {
                deposited = plugin.getEconomyManager().deposit(uuid, total);
            } catch (RuntimeException ex) {
                deposited = false;
                plugin.getLogger().warning("Sell transaction failed for " + player.getName() + ": " + ex.getMessage());
            }

            if (!deposited) {
                // Economy failed: restore the exact ItemStacks before unlocking the session.
                for (int i = 0; i < sellSlots.size(); i++) {
                    int slot = sellSlots.get(i);
                    ItemStack original = soldItems.get(i).clone();
                    ItemStack existing = active.getItem(slot);

                    if (existing == null || existing.getType().isAir()) {
                        active.setItem(slot, original);
                    } else {
                        Map<Integer, ItemStack> leftovers = active.addItem(original);
                        for (ItemStack leftover : leftovers.values()) {
                            if (leftover != null && !leftover.getType().isAir()) {
                                player.getWorld().dropItemNaturally(player.getLocation(), leftover.clone());
                            }
                        }
                    }
                }

                session.processing = false;
                refresh(player, session);
                player.sendMessage(ChatColor.RED + "The economy rejected the transaction. Your items were restored.");
                return;
            }

            // Money was deposited exactly once and only after the active sell-container
            // snapshot succeeded. The successful slots remain empty.
            session.processing = false;
            sessions.remove(uuid, session);

            player.sendMessage(
                    ChatColor.GREEN + "💚 Sold "
                            + ChatColor.WHITE + String.format(Locale.US, "%,d", itemCount)
                            + ChatColor.GREEN + " items for "
                            + ChatColor.WHITE + plugin.getEconomyManager().format(total)
                            + ChatColor.GREEN + "!"
            );
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.25f);
            player.closeInventory();

        } finally {
            transactionLocks.remove(uuid);
        }
    }

    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Session session = sessions.remove(player.getUniqueId());
            if (session == null || session.processing || session.inventory == null) continue;

            List<ItemStack> items = captureAndClear(session.inventory);
            if (player.getOpenInventory().getTopInventory() == session.inventory) {
                player.closeInventory();
            }
            returnItems(player, items);
        }
        sessions.clear();
        transactionLocks.clear();
    }

    private static final class Session implements InventoryHolder {
        private final UUID sessionId;
        private final UUID owner;
        private Inventory inventory;
        private boolean processing;

        private Session(UUID sessionId, UUID owner) {
            this.sessionId = sessionId;
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
