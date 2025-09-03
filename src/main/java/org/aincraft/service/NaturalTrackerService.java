package org.aincraft.service;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Non-persistent, per-chunk tracker for player-placed blocks we care about.
 * - Cap: 512 indices per chunk (new placements ignored after cap)
 * - Cleared automatically on chunk unload
 */
public final class NaturalTrackerService implements Listener {
    private static final int PER_CHUNK_CAP = 512;

    // Which materials we tag as "player placed" (so they’re NOT natural)
    private static final Set<Material> TRACKED = Set.of(
            Material.LILY_PAD,
            Material.FERN, Material.LARGE_FERN,
            Material.DEAD_BUSH,

            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
            Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
            Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES, Material.PALE_OAK_LEAVES,
            Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES
    );

    private final Plugin plugin;

    private static final class ChunkIndex {
        final BitSet bits = new BitSet();
        int count = 0;
        boolean add(int idx) {
            if (bits.get(idx)) return true;
            if (count >= PER_CHUNK_CAP) return false;
            bits.set(idx); count++; return true;
        }
        void remove(int idx) {
            if (bits.get(idx)) { bits.clear(idx); count--; }
        }
        boolean contains(int idx) { return bits.get(idx); }
        boolean isEmpty() { return count == 0; }
    }

    private final Map<Long, ChunkIndex> placed = new ConcurrentHashMap<>();

    public NaturalTrackerService(Plugin plugin) { this.plugin = plugin; }

    private static long chunkKey(int cx, int cz) { return (((long)cx) << 32) ^ (cz & 0xffffffffL); }

    private static int packIndex(Block b) {
        World w = b.getWorld();
        int minY = w.getMinHeight();
        int height = w.getMaxHeight() - minY;
        int lx = b.getX() & 15, lz = b.getZ() & 15, y = b.getY() - minY;
        return (((lz * height) + y) << 4) | lx; // [lz][y][lx]
    }

    private ChunkIndex getOrCreate(Chunk c) { return placed.computeIfAbsent(chunkKey(c.getX(), c.getZ()), k -> new ChunkIndex()); }
    private ChunkIndex get(Chunk c) { return placed.get(chunkKey(c.getX(), c.getZ())); }

    /** True if block is not marked placed in this session (= natural). */
    public boolean isNatural(Block b) {
        ChunkIndex idx = get(b.getChunk());
        if (idx == null) return true;
        return !idx.contains(packIndex(b));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (!TRACKED.contains(b.getType())) return;
        ChunkIndex idx = getOrCreate(b.getChunk());
        idx.add(packIndex(b));
        if (b.getType() == Material.LARGE_FERN) {
            Block up = b.getRelative(0, 1, 0);
            if (up.getType() == Material.LARGE_FERN) idx.add(packIndex(up));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e) {
        ChunkIndex idx = get(e.getBlock().getChunk());
        if (idx == null) return;
        idx.remove(packIndex(e.getBlock()));
        if (idx.isEmpty()) placed.remove(chunkKey(e.getBlock().getChunk().getX(), e.getBlock().getChunk().getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent e) {
        placed.remove(chunkKey(e.getChunk().getX(), e.getChunk().getZ()));
    }
}

