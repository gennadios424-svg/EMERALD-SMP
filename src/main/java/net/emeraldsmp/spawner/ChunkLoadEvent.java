package net.emeraldsmp.spawner;

import org.bukkit.Chunk;

/** Compatibility subtype used by the legacy hologram listener signature. */
public final class ChunkLoadEvent extends org.bukkit.event.world.ChunkLoadEvent {
    public ChunkLoadEvent(Chunk chunk, boolean newChunk) {
        super(chunk, newChunk);
    }
}