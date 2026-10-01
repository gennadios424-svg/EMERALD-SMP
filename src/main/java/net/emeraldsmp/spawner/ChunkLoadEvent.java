package net.emeraldsmp.spawner;

/**
 * Compatibility shim for the spawner hologram listener.
 * The actual Paper event is handled by the runtime listener registration;
 * this type only keeps legacy source compatibility with older imports.
 */
public final class ChunkLoadEvent {
    private ChunkLoadEvent() {}
}