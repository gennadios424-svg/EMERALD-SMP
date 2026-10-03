package net.emeraldsmp.managers;

import net.emeraldsmp.EmeraldSMP;
import net.emeraldsmp.data.PlayerData;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

public final class EconomyManager {
    private final EmeraldSMP plugin;
    public EconomyManager(EmeraldSMP plugin) { this.plugin = plugin; }

    public synchronized long getBalance(UUID uuid) {
        PlayerData data = plugin.getPlayerDataManager().getLoaded(uuid);
        if (data != null) return data.getBalance();
        return plugin.getPlayerDataManager().loadOrCreate(uuid, null).getBalance();
    }

    public synchronized boolean deposit(UUID uuid, long amount) {
        if (amount <= 0) return false;
        PlayerData data = requireData(uuid, null);
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
        PlayerData data = requireData(uuid, null);
        long old = data.getBalance();
        if (old < amount) return false;
        data.setBalance(old - amount);
        if (plugin.getPlayerDataManager().save(data)) return true;
        data.setBalance(old);
        return false;
    }

    public synchronized boolean setBalance(UUID uuid, long amount) {
        if (amount < 0) return false;
        PlayerData data = requireData(uuid, null);
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
        PlayerData from = requireData(sender.getUniqueId(), sender.getName());
        PlayerData to = requireData(receiver.getUniqueId(), receiver.getName());
        long oldFrom = from.getBalance(), oldTo = to.getBalance();
        if (oldFrom < amount || Long.MAX_VALUE - oldTo < amount) return false;
        from.setBalance(oldFrom - amount); to.setBalance(oldTo + amount);
        if (plugin.getPlayerDataManager().saveBoth(from, to)) return true;
        from.setBalance(oldFrom); to.setBalance(oldTo); return false;
    }

    /** Parse exact whole-money input, including compact K/M/B suffixes. */
    public long parseAmount(String input) {
        if (input == null) return -1L;
        String raw = input.trim().toUpperCase(Locale.ROOT).replace(",", "");
        if (raw.isEmpty()) return -1L;
        BigDecimal multiplier = BigDecimal.ONE;
        if (raw.endsWith("K")) { multiplier = BigDecimal.valueOf(1_000L); raw = raw.substring(0, raw.length() - 1); }
        else if (raw.endsWith("M")) { multiplier = BigDecimal.valueOf(1_000_000L); raw = raw.substring(0, raw.length() - 1); }
        else if (raw.endsWith("B")) { multiplier = BigDecimal.valueOf(1_000_000_000L); raw = raw.substring(0, raw.length() - 1); }
        try {
            BigDecimal value = new BigDecimal(raw).multiply(multiplier);
            if (value.signum() <= 0 || value.scale() > 0) return -1L;
            return value.longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) { return -1L; }
    }

    private PlayerData requireData(UUID uuid, String username) {
        PlayerData data = plugin.getPlayerDataManager().getLoaded(uuid);
        return data != null ? data : plugin.getPlayerDataManager().loadOrCreate(uuid, username);
    }

    /** Player-facing money formatter. Stored economy values remain exact longs. */
    public String format(long amount) {
        return plugin.getConfigManager().getConfig().getString("economy.currency-symbol", "$") + formatCompact(amount);
    }

    /** Shared compact numeric formatter used by player-facing currency/quantity displays. */
    public String formatCompact(long amount) {
        final String sign = amount < 0 ? "-" : "";
        double value = Math.abs((double) amount);
        String suffix = "";
        if (value >= 1_000_000_000D) { value /= 1_000_000_000D; suffix = "B"; }
        else if (value >= 1_000_000D) { value /= 1_000_000D; suffix = "M"; }
        else if (value >= 1_000D) { value /= 1_000D; suffix = "K"; }
        if (suffix.isEmpty()) return sign + Long.toString(Math.abs(amount));
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return sign + rounded.toPlainString() + suffix;
    }
}
