package org.aincraft.listener;

import org.aincraft.ingame_items.TackleBoxItem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class TackleBoxListener implements Listener {

    private final TackleBoxItem tackleBoxItem;
    private final TackleBoxService service;

    public TackleBoxListener(TackleBoxItem tackleBoxItem, TackleBoxService service) {
        this.tackleBoxItem = tackleBoxItem;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        EquipmentSlot hand = e.getHand();
        if (hand == null) return;

        ItemStack item = (hand == EquipmentSlot.HAND)
                ? e.getPlayer().getInventory().getItemInMainHand()
                : e.getPlayer().getInventory().getItemInOffHand();

        if (item == null || item.getType().isAir()) return;
        if (!tackleBoxItem.isTackleBox(item)) return;

        // Prevent normal use (placing/interacting) and spam
        e.setCancelled(true);
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        e.getPlayer().setCooldown(item.getType(), 5);

        // Run your command as the player
        service.openFromHand(e.getPlayer(), e.getHand());
    }
}

