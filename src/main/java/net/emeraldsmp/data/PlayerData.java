package net.emeraldsmp.data;

import java.util.UUID;

public final class PlayerData {
    private final UUID uuid;
    private String username;
    private final long firstJoin;
    private long lastSeen;
    private long balance;

    public PlayerData(UUID uuid, String username, long firstJoin, long lastSeen, long balance) {
        this.uuid = uuid;
        this.username = username;
        this.firstJoin = firstJoin;
        this.lastSeen = lastSeen;
        this.balance = balance;
    }

    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public long getFirstJoin() { return firstJoin; }
    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
    public long getBalance() { return balance; }
    public void setBalance(long balance) { this.balance = balance; }
}