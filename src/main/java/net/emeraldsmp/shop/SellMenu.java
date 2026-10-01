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
    private static final String TITLE = ChatColor.DARK_GREEN + "💚 EMERALD SELL";
    private static final int INPUT_SLOTS = 45;
    private static final int SELL_SLOT = 49;

    /*
     * These are visual/control slots only. The actual working sell container
     * remains the same 54-slot inventory with slots 0-44 used for input.
     * The frame occupies a few of those input slots, so those exact slots
     * must be protected without disabling the rest of the container.
     */
    private static final int[] PROTECTED_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17, 18, 26, 27, 35, 36, 44,
            45, 46, 47, 48, 49, 50, 51, 52, 53
    };

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
        ItemStack dark = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack emerald = item(Material.GREEN_STAINED_GLASS_PANE, ChatColor.GREEN + "💚");

        for (int row = 0; row < inventory.getSize() / 9; row++) {
            inventory.setItem(row * 9, dark);
            inventory.setItem(row * 9 + 8, dark);
        }

        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, emerald);
            inventory.setItem(inventory.getSize() - 9 + slot, emerald);
        }
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, TITLE);

        frame(inventory);
        inventory.setItem(45, item(
                Material.EMERALD,
                ChatColor.GREEN + "💚 Emerald Sell",
                "",
                ChatColor.GRAY + "Place items in the top 45 slots."
        ));
        inventory.setItem(SELL_SLOT, item(
                Material.EMERALD_BLOCK,
                ChatColor.GREEN + "SELL ALL",
                "",
                ChatColor.GRAY + "Shulkers are opened and their contents are sold too."
        ));
        inventory.setItem(53, item(
                Material.BARRIER,
                ChatColor.WHITE + "Close",
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
                ChatColor.GREEN + "💚 Sold items for "
                        + ChatColor.GOLD + "$" + money(total)
                        + ChatColor.GREEN + "!"
        );
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.25f);
        player.closeInventory();
    }

    private boolean isProtectedSlot(int rawSlot) {
        for (int slot : PROTECTED_SLOTS) {
            if (slot == rawSlot) {
                return true;
            }
        }
        return false;
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

        // Lock only the visual/control slots. Real sell-input slots and the
        // player's bottom inventory keep the existing working container model.
        if (isProtectedSlot(rawSlot)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (isProtectedSlot(rawSlot)) {
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
