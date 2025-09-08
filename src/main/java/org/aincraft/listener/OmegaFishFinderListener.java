// org/aincraft/listener/OmegaFishFinderListener.java
package org.aincraft.listener;

import org.aincraft.ingame_items.OmegaFishFinderItem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class OmegaFishFinderListener implements Listener {
    private final OmegaFishFinderItem item;

    public OmegaFishFinderListener(OmegaFishFinderItem item) {
        this.item = item;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        // Only care about the hand that triggered the event
        if (e.getHand() != EquipmentSlot.HAND && e.getHand() != EquipmentSlot.OFF_HAND) return;
        var stack = (e.getHand() == EquipmentSlot.OFF_HAND)
                ? e.getPlayer().getInventory().getItemInOffHand()
                : e.getPlayer().getInventory().getItemInMainHand();

        if (!item.isOmega(stack)) return;

        e.getPlayer().sendMessage("§7Hold this in your §eoffhand§7 for detailed info.");
    }
}

