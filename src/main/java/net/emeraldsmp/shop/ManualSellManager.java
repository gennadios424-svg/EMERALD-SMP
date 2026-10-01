package net.emeraldsmp.shop;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.worth.WorthEntry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class ManualSellManager {
    private static final int ITEM_SLOTS = 45;
    private static final int BACK_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private final EmeraldSMP plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public ManualSellManager(EmeraldSMP plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        sessions.remove(player.getUniqueId());
        Inventory inv = plugin.getServer().createInventory(null, 54, "§2§l💚 SELL §8• §fSELECT ITEM");

        List<Material> materials = getSellableMaterials(player);
        for (int i = 0; i < Math.min(ITEM_SLOTS, materials.size()); i++) {
            Material material = materials.get(i);
            int amount = count(player, material);
            long value = plugin.getWorthManager().sellValue(material, amount);
            inv.setItem(i, icon(material, "§a§l" + pretty(material), Arrays.asList(
                    "§7In inventory: §f" + fmt(amount),
                    "§7Sell value: §a" + plugin.getEconomyManager().format(value),
                    "",
                    "§e▶ Click to select this item"
            )));
        }

        inv.setItem(BACK_SLOT, icon(Material.ARROW, "§e§l⬅ BACK", Collections.singletonList("§7Return to the main shop")));
        inv.setItem(INFO_SLOT, icon(Material.EMERALD, "§a§l💰 MANUAL SELLING", Arrays.asList(
                "§7Nothing is sold automatically.",
                "§7Choose an item you actually own.",
                "§7Choose the exact amount.",
                "",
                "§eYou must press CONFIRM SALE."
        )));
        inv.setItem(CLOSE_SLOT, icon(Material.BARRIER, "§c§l✕ CLOSE", Collections.emptyList()));

        sessions.put(player.getUniqueId(), new Session(null, 0));
        player.openInventory(inv);
    }

    private void openAmount(Player player, Material material) {
        int owned = count(player, material);
        WorthEntry worth = plugin.getWorthManager().get(material);
        if (owned <= 0 || worth == null || !worth.enabled() || worth.worth() <= 0) {
            open(player);
            return;
        }

        sessions.put(player.getUniqueId(), new Session(material, 1));
        Inventory inv = plugin.getServer().createInventory(null, 27, "§2§l💚 SELL §8• §f" + pretty(material));

        inv.setItem(13, icon(material, "§a§l" + pretty(material), Arrays.asList(
                "§7You own: §f" + fmt(owned),
                "§7Price: §a" + plugin.getEconomyManager().format(worth.worth()) + " §7/ item",
                "",
                "§eSelect an amount, then confirm."
        )));
        inv.setItem(10, quantityButton(material, 1, owned));
        inv.setItem(11, quantityButton(material, 16, owned));
        inv.setItem(12, quantityButton(material, 64, owned));
        inv.setItem(14, quantityButton(material, owned, owned));
        inv.setItem(16, confirmButton(material, 1));
        inv.setItem(18, icon(Material.ARROW, "§e§l⬅ BACK", Collections.singletonList("§7Choose another item")));
        inv.setItem(22, icon(Material.BARRIER, "§c§l✕ CANCEL", Collections.singletonList("§7No items will be sold")));

        player.openInventory(inv);
    }

    public boolean isOpen(Player player, Inventory inventory) {
        return player.getOpenInventory().getTopInventory() == inventory
                && player.getOpenInventory().getTitle().contains("SELL");
    }

    public void handleClick(Player player, int slot) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        String title = player.getOpenInventory().getTitle();

        if (title.contains("SELECT ITEM")) {
            if (slot == BACK_SLOT) {
                plugin.getShopManager().openMain(player);
                return;
            }
            if (slot == CLOSE_SLOT) {
                player.closeInventory();
                return;
            }

            Session session = sessions.get(player.getUniqueId());
            if (session == null || slot < 0 || slot >= ITEM_SLOTS) return;

            List<Material> materials = getSellableMaterials(player);
            if (slot < materials.size()) openAmount(player, materials.get(slot));
            return;
        }

        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.material == null) {
            open(player);
            return;
        }

        if (slot == 18) {
            open(player);
            return;
        }
        if (slot == 22) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        int owned = count(player, session.material);
        int selected = 0;
        if (slot == 10) selected = Math.min(1, owned);
        if (slot == 11) selected = Math.min(16, owned);
        if (slot == 12) selected = Math.min(64, owned);
        if (slot == 14) selected = owned;

        if (selected > 0) {
            session.quantity = selected;
            inv.setItem(16, confirmButton(session.material, selected));
            return;
        }

        if (slot == 16) confirm(player);
    }

    private void confirm(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.material == null || session.quantity <= 0) return;

        Material material = session.material;
        int quantity = session.quantity;
        if (count(player, material) < quantity) {
            player.sendMessage("§cYou no longer have enough " + pretty(material) + " to complete that sale.");
            open(player);
            return;
        }

        long total = plugin.getWorthManager().sellValue(material, quantity);
        if (total <= 0) {
            player.sendMessage("§cThat item cannot currently be sold.");
            open(player);
            return;
        }

        if (!remove(player, material, quantity)) {
            player.sendMessage("§cThe sale could not be prepared. Nothing was sold.");
            return;
        }

        if (!plugin.getEconomyManager().deposit(player.getUniqueId(), total)) {
            player.getInventory().addItem(new ItemStack(material, quantity)).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            player.sendMessage("§cThe economy rejected the sale. Your items were returned.");
            return;
        }

        player.sendMessage("§a💰 Sold §f" + fmt(quantity) + "x " + pretty(material)
                + " §afor §f" + plugin.getEconomyManager().format(total) + "§a.");
        sessions.remove(player.getUniqueId());
        open(player);
    }

    private boolean remove(Player player, Material material, int quantity) {
        int remaining = quantity;
        for (int slot = 0; slot < player.getInventory().getStorageContents().length && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.getType() != material) continue;

            int take = Math.min(remaining, stack.getAmount());
            int newAmount = stack.getAmount() - take;
            if (newAmount <= 0) player.getInventory().setItem(slot, null);
            else {
                ItemStack updated = stack.clone();
                updated.setAmount(newAmount);
                player.getInventory().setItem(slot, updated);
            }
            remaining -= take;
        }
        return remaining == 0;
    }

    public void close(Player player) {
        sessions.remove(player.getUniqueId());
    }

    private List<Material> getSellableMaterials(Player player) {
        LinkedHashSet<Material> result = new LinkedHashSet<>();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            WorthEntry worth = plugin.getWorthManager().get(stack.getType());
            if (worth != null && worth.enabled() && worth.worth() > 0) result.add(stack.getType());
        }
        return new ArrayList<>(result);
    }

    private int count(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) total += stack.getAmount();
        }
        return total;
    }

    private ItemStack quantityButton(Material material, int amount, int owned) {
        int actual = Math.min(amount, owned);
        long value = plugin.getWorthManager().sellValue(material, actual);
        return icon(Material.PAPER, "§e§lSELL ×" + fmt(actual), Arrays.asList(
                "§7Amount: §f" + fmt(actual),
                "§7Receive: §a" + plugin.getEconomyManager().format(value),
                "§eClick to select"
        ));
    }

    private ItemStack confirmButton(Material material, int amount) {
        long value = plugin.getWorthManager().sellValue(material, amount);
        return icon(Material.EMERALD_BLOCK, "§a§l✓ CONFIRM SALE", Arrays.asList(
                "§7Selling: §f" + fmt(amount) + "x " + pretty(material),
                "§7Receive: §a" + plugin.getEconomyManager().format(value),
                "",
                "§eClick to complete the sale"
        ));
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String pretty(Material material) {
        String raw = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return out.toString().trim();
    }

    private String fmt(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static final class Session {
        private final Material material;
        private int quantity;

        private Session(Material material, int quantity) {
            this.material = material;
            this.quantity = quantity;
        }
    }
}
