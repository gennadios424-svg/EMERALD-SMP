package net.emeraldsmp.invest;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class InvestmentManager implements Listener {
    public static final long MAX_INVESTMENT = 125_000_000L;
    private static final long RATE_DIVISOR = 1_000_000L;
    private static final long NUMERATOR_DIVISOR = 1_000_000_000L;
    private static final String TITLE = "§2§l💚 EMERALD INVESTMENTS";
    private final EmeraldSMP plugin;
    private final Map<UUID, Long> lastAccrual = new HashMap<>();
    private final Map<UUID, Long> fractionalNumerator = new HashMap<>();
    private final Set<UUID> transactions = new HashSet<>();
    private final Map<UUID, Long> depositWaiting = new HashMap<>();
    private BukkitTask task;

    public InvestmentManager(EmeraldSMP plugin) { this.plugin = plugin; }

    public void start() {
        stop();
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) lastAccrual.put(p.getUniqueId(), now);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) accrue(p);
        lastAccrual.clear();
        fractionalNumerator.clear();
        transactions.clear();
        depositWaiting.clear();
    }

    public void open(Player p) {
        accrue(p);
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        draw(inv, p);
        p.openInventory(inv);
    }

    private void draw(Inventory inv, Player p) {
        fill(inv);
        PlayerData d = data(p);
        long investment = d.getInvestment();
        long earnings = d.getInvestmentEarnings();

        inv.setItem(13, icon(Material.EMERALD_BLOCK, "§a§l💚 INVESTED", List.of(
                "§7Invested: §f" + money(investment),
                "§7Maximum: §f" + money(MAX_INVESTMENT),
                "§7Earnings: §a" + rateText(investment) + "§7 / second"
        )));
        inv.setItem(22, icon(Material.GOLD_INGOT, "§6§l⚡ EARNINGS", List.of(
                "§7Available: §f" + money(earnings),
                "§8Generated from your active investment",
                "§8Withdraw whenever you want"
        )));
        inv.setItem(31, icon(Material.EMERALD, "§a§l💰 DEPOSIT", List.of(
                "§7Enter any investment amount in chat",
                "§7Maximum total: §f$125,000,000",
                "§eClick to enter amount"
        )));
        inv.setItem(40, icon(d.isInvestmentAutoCollect() ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                d.isInvestmentAutoCollect() ? "§a§l⚙ AUTO COLLECT: ON" : "§c§l⚙ AUTO COLLECT: OFF",
                List.of(
                    d.isInvestmentAutoCollect() ? "§7Earnings are paid directly to your balance" : "§7Earnings stay here until withdrawn",
                    "§eClick to toggle"
                )));
        inv.setItem(42, icon(Material.GOLD_BLOCK, "§e§l💵 WITHDRAW", List.of(
                "§7Withdraw accumulated earnings",
                "§7Available: §f" + money(earnings),
                "§8Original investment remains invested"
        )));
        inv.setItem(44, icon(Material.BOOK, "§b§l📊 STATUS", List.of(
                "§7Investment: §f" + money(investment),
                "§7Earnings: §f" + money(earnings),
                "§7Rate: §a" + rateText(investment) + "§7 / second",
                "§7Auto collect: " + (d.isInvestmentAutoCollect() ? "§aON" : "§cOFF")
        )));
        inv.setItem(53, icon(Material.BARRIER, "§c§l✕ CLOSE", List.of()));
    }

    public boolean invest(Player p, long amount) {
        if (amount <= 0 || amount > MAX_INVESTMENT) {
            p.sendMessage(ChatColor.RED + "Investment amount must be between $1 and $125,000,000.");
            return false;
        }
        UUID u = p.getUniqueId();
        if (!transactions.add(u)) { p.sendMessage(ChatColor.YELLOW + "⏳ Investment transaction already processing."); return false; }
        try {
            accrue(p);
            PlayerData d = data(p);
            long old = d.getInvestment();
            if (old > MAX_INVESTMENT - amount) {
                p.sendMessage(ChatColor.RED + "Your total investment cannot exceed $125,000,000.");
                return false;
            }
            if (plugin.getEconomyManager().getBalance(u) < amount) {
                p.sendMessage(ChatColor.RED + "You do not have enough money to invest that amount.");
                return false;
            }
            if (!plugin.getEconomyManager().withdraw(u, amount)) {
                p.sendMessage(ChatColor.RED + "Investment failed safely; no money was changed.");
                return false;
            }
            d.setInvestment(old + amount);
            if (!plugin.getPlayerDataManager().save(d)) {
                d.setInvestment(old);
                plugin.getEconomyManager().deposit(u, amount);
                p.sendMessage(ChatColor.RED + "Investment save failed; your money was restored.");
                return false;
            }
            p.sendMessage(ChatColor.GREEN + "💚 Invested " + money(amount) + ". Total investment: " + money(d.getInvestment()) + ".");
            return true;
        } finally { transactions.remove(u); }
    }

    public boolean withdraw(Player p) {
        UUID u = p.getUniqueId();
        if (!transactions.add(u)) { p.sendMessage(ChatColor.YELLOW + "⏳ Investment transaction already processing."); return false; }
        try {
            accrue(p);
            PlayerData d = data(p);
            long amount = d.getInvestmentEarnings();
            if (amount <= 0) { p.sendMessage(ChatColor.RED + "You have no investment earnings to withdraw."); return false; }
            d.setInvestmentEarnings(0L);
            if (!plugin.getPlayerDataManager().save(d)) {
                d.setInvestmentEarnings(amount);
                p.sendMessage(ChatColor.RED + "Withdrawal failed safely; earnings were preserved.");
                return false;
            }
            if (!plugin.getEconomyManager().deposit(u, amount)) {
                d.setInvestmentEarnings(amount);
                plugin.getPlayerDataManager().save(d);
                p.sendMessage(ChatColor.RED + "Withdrawal failed safely; earnings were preserved.");
                return false;
            }
            p.sendMessage(ChatColor.GREEN + "💵 Withdrawn " + money(amount) + " investment earnings.");
            return true;
        } finally { transactions.remove(u); }
    }

    public void beginDeposit(Player p) {
        UUID u = p.getUniqueId();
        if (transactions.contains(u)) {
            p.sendMessage("§e⏳ Investment transaction already processing.");
            return;
        }
        depositWaiting.put(u, System.currentTimeMillis() + 60_000L);
        p.closeInventory();
        p.sendTitle("§a§l💚 INVESTMENT DEPOSIT", "§f§lHOW MUCH?", 5, 40, 10);
        p.sendMessage("§a§l💚 INVESTMENT DEPOSIT");
        p.sendMessage("§fHOW MUCH?");
        p.sendMessage("§7Type the amount in chat.");
        p.sendMessage("§7Maximum: §f$125,000,000");
        p.sendMessage("§7Type §ccancel §7to cancel.");
    }

    public void toggleAutoCollect(Player p) {
        UUID u = p.getUniqueId();
        if (!transactions.add(u)) {
            p.sendMessage("§e⏳ Investment transaction already processing.");
            return;
        }
        try {
            accrue(p);
            PlayerData d = data(p);
            boolean next = !d.isInvestmentAutoCollect();
            d.setInvestmentAutoCollect(next);
            if (!plugin.getPlayerDataManager().save(d)) {
                d.setInvestmentAutoCollect(!next);
                p.sendMessage("§cCould not save auto collect setting.");
                return;
            }
            p.sendMessage(next ? "§a⚙ Auto Collect enabled." : "§c⚙ Auto Collect disabled.");
        } finally { transactions.remove(u); }
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) accrue(p);
    }

    private void accrue(Player p) {
        UUID u = p.getUniqueId();
        PlayerData d = data(p);
        long investment = d.getInvestment();
        long now = System.currentTimeMillis();
        long last = lastAccrual.getOrDefault(u, now);
        lastAccrual.put(u, now);
        if (investment <= 0 || now <= last) return;

        long elapsed = Math.min(now - last, 86_400_000L);
        long numerator;
        try {
            numerator = Math.addExact(fractionalNumerator.getOrDefault(u, 0L), Math.multiplyExact(investment, elapsed));
        } catch (ArithmeticException ex) {
            plugin.getLogger().warning("Investment accrual overflow prevented for " + p.getName());
            return;
        }
        long earned = numerator / NUMERATOR_DIVISOR;
        fractionalNumerator.put(u, numerator % NUMERATOR_DIVISOR);
        if (earned <= 0) return;

        long old = d.getInvestmentEarnings();
        if (d.isInvestmentAutoCollect()) {
            if (plugin.getEconomyManager().deposit(u, earned)) {
                plugin.getPlayerDataManager().save(d);
                return;
            }
        }
        if (Long.MAX_VALUE - old < earned) {
            plugin.getLogger().warning("Investment earnings overflow prevented for " + p.getName());
            return;
        }
        d.setInvestmentEarnings(old + earned);
        plugin.getPlayerDataManager().save(d);
    }

    private PlayerData data(Player p) {
        PlayerData d = plugin.getPlayerDataManager().getLoaded(p.getUniqueId());
        return d == null ? plugin.getPlayerDataManager().loadOrCreate(p) : d;
    }

    private String rateText(long investment) {
        double rate = investment / (double) RATE_DIVISOR;
        if (rate == Math.rint(rate)) return String.format(Locale.US, "%.0f", rate);
        return String.format(Locale.US, "%.2f", rate);
    }

    private String money(long amount) { return plugin.getEconomyManager().format(amount); }

    private void fill(Inventory inv) {
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (e.getRawSlot() < 0 || e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;
        switch (e.getRawSlot()) {
            case 31 -> beginDeposit(p);
            case 40 -> toggleAutoCollect(p);
            case 42 -> withdraw(p);
            case 53 -> { p.closeInventory(); return; }
            default -> { return; }
        }
        if (p.isOnline() && p.getOpenInventory().getTopInventory() == e.getView().getTopInventory()) draw(e.getView().getTopInventory(), p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e) {
        if (TITLE.equals(e.getView().getTitle())) e.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (TITLE.equals(e.getView().getTitle())) lastAccrual.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void chat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        Long expires = depositWaiting.get(u);
        if (expires == null) return;
        e.setCancelled(true);
        String message = e.getMessage().trim();
        if (message.equalsIgnoreCase("cancel")) {
            depositWaiting.remove(u);
            p.sendMessage("§e💚 Investment deposit cancelled.");
            Bukkit.getScheduler().runTask(plugin, () -> open(p));
            return;
        }
        if (expires <= System.currentTimeMillis()) {
            depositWaiting.remove(u);
            p.sendMessage("§e💚 Investment deposit timed out. Nothing was charged.");
            return;
        }
        long amount = plugin.getEconomyManager().parseAmount(message);
        if (amount <= 0 || amount > MAX_INVESTMENT) {
            p.sendMessage("§cEnter a positive whole number from $1 to $125,000,000.");
            return;
        }
        depositWaiting.remove(u);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (invest(p, amount)) open(p);
            else open(p);
        });
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        lastAccrual.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        fractionalNumerator.remove(e.getPlayer().getUniqueId());
        depositWaiting.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        accrue(e.getPlayer());
        lastAccrual.remove(u);
        fractionalNumerator.remove(u);
        transactions.remove(u);
        depositWaiting.remove(u);
    }
}
