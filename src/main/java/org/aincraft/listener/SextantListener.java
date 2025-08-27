package org.aincraft.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.ingame_items.SextantItem;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class SextantListener implements Listener {

    private final SextantItem sextantItem;

    public SextantListener(SextantItem sextantItem) {
        this.sextantItem = sextantItem;
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
        if (!sextantItem.isSextant(item)) return;

        e.setCancelled(true);
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        e.getPlayer().setCooldown(item.getType(), 5);

        World w = e.getPlayer().getWorld();
        int phase = w.getMoonPhase().ordinal(); // 0..7
        String phaseName = switch (phase) {
            // Minecraft phases (0 = Full, 4 = New)
            case 0 -> "Full Moon";
            case 1 -> "Waning Gibbous";
            case 2 -> "Last Quarter";
            case 3 -> "Waning Crescent";
            case 4 -> "New Moon";
            case 5 -> "Waxing Crescent";
            case 6 -> "First Quarter";
            case 7 -> "Waxing Gibbous";
            default -> "Unknown";
        };

        e.getPlayer().sendActionBar(Component.text(
                "Current Moon Phase: " + phaseName, NamedTextColor.AQUA));
    }
}

