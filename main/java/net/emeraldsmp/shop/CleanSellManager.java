package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Real writable /sell container. Slots 0-44 are genuine input slots; 45-53 are controls.
 */
public final class CleanSellManager {
    private static final int INPUT = 45;
    private static final int TOTAL = 47;
    private static final int SELL = 49;
    private static final int CLOSE = 53;

    private final EmeraldSMP plugin;
    private final Map<UUID, Inventory> open = new HashMap<>();
    private final Set<UUID> locks = new HashSet<>();

    public CleanSellManager(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        close(player);
        Inventory inventory = Bukkit.createInventory(new SellHolder(), 54, "§2§l💚 SELL ITEMS");
        for (int slot = INPUT; slot < 54; slot++) {
            inventory.setItem(slot, button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
        inventory.setItem(TOTAL, button(Material.EMERALD, "§a§l💵 TOTAL VALUE",
                List.of("§7Current sell value", "§f$0")));
        inventory.setItem(SELL, button(Material.EMERALD_BLOCK, "§a§l💰 SELL",
                List.of("§7Sell every valid item in the input area", "", "§e▶ Click to sell")));
        inventory.setItem(CLOSE, button(Material.BARRIER, "§c§l✖ CLOSE",
                List.of("§7Return unsold items")));

        open.put(player.getUniqueId(), inventory);
        player.openInventory(inventory);
        refresh(player);
    }

    public boolean isOpen(Player player, Inventory inventory) {
        return open.get(player.getUniqueId()) == inventory;
    }

    public boolean isInputSlot(int slot) {
        return slot >= 0 && slot < INPUT;
    }

    public boolean isControl(int slot) {
        return slot >= INPUT && slot < 54;
    }

    public void control(Player player, int slot) {
        if (slot == SELL) {
            sell(player);
        } else if (slot == CLOSE) {
            player.closeInventory();
        }
    }

    public void refreshLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && open.containsKey(player.getUniqueId())) {
                refresh(player);
            }
        });
    }

    private void refresh(Player player) {
        Inventory inventory = open.get(player.getUniqueId());
        if (inventory == null) return;

        long total = 0L;
        for (int slot = 0; slot < INPUT; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            try {
                long value = plugin.getWorthManager().sellValue(item.getType(), item.getAmount());
                total = Math.addExact(total, value);
            } catch (ArithmeticException ignored) {
                total = Long.MAX_VALUE;
                break;
            }
        }

        inventory.setItem(TOTAL, button(Material.EMERALD, "§a§l💵 TOTAL VALUE",
                List.of("§7Current sell value", "§f" + plugin.getEconomyManager().format(total))));
    }

    private void sell(Player player) {
        UUID uuid = player.getUniqueId();
        if (!locks.add(uuid)) {
            player.sendMessage("§e💰 Sell transaction is already processing.");
            return;
        }

        try {
            Inventory inventory = open.get(uuid);
            if (inventory == null) return;

            long total = 0L;
            long count = 0L;
            List<Integer> slots = new ArrayList<>();
            List<ItemStack> soldItems = new ArrayList<>();

            // Validate and calculate everything before changing the container.
            for (int slot = 0; slot < INPUT; slot++) {
                ItemStack item = inventory.getItem(slot);
                if (item == null || item.getType().isAir()) continue;

                var worth = plugin.getWorthManager().get(item.getType());
                if (worth == null || !worth.enabled() || worth.worth() <= 0) {
                    continue;
                }

                try {
                    total = Math.addExact(total,
                            plugin.getWorthManager().sellValue(item.getType(), item.getAmount()));
                    count = Math.addExact(count, item.getAmount());
                } catch (ArithmeticException ignored) {
                    player.sendMessage("§cThe sale is too large to process safely. Nothing was sold.");
                    return;
                }

                slots.add(slot);
                soldItems.add(item.clone());
            }

            if (slots.isEmpty() || total <= 0L) {
                player.sendMessage("§c❌ There are no sellable items in the sell area.");
                return;
            }

            // Remove only after every item was validated and the total was calculated.
            for (int slot : slots) {
                inventory.setItem(slot, null);
            }

            if (!plugin.getEconomyManager().deposit(uuid, total)) {
                for (int i = 0; i < slots.size(); i++) {
                    inventory.setItem(slots.get(i), soldItems.get(i));
                }
                player.sendMessage("§cThe economy rejected the transaction. Your items were restored.");
                refresh(player);
                return;
            }

            // Remove tracking BEFORE close so InventoryCloseEvent cannot return sold items.
            open.remove(uuid);
            player.sendMessage("§a💰 Sold §f" + String.format(Locale.US, "%,d", count)
                    + " §aitems for §f" + plugin.getEconomyManager().format(total) + "§a.");
            player.closeInventory();
        } finally {
            locks.remove(uuid);
        }
    }

    public void close(Player player) {
        if (locks.contains(player.getUniqueId())) return;

        Inventory inventory = open.remove(player.getUniqueId());
        if (inventory != null) {
            returnItems(player, inventory);
        }
    }

    public void quit(Player player) {
        close(player);
    }

    private void returnItems(Player player, Inventory inventory) {
        for (int slot = 0; slot < INPUT; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            // Clear first so the item has exactly one owner during the transfer.
            inventory.setItem(slot, null);

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            for (ItemStack leftover : leftovers.values()) {
                // Final fallback: never delete overflow.
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
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

    private static final class SellHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
