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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public final class SellMenu implements Listener {
    private static final String TITLE = ChatColor.GOLD + "☀ Sunlight Sell";
    private static final int INPUT_SLOTS = 45;
    private static final int SELL_SLOT = 49;

    private final EmeraldSMP plugin;

    public SellMenu(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String money(long amount) {
        return NumberFormat.getNumberInstance(Locale.US).format(amount);
    }

    private void frame(Inventory inventory) {
        ItemStack side = item(Material.ORANGE_STAINED_GLASS_PANE, " ");
        ItemStack accent = item(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.GOLD + "☀");

        for (int row = 0; row < inventory.getSize() / 9; row++) {
            inventory.setItem(row * 9, side);
            inventory.setItem(row * 9 + 8, side);
        }

        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, accent);
            inventory.setItem(inventory.getSize() - 9 + slot, accent);
        }
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, TITLE);

        frame(inventory);
        inventory.setItem(45, item(
                Material.SUNFLOWER,
                ChatColor.GOLD + "☀ Sunlight Sell",
                "",
                ChatColor.GRAY + "Place items in the top 45 slots."
        ));
        inventory.setItem(SELL_SLOT, item(
                Material.GOLD_INGOT,
                ChatColor.GREEN + "SELL ALL",
                "",
                ChatColor.GRAY + "Shulkers are opened and their contents are sold too."
        ));
        inventory.setItem(53, item(
                Material.BARRIER,
                ChatColor.RED + "Close",
                ChatColor.GRAY + "Items are returned to you."
        ));

        player.openInventory(inventory);
    }

    private long value(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0L;
        }

        long total = 0L;

        if (stack.getType().name().endsWith("SHULKER_BOX")
                && stack.getItemMeta() instanceof BlockStateMeta meta
                && meta.hasBlockState()
                && meta.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack inside : box.getInventory().getContents()) {
                total = Math.addExact(total, value(inside));
            }
        } else {
            long unit = plugin.getWorthManager().sellValue(stack.getType(), 1);
            total = Math.multiplyExact(unit, stack.getAmount());
        }

        return total;
    }

    private long total(Inventory inventory) {
        long total = 0L;

        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            total = Math.addExact(total, value(inventory.getItem(slot)));
        }

        return total;
    }

    private void sell(Player player, Inventory inventory) {
        final long total;

        try {
            total = total(inventory);
        } catch (ArithmeticException exception) {
            player.sendMessage(ChatColor.RED + "The sale is too large to process safely.");
            return;
        }

        if (total <= 0L) {
            player.sendMessage(ChatColor.RED + "Place items in the sell menu first.");
            return;
        }

        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            inventory.setItem(slot, null);
        }

        if (!plugin.getEconomyManager().deposit(player.getUniqueId(), total)) {
            player.sendMessage(ChatColor.RED + "The economy rejected the transaction.");
            return;
        }

        player.sendMessage(
                ChatColor.GREEN + "☀ Sold items for "
                        + ChatColor.GOLD + "$" + money(total)
                        + ChatColor.GREEN + "!"
        );
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.25f);
        player.closeInventory();
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !event.getView().getTitle().equals(TITLE)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= 54) {
            return;
        }

        if (rawSlot == SELL_SLOT) {
            event.setCancelled(true);
            sell(player, event.getView().getTopInventory());
            return;
        }

        if (rawSlot >= INPUT_SLOTS) {
            event.setCancelled(true);
        }
        // Intentionally do not cancel input-area clicks or bottom-inventory
        // shift-clicks: this is the Sunlight container interaction model.
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= INPUT_SLOTS) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !event.getView().getTitle().equals(TITLE)) {
            return;
        }

        Inventory inventory = event.getView().getTopInventory();

        Bukkit.getScheduler().runTask(plugin, () -> returnItems(player, inventory));
    }

    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTitle().equals(TITLE)) {
                player.closeInventory();
            }
        }
    }

    private void returnItems(Player player, Inventory inventory) {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            inventory.setItem(slot, null);

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack.clone());
            for (ItemStack remaining : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
        }
    }
}
