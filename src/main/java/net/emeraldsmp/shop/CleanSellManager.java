package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.NumberFormat;
import java.util.*;

public final class CleanSellManager implements Listener, CommandExecutor {
    private static final String TITLE = "§2§l💚 SELL ITEMS";
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /sell.");
            return true;
        }
        open(player);
        return true;
    }

    public void open(Player player) {
        close(player);

        Session session = new Session(UUID.randomUUID());
        Inventory inventory = Bukkit.createInventory(session, 54, TITLE);
        session.inventory = inventory;

        for (int slot = INPUT_SLOTS; slot < 54; slot++) {
            inventory.setItem(slot, button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }

        inventory.setItem(TOTAL_SLOT, button(
                Material.EMERALD,
                "§a§l💵 TOTAL VALUE",
                List.of("", "§7Current sell value", "§f$0")
        ));
        inventory.setItem(SELL_SLOT, button(
                Material.EMERALD_BLOCK,
                "§a§l💰 SELL ALL",
                List.of("", "§7Sell everything in the input area", "§7Shulker contents are included", "", "§e▶ Click to sell")
        ));
        inventory.setItem(CLOSE_SLOT, button(
                Material.BARRIER,
                "§c§l✖ CLOSE",
                List.of("§7Unsold items are returned to you")
        ));

        sessions.put(player.getUniqueId(), session);
        player.openInventory(inventory);
        refresh(session);
    }

    public boolean isOpen(Player player, Inventory inventory) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.inventory == inventory && !session.processing;
    }

    public boolean isControl(int rawSlot) {
        return rawSlot >= INPUT_SLOTS && rawSlot < 54;
    }

    public void handleClick(Player player, InventoryClickEvent event) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.inventory != event.getView().getTopInventory() || session.processing) return;

        int raw = event.getRawSlot();

        if (raw >= INPUT_SLOTS && raw < 54) {
            event.setCancelled(true);
            if (raw == SELL_SLOT) sell(player, session);
            else if (raw == CLOSE_SLOT) player.closeInventory();
            return;
        }

        refreshNextTick(player, session);
    }

    public void handleDrag(Player player, InventoryDragEvent event) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.inventory != event.getView().getTopInventory() || session.processing) return;

        // This is the same container model as the proven Sunlight sell menu:
        // all top 45 slots are real input slots; controls are protected.
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= INPUT_SLOTS && rawSlot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }

        refreshNextTick(player, session);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (isOpen(player, event.getInventory())) close(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        close(event.getPlayer());
    }

    private void refreshNextTick(Player player, Session session) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sessions.get(player.getUniqueId()) == session && player.isOnline() && !session.processing) {
                refresh(session);
            }
        });
    }

    private void refresh(Session session) {
        if (session.inventory == null) return;

        long total = total(session.inventory);
        session.inventory.setItem(TOTAL_SLOT, button(
                Material.EMERALD,
                "§a§l💵 TOTAL VALUE",
                List.of("", "§7Current sell value", "§f$" + NumberFormat.getNumberInstance(Locale.US).format(total))
        ));
    }

    private long total(Inventory inventory) {
        long total = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
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

    // Port of the working Sunlight sell behavior: shulker boxes are opened
    // logically and their stored contents are sold too. The Emerald SMP
    // WorthManager remains the only authoritative price source.
    private long value(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;

        long total = 0;
        if (item.getType().name().endsWith("SHULKER_BOX")
                && item.getItemMeta() instanceof BlockStateMeta meta
                && meta.hasBlockState()
                && meta.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack inside : box.getInventory().getContents()) {
                total = Math.addExact(total, value(inside));
            }
        } else {
            long unit = plugin.getWorthManager().sellValue(item.getType(), 1);
            total = Math.multiplyExact(unit, item.getAmount());
        }

        return total;
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

            for (int slot = 0; slot < INPUT_SLOTS; slot++) {
                ItemStack item = session.inventory.getItem(slot);
                if (item == null || item.getType().isAir()) continue;

                long value;
                try {
                    value = value(item);
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

            // Remove only after the complete transaction has been validated.
            for (int slot : soldSlots) session.inventory.setItem(slot, null);

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

            sessions.remove(uuid);
            session.processing = false;

            player.sendMessage("§a💰 Sold §f" + NumberFormat.getNumberInstance(Locale.US).format(itemCount)
                    + " §aitems for §f" + plugin.getEconomyManager().format(total) + "§a.");
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
        for (Player player : Bukkit.getOnlinePlayers()) close(player);
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

    private static final class Session implements org.bukkit.inventory.InventoryHolder {
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
