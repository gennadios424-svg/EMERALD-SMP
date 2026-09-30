package net.emeraldsmp.worth;

import org.bukkit.Material;

public record WorthEntry(Material material, long worth, WorthCategory category, boolean enabled) {
    public long stackWorth() {
        return Math.multiplyExact(worth, material.getMaxStackSize());
    }
}
