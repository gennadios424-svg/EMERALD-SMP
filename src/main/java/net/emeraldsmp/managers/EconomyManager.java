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
        if (Long.MAX_VALUE - data.getBalance() < amount) return false;
        data.setBalance(data.getBalance() + amount);
        return plugin.getPlayerDataManager().save(data);
    }

    public synchronized boolean withdraw(UUID uuid, long amount) {
        if (amount <= 0) return false;
        PlayerData data = requireLoaded(uuid);
        if (data.getBalance() < amount) return false;
        data.setBalance(data.getBalance() - amount);
        return plugin.getPlayerDataManager().save(data);
    }

    public synchronized boolean setBalance(UUID uuid, long amount) {
        if (amount < 0) return false;
        PlayerData data = requireLoaded(uuid);
        data.setBalance(amount);
        return plugin.getPlayerDataManager().save(data);
    }

    public synchronized boolean reset(UUID uuid) {
        return setBalance(uuid, plugin.getConfigManager().getConfig().getLong("economy.starting-balance", 0L));
    }

    public synchronized boolean transfer(Player sender, Player receiver, long amount) {
        if (amount <= 0 || sender.getUniqueId().equals(receiver.getUniqueId())) return false;
        PlayerData from = requireLoaded(sender.getUniqueId());
        PlayerData to = requireLoaded(receiver.getUniqueId());
        if (from.getBalance() < amount || Long.MAX_VALUE - to.getBalance() < amount) return false;

        long oldFrom = from.getBalance();
        long oldTo = to.getBalance();
        from.setBalance(oldFrom - amount);
        to.setBalance(oldTo + amount);

        if (plugin.getPlayerDataManager().saveBoth(from, to)) return true;
        from.setBalance(oldFrom);
        to.setBalance(oldTo);
        return false;
    }

    public long parseAmount(String input) {
        try {
            BigDecimal value = new BigDecimal(input);
            if (value.signum() <= 0 || value.scale() > 0) return -1L;
            return value.longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            return -1L;
        }
    }

    private PlayerData requireLoaded(UUID uuid) {
        PlayerData data = plugin.getPlayerDataManager().getLoaded(uuid);
        if (data == null) throw new IllegalStateException("Player data is not loaded for " + uuid);
        return data;
    }

    public String format(long amount) {
        String symbol = plugin.getConfigManager().getConfig().getString("economy.currency-symbol", "$");
        return symbol + String.format("%,d", amount);
    }
}