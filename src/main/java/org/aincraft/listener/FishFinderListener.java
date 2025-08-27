package org.aincraft.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.container.FishTimeCycle;
import org.aincraft.ingame_items.FishFinderItem;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class FishFinderListener implements Listener {

    private final FishFinderItem item;

    public FishFinderListener(FishFinderItem item) {
        this.item = item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        EquipmentSlot hand = e.getHand();
        if (hand == null) return;

        ItemStack inHand = (hand == EquipmentSlot.HAND)
                ? e.getPlayer().getInventory().getItemInMainHand()
                : e.getPlayer().getInventory().getItemInOffHand();

        if (inHand == null || inHand.getType().isAir()) return;
        if (!item.isFishFinder(inHand)) return;

        // consume interaction
        e.setCancelled(true);
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        e.getPlayer().setCooldown(inHand.getType(), 5);

        World w = e.getPlayer().getWorld();

        // Weather string
        String weather = (!w.hasStorm())
                ? "Sunny"
                : (w.isThundering() ? "Stormy" : "Rainy");

        // Time -> hh:mmAM/PM (0 ticks = 06:00)
        int t = (int) (w.getTime() % 24000L);
        int totalMinutes = ((t + 6000) % 24000) * 60 / 1000; // 0..1439
        int hour24 = totalMinutes / 60;
        int minute = totalMinutes % 60;
        int hour12 = hour24 % 12; if (hour12 == 0) hour12 = 12;
        String ampm = (hour24 < 12) ? "AM" : "PM";
        String timeStr = String.format("%02d:%02d%s", hour12, minute, ampm);

        // Time enum via your FishTimeCycle
        FishTimeCycle cycle = resolveCycle(t);
        String cycleName = cycle.getFrontTimeName(); // Morning / Noon / Evening / Midnight

        // Moon phase
        int phase = w.getMoonPhase().ordinal(); // 0..7
        String phaseName = switch (phase) {
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

        // Single-line action bar as requested
        String msg = String.format(
                "It's a %s day at %s %s. A %s is coming.",
                weather, timeStr, cycleName, phaseName
        );

        e.getPlayer().sendActionBar(Component.text(msg, NamedTextColor.AQUA));
    }

    private static FishTimeCycle resolveCycle(int tick) {
        for (FishTimeCycle c : FishTimeCycle.values()) {
            int start = (int) c.getStartTime();
            int end   = (int) c.getEndTime();
            if (inRangeWrap(tick, start, end)) return c;
        }
        return FishTimeCycle.DAY;
    }

    // Inclusive start, exclusive end; supports wraparound ranges like 22000..1000
    private static boolean inRangeWrap(int tick, int start, int end) {
        if (start < end) return tick >= start && tick < end;
        return tick >= start || tick < end;
    }
}

