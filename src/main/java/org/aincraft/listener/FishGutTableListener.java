// org/aincraft/listener/FishGutTableListener.java
package org.aincraft.listener;

import org.aincraft.events.PacketBlockInteractEvent;
import org.aincraft.processor.FishProcessorUI;
import org.bukkit.block.Block;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public final class FishGutTableListener implements Listener {
    private static final String GUTTING_STATION_ID = "longhardfish:block/gutting_station";

    private final JavaPlugin plugin;
    private final FishProcessorUI ui;

    public FishGutTableListener(JavaPlugin plugin, FishProcessorUI ui) {
        this.plugin = plugin;
        this.ui = ui;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onUseFishGut(PacketBlockInteractEvent event) {
        if (!event.isRightClick()) return;
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        if (event.getPlayer().isSneaking()) return;
        if (!GUTTING_STATION_ID.equals(event.getResourceKey())) return;

        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin,
                () -> ui.openAt(event.getPlayer(), event.getBlock())); // use the actual block
    }

    // --- NEW: drop/purge table contents on break/explode ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        ui.onTableBroken(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Block b : e.blockList()) {
            ui.onTableBroken(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        for (Block b : e.blockList()) {
            ui.onTableBroken(b);
        }
    }
}