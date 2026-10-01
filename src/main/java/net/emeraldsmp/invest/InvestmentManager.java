package net.emeraldsmp.invest;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
    private static final String TITLE = "§2§l💚 EMERALD INVESTMENTS";
    private final EmeraldSMP plugin;
    private final Map<UUID, Long> lastAccrual = new HashMap<>();
    private final Set<UUID> transactions = new HashSet<>();
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
        transactions.clear();
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
        inv.setItem(13, icon(Material.EMERALD_BLOCK, "§a§l💚 YOUR INVESTMENT", List.of(
                "§7Invested: §f" + money(investment),
                "§7Maximum: §f" + money(MAX_INVESTMENT),
                "§7Rate: §a" + rateText(investment) + "§7 / second"
        )));
        inv.setItem(22, icon(Material.GOLD_INGOT, "§6§l📈 EARNINGS", List.of(
                "§7Accumulated: §f" + money(earnings),
                "§8Earnings are active while you are online",
                "§8Original investment is separate"
        )));
        inv.setItem(31, icon(Material.EMERALD, "§a§l💰 INVEST", List.of(
                "§7Choose an amount below",
                "§8Funds are deducted server-side",
                "§8Maximum investment: §f$125M"
        )));
        inv.setItem(40, icon(Material.BOOK, "§b§l📊 STATUS", List.of(
                "§7Investment: §f" + money(investment),
                "§7Earnings: §f" + money(earnings),
                "§7Rate: §a" + rateText(investment) + "§7 / second"
        )));
        inv.setItem(42, icon(Material.GOLD_BLOCK, "§e§l💵 WITHDRAW", List.of(
                "§7Withdraw accumulated earnings only",
                "§7Available: §f" + money(earnings),
                "§8Your original investment stays invested"
        )));
        inv.setItem(45, icon(Material.EMERALD, "§a§l+$1M", List.of("§7Invest $1,000,000")));
        inv.setItem(47, icon(Material.EMERALD, "§a§l+$10M", List.of("§7Invest $10,000,000")));
        inv.setItem(49, icon(Material.EMERALD_BLOCK, "§a§l+$25M", List.of("§7Invest $25,000,000")));
        inv.setItem(51, icon(Material.DIAMOND, "§b§l+$100M", List.of("§7Invest $100,000,000")));
        inv.setItem(53, icon(Material.BARRIER, "§c§l✕ CLOSE", List.of()));
    }

    public boolean invest(Player p, long amount) {
        if (amount <= 0 || amount > MAX_INVESTMENT) return false;
        UUID u = p.getUniqueId();
        if (!transactions.add(u)) return false;
        try {
            accrue(p);
            PlayerData d = data(p);
            long old = d.getInvestment();
            if (old > MAX_INVESTMENT - amount) return false;
            if (plugin.getEconomyManager().getBalance(u) < amount) return false;
            if (!plugin.getEconomyManager().withdraw(u, amount)) return false;
            d.setInvestment(old + amount);
            if (!plugin.getPlayerDataManager().save(d)) {
                d.setInvestment(old);
                plugin.getEconomyManager().deposit(u, amount);
                return false;
            }
            p.sendMessage(ChatColor.GREEN + "💚 Invested " + money(amount) + ". Total investment: " + money(d.getInvestment()) + ".");
            return true;
        } finally { transactions.remove(u); }
    }

    public boolean withdraw(Player p) {
        UUID u = p.getUniqueId();
        if (!transactions.add(u)) return false;
        try {
            accrue(p);
            PlayerData d = data(p);
            long amount = d.getInvestmentEarnings();
            if (amount <= 0) { p.sendMessage(ChatColor.RED + "You have no investment earnings to withdraw."); return false; }
            d.setInvestmentEarnings(0L);
            if (!plugin.getPlayerDataManager().save(d)) { d.setInvestmentEarnings(amount); return false; }
            if (!plugin.getEconomyManager().deposit(u, amount)) {
                d.setInvestmentEarnings(amount);
                plugin.getPlayerDataManager().save(d);
                return false;
            }
            p.sendMessage(ChatColor.GREEN + "💵 Withdrawn " + money(amount) + " investment earnings.");
            return true;
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
        try { numerator = Math.multiplyExact(investment, elapsed); }
        catch (ArithmeticException ex) { return; }
        long earned = numerator / (RATE_DIVISOR * 1000L);
        if (earned <= 0) return;
        long old = d.getInvestmentEarnings();
        if (Long.MAX_VALUE - old < earned) return;
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
            case 45 -> invest(p, 1_000_000L);
            case 47 -> invest(p, 10_000_000L);
            case 49 -> invest(p, 25_000_000L);
            case 51 -> invest(p, 100_000_000L);
            case 42 -> withdraw(p);
            case 53 -> p.closeInventory();
            default -> { return; }
        }
        if (p.isOnline() && !p.getOpenInventory().getTopInventory().equals(e.getView().getTopInventory())) return;
        draw(e.getView().getTopInventory(), p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e) {
        if (TITLE.equals(e.getView().getTitle())) e.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (e.getView().getTitle().equals(TITLE)) lastAccrual.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void join(PlayerJoinEvent e) { lastAccrual.put(e.getPlayer().getUniqueId(), System.currentTimeMillis()); }

    @EventHandler
    public void quit(PlayerQuitEvent e) { accrue(e.getPlayer()); lastAccrual.remove(e.getPlayer().getUniqueId()); transactions.remove(e.getPlayer().getUniqueId()); }
}
