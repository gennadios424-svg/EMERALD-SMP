package net.emeraldsmp.data;

import java.util.UUID;

public final class PlayerData {
    private final UUID uuid;
    private String username;
    private final long firstJoin;
    private long lastSeen;
    private long balance;
    private long emeraldShards;
    private long investment;
    private long investmentEarnings;

    public PlayerData(UUID uuid, String username, long firstJoin, long lastSeen, long balance) {
        this(uuid, username, firstJoin, lastSeen, balance, 0L, 0L, 0L);
    }

    public PlayerData(UUID uuid, String username, long firstJoin, long lastSeen, long balance, long emeraldShards) {
        this(uuid, username, firstJoin, lastSeen, balance, emeraldShards, 0L, 0L);
    }

    public PlayerData(UUID uuid, String username, long firstJoin, long lastSeen, long balance, long emeraldShards, long investment, long investmentEarnings) {
        this.uuid = uuid;
        this.username = username;
        this.firstJoin = firstJoin;
        this.lastSeen = lastSeen;
        this.balance = Math.max(0L, balance);
        this.emeraldShards = Math.max(0L, emeraldShards);
        this.investment = Math.max(0L, investment);
        this.investmentEarnings = Math.max(0L, investmentEarnings);
    }

    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public long getFirstJoin() { return firstJoin; }
    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
    public long getBalance() { return balance; }
    public void setBalance(long balance) { this.balance = Math.max(0L, balance); }
    public long getEmeraldShards() { return emeraldShards; }
    public void setEmeraldShards(long emeraldShards) { this.emeraldShards = Math.max(0L, emeraldShards); }
    public long getInvestment() { return investment; }
    public void setInvestment(long investment) { this.investment = Math.max(0L, investment); }
    public long getInvestmentEarnings() { return investmentEarnings; }
    public void setInvestmentEarnings(long investmentEarnings) { this.investmentEarnings = Math.max(0L, investmentEarnings); }
}
