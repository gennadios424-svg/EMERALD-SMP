package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
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

    public CleanSellManager(EmeraldSMP plugin) { this.plugin = plugin; }

    public void open(Player player) {
        close(player);
        Session session = new Session(UUID.randomUUID());
        Inventory inventory = Bukkit.createInventory(session, 54, "§2§l💚 SELL ITEMS");
        session.inventory = inventory;

        for (int slot = INPUT_SLOTS; slot < 54; slot++) {
            inventory.setItem(slot, button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
        inventory.setItem(TOTAL_SLOT, button(Material.EMERALD, "§a§l💵 TOTAL VALUE",
                List.of("§7Current sell value", "§f$0")));
        inventory.setItem(SELL_SLOT, button(Material.EMERALD_BLOCK, "§a§l💰 SELL",
                List.of("§7Sell every valid item in the input area", "", "§e▶ Click to sell")));
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "§c§l✖ CLOSE",
                List.of("§7Return unsold items")));

        sessions.put(player.getUniqueId(), session);
        player.openInventory(inventory);
        refresh(player, session);
    }

    public boolean isOpen(Player player, Inventory inventory) {
        Session s = sessions.get(player.getUniqueId());
        return s != null && s.inventory == inventory && s.sessionId != null;
    }

    public boolean isControl(int rawSlot) {
        return rawSlot >= INPUT_SLOTS && rawSlot < 54;
    }

    public void handleClick(Player player, InventoryClickEvent event) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.inventory != event.getView().getTopInventory() || s.processing) return;

        int raw = event.getRawSlot();

        // Protected controls are the only top-inventory slots that are cancelled.
        if (raw >= INPUT_SLOTS && raw < 54) {
            event.setCancelled(true);
            if (raw == SELL_SLOT) sell(player, s);
            else if (raw == CLOSE_SLOT) player.closeInventory();
            return;
        }

        // Explicitly implement shift-click from the player's inventory. This avoids
        // Bukkit's generic MOVE_TO_OTHER_INVENTORY routing around the protected rows.
        if (event.isShiftClick() && raw >= event.getView().getTopInventory().getSize()) {
            event.setCancelled(true);
            movePlayerStackToSellArea(player, s, event.getSlot());
            refresh(player, s);
            return;
        }

        // Ordinary clicks in the real input container are intentionally not cancelled.
        // Bukkit/Paper owns the normal place/pickup/merge/split/number-key behavior.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Session current = sessions.get(player.getUniqueId());
            if (current == s && player.isOnline() && !s.processing) refresh(player, s);
        });
    }

    private void movePlayerStackToSellArea(Player player, Session s, int playerSlot) {
        ItemStack source = player.getInventory().getItem(playerSlot);
        if (source == null || source.getType().isAir()) return;

        ItemStack remaining = source.clone();

        // First merge into compatible existing stacks.
        for (int slot = 0; slot < INPUT_SLOTS && !remaining.getType().isAir(); slot++) {
            ItemStack target = s.inventory.getItem(slot);
            if (target == null || target.getType().isAir()) continue;
            if (!target.isSimilar(remaining)) continue;

            int room = Math.min(remaining.getMaxStackSize(), target.getMaxStackSize()) - target.getAmount();
            if (room <= 0) continue;

            int moved = Math.min(room, remaining.getAmount());
            target.setAmount(target.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
            s.inventory.setItem(slot, target);
        }

        // Then fill empty input slots.
        for (int slot = 0; slot < INPUT_SLOTS && !remaining.getType().isAir(); slot++) {
            ItemStack target = s.inventory.getItem(slot);
            if (target != null && !target.getType().isAir()) continue;

            int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            s.inventory.setItem(slot, placed);
            remaining.setAmount(remaining.getAmount() - moved);
        }

        if (remaining.getType().isAir()) {
            player.getInventory().setItem(playerSlot, null);
        } else {
            player.getInventory().setItem(playerSlot, remaining);
        }
    }

    public void handleDrag(Player player, org.bukkit.event.inventory.InventoryDragEvent event) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.inventory != event.getView().getTopInventory() || s.processing) return;

        // Only protected controls cancel a drag. Drags touching input slots remain real
        // container operations; player-inventory-only drags are also left untouched.
        if (event.getRawSlots().stream().anyMatch(slot -> slot >= INPUT_SLOTS && slot < 54)) {
            event.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Session current = sessions.get(player.getUniqueId());
            if (current == s && player.isOnline() && !s.processing) refresh(player, s);
        });
    }

    private void refresh(Player player, Session s) {
        if (sessions.get(player.getUniqueId()) != s || s.inventory == null || s.processing) return;
        long total = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack item = s.inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            long value = plugin.getWorthManager().sellValue(item.getType(), item.getAmount());
            if (value > 0) {
                try { total = Math.addExact(total, value); }
                catch (ArithmeticException ex) { total = Long.MAX_VALUE; break; }
            }
        }
        s.inventory.setItem(TOTAL_SLOT, button(Material.EMERALD, "§a§l💵 TOTAL VALUE",
                List.of("§7Current sell value", "§f" + plugin.getEconomyManager().format(total))));
    }

    private void sell(Player player, Session s) {
        UUID uuid = player.getUniqueId();
        if (!transactionLocks.add(uuid)) {
            player.sendMessage("§e💰 Sell transaction is already processing.");
            return;
        }

        try {
            if (sessions.get(uuid) != s || s.inventory != player.getOpenInventory().getTopInventory() || s.processing) return;

            long total = 0;
            long itemCount = 0;
            List<Integer> sellSlots = new ArrayList<>();
            List<ItemStack> soldItems = new ArrayList<>();

            for (int slot = 0; slot < INPUT_SLOTS; slot++) {
                ItemStack item = s.inventory.getItem(slot);
                if (item == null || item.getType().isAir()) continue;

                var worth = plugin.getWorthManager().get(item.getType());
                if (worth == null || !worth.enabled() || worth.worth() <= 0) continue;

                try {
                    long value = plugin.getWorthManager().sellValue(item.getType(), item.getAmount());
                    total = Math.addExact(total, value);
                    itemCount = Math.addExact(itemCount, item.getAmount());
                    if (value > 0) {
                        sellSlots.add(slot);
                        soldItems.add(item.clone());
                    }
                } catch (ArithmeticException ex) {
                    player.sendMessage("§cThe sale is too large to process safely. Nothing was sold.");
                    return;
                }
            }

            if (sellSlots.isEmpty() || total <= 0) {
                player.sendMessage("§c❌ There are no sellable items in the sell area.");
                return;
            }

            s.processing = true;
            for (int slot : sellSlots) s.inventory.setItem(slot, null);

            if (!plugin.getEconomyManager().deposit(uuid, total)) {
                for (int i = 0; i < sellSlots.size(); i++) {
                    int slot = sellSlots.get(i);
                    ItemStack existing = s.inventory.getItem(slot);
                    if (existing == null || existing.getType().isAir()) s.inventory.setItem(slot, soldItems.get(i));
                    else {
                        Map<Integer, ItemStack> left = s.inventory.addItem(soldItems.get(i).clone());
                        for (ItemStack item : left.values()) player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }
                s.processing = false;
                player.sendMessage("§cThe economy rejected the transaction. Your items were restored.");
                refresh(player, s);
                return;
            }

            sessions.remove(uuid);
            s.processing = false;
            player.sendMessage("§a💰 Sold §f" + String.format(Locale.US, "%,d", itemCount)
                    + " §aitems for §f" + plugin.getEconomyManager().format(total) + "§a.");
            player.closeInventory();
        } finally {
            transactionLocks.remove(uuid);
        }
    }

    public void close(Player player) {
        UUID uuid = player.getUniqueId();
        if (transactionLocks.contains(uuid)) return;

        Session s = sessions.remove(uuid);
        if (s == null || s.processing || s.inventory == null) return;
        returnItems(player, s.inventory);
    }

    public void quit(Player player) { close(player); }

    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) close(player);
        sessions.clear();
    }

    private void returnItems(Player player, Inventory inventory) {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            inventory.setItem(slot, null);
            Map<Integer, ItemStack> left = player.getInventory().addItem(item.clone());
            for (ItemStack remaining : left.values()) {
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
        private Session(UUID sessionId) { this.sessionId = sessionId; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
