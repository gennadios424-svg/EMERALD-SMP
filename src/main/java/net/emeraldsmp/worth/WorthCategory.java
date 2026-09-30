package net.emeraldsmp.worth;

public enum WorthCategory {
    BLOCKS("🧱 Blocks"),
    RESOURCES("💎 Ores & Resources"),
    REDSTONE("🔴 Redstone"),
    COMBAT("⚔ Combat"),
    TOOLS("🛠 Tools"),
    FOOD("🍖 Food"),
    FARMING("🌾 Farming"),
    MOB_DROPS("🐄 Mob Drops"),
    BREWING("🧪 Brewing"),
    NETHER("🔥 Nether"),
    END("🌌 End"),
    BUILDING("🏗 Building"),
    MISC("📦 Miscellaneous");

    private final String displayName;
    WorthCategory(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
