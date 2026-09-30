package net.emeraldsmp.worth;

public enum WorthCategoryFilter {
    ALL("All Items", null),
    BLOCKS("🧱 Blocks", WorthCategory.BLOCKS),
    RESOURCES("💎 Ores & Resources", WorthCategory.RESOURCES),
    REDSTONE("🔴 Redstone", WorthCategory.REDSTONE),
    COMBAT("⚔ Combat", WorthCategory.COMBAT),
    TOOLS("🛠 Tools", WorthCategory.TOOLS),
    FOOD("🍖 Food", WorthCategory.FOOD),
    FARMING("🌾 Farming", WorthCategory.FARMING),
    MOB_DROPS("🐄 Mob Drops", WorthCategory.MOB_DROPS),
    BREWING("🧪 Brewing", WorthCategory.BREWING),
    NETHER("🔥 Nether", WorthCategory.NETHER),
    END("🌌 End", WorthCategory.END),
    BUILDING("🏗 Building", WorthCategory.BUILDING),
    MISC("📦 Miscellaneous", WorthCategory.MISC);

    private final String displayName; private final WorthCategory category;
    WorthCategoryFilter(String displayName, WorthCategory category){this.displayName=displayName;this.category=category;}
    public String displayName(){return displayName;} public WorthCategory category(){return category;}
}
