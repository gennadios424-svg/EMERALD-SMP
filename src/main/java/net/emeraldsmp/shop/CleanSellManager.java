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
        if (s == null || s.inventory != event.getView().getTopInventory()) return;

        int raw = event.getRawSlot();
        if (raw >= INPUT_SLOTS && raw < 54) {
            event.setCancelled(true);
            if (raw == SELL_SLOT) sell(player, s);
            else if (raw == CLOSE_SLOT) player.closeInventory();
            return;
        }

        // Never cancel normal container interaction in the input area or the player's inventory.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Session current = sessions.get(player.getUniqueId());
            if (current != null && current == s && player.isOnline()) refresh(player, s);
        });
    }

    public void handleDrag(Player player, org.bukkit.event.inventory.InventoryDragEvent event) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.inventory != event.getView().getTopInventory()) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot >= INPUT_SLOTS && slot < 54)) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Session current = sessions.get(player.getUniqueId());
            if (current == s && player.isOnline()) refresh(player, s);
        });
    }

    private void refresh(Player player, Session s) {
        if (sessions.get(player.getUniqueId()) != s || s.inventory == null) return;
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
            if (sessions.get(uuid) != s || s.inventory != player.getOpenInventory().getTopInventory()) return;

            long total = 0;
            long itemCount = 0;
            List<Integer> sellSlots = new ArrayList<>();
            List<ItemStack> soldItems = new ArrayList<>();

            // Validate the complete transaction before changing any inventory or balance.
            for (int slot = 0; slot < INPUT_SLOTS; slot++) {
                ItemStack item = s.inventory.getItem(slot);
                if (item == null || item.getType().isAir()) continue;

                var worth = plugin.getWorthManager().get(item.getType());
                if (worth == null || !worth.enabled() || worth.worth() <= 0) continue;

                long value;
                try {
                    value = plugin.getWorthManager().sellValue(item.getType(), item.getAmount());
                    total = Math.addExact(total, value);
                    itemCount = Math.addExact(itemCount, item.getAmount());
                } catch (ArithmeticException ex) {
                    player.sendMessage("§cThe sale is too large to process safely. Nothing was sold.");
                    return;
                }

                if (value > 0) {
                    sellSlots.add(slot);
                    soldItems.add(item.clone());
                }
            }

            if (sellSlots.isEmpty() || total <= 0) {
                player.sendMessage("§c❌ There are no sellable items in the sell area.");
                return;
            }

            // Lock the session before the commit. All changes below occur on the server thread.
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
                player.sendMessage("§cThe economy rejected the transaction. Your items were restored.");
                s.processing = false;
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
