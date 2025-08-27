package org.aincraft.listener;

import org.aincraft.ingame_items.FishDexItem;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class FishDexListener implements Listener {

    private final Plugin plugin;
    private final FishDexItem fishDexItem;

    public FishDexListener(Plugin plugin, FishDexItem fishDexItem) {
        this.plugin = plugin;
        this.fishDexItem = fishDexItem;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        // Check the actual hand this event is for (can be HAND or OFF_HAND)
        EquipmentSlot hand = e.getHand();
        if (hand == null) return;

        ItemStack item = (hand == EquipmentSlot.HAND)
                ? e.getPlayer().getInventory().getItemInMainHand()
                : e.getPlayer().getInventory().getItemInOffHand();

        if (item == null || item.getType().isAir()) return;
        if (!fishDexItem.isFishDex(item)) return; // our PDC tag?

        // Prevents placing/using the item; also covers interactable blocks.
        e.setCancelled(true);

        // Minor cooldown to prevent spam if player holds RMB
        e.getPlayer().setCooldown(item.getType(), 5);

        // If you right-clicked a block like a button/door, some plugins/vanilla can mark it allowed again.
        // These calls make it crystal clear we consumed the interaction:
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        // Run as player (respects their perms and context)
        // (player.performCommand is a tiny bit cleaner than dispatchCommand here)
        e.getPlayer().performCommand("fishdex");
    }
}