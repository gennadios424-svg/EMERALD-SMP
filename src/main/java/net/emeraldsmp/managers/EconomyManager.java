package net.emeraldsmp.managers;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.data.PlayerData;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.UUID;

public final class EconomyManager {
    private final EmeraldSMP plugin;
    public EconomyManager(EmeraldSMP plugin) { this.plugin = plugin; }

    public synchronized long getBalance(UUID uuid) {
        PlayerData data = plugin.getPlayerDataManager().getLoaded(uuid);
        return data == null ? 0L : data.getBalance();
    }

    public synchronized boolean deposit(UUID uuid, long amount) {
        if (amount <= 0) return false;
        PlayerData data = requireLoaded(uuid);
        return depositData(data, amount);
    }

    public synchronized boolean depositToUuid(UUID uuid, long amount, String username) {
        if (amount <= 0) return false;
        PlayerData data = plugin.getPlayerDataManager().loadOrCreate(uuid, username);
        return depositData(data, amount);
    }

    private boolean depositData(PlayerData data, long amount) {
        long old = data.getBalance();
        if (Long.MAX_VALUE - old < amount) return false;
        data.setBalance(old + amount);
        if (plugin.getPlayerDataManager().save(data)) return true;
        data.setBalance(old);
        return false;
    }

    public synchronized boolean withdraw(UUID uuid, long amount) {
        if (amount <= 0) return false;
        PlayerData data = requireLoaded(uuid);
        long old = data.getBalance();
        if (old < amount) return false;
        data.setBalance(old - amount);
        if (plugin.getPlayerDataManager().save(data)) return true;
        data.setBalance(old);
        return false;
    }

    public synchronized boolean setBalance(UUID uuid, long amount) {
        if (amount < 0) return false;
        PlayerData data = requireLoaded(uuid);
        long old = data.getBalance();
        data.setBalance(amount);
        if (plugin.getPlayerDataManager().save(data)) return true;
        data.setBalance(old);
        return false;
    }

    public synchronized boolean reset(UUID uuid) {
        return setBalance(uuid, plugin.getConfigManager().getConfig().getLong("economy.starting-balance", 0L));
    }

    public synchronized boolean transfer(Player sender, Player receiver, long amount) {
        if (amount <= 0 || sender.getUniqueId().equals(receiver.getUniqueId())) return false;
        PlayerData from = requireLoaded(sender.getUniqueId());
        PlayerData to = requireLoaded(receiver.getUniqueId());
        long oldFrom = from.getBalance(), oldTo = to.getBalance();
        if (oldFrom < amount || Long.MAX_VALUE - oldTo < amount) return false;
        from.setBalance(oldFrom - amount); to.setBalance(oldTo + amount);
        if (plugin.getPlayerDataManager().saveBoth(from, to)) return true;
        from.setBalance(oldFrom); to.setBalance(oldTo); return false;
    }

    public long parseAmount(String input) {
        try {
            BigDecimal value = new BigDecimal(input);
            if (value.signum() <= 0 || value.scale() > 0) return -1L;
            return value.longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) { return -1L; }
    }

    private PlayerData requireLoaded(UUID uuid) {
        PlayerData data = plugin.getPlayerDataManager().getLoaded(uuid);
        if (data == null) throw new IllegalStateException("Player data is not loaded for " + uuid);
        return data;
    }

    /**
     * Formats the actual money balance for display only.
     * The stored value remains a precise long and is never converted to shards.
     */
    public String format(long amount) {
        return plugin.getConfigManager().getConfig().getString("economy.currency-symbol", "$") + formatCompact(amount);
    }

    /** Visual-only compact money formatter: K, M, B, T. */
    public String formatCompact(long amount) {
        final String sign = amount < 0 ? "-" : "";
        double value = Math.abs((double) amount);
        String suffix = "";
        if (value >= 1_000_000_000_000D) { value /= 1_000_000_000_000D; suffix = "T"; }
        else if (value >= 1_000_000_000D) { value /= 1_000_000_000D; suffix = "B"; }
        else if (value >= 1_000_000D) { value /= 1_000_000D; suffix = "M"; }
        else if (value >= 1_000D) { value /= 1_000D; suffix = "K"; }

        if (suffix.isEmpty()) return sign + Long.toString(Math.abs(amount));
        if (value >= 100D) return sign + String.format(java.util.Locale.US, "%.0f%s", value, suffix);
        if (value >= 10D) return sign + String.format(java.util.Locale.US, "%.1f%s", value, suffix);
        return sign + String.format(java.util.Locale.US, "%.2f%s", value, suffix);
    }
}
