package org.aincraft.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.container.FishTimeCycle;
import org.aincraft.ingame_items.WatchItem;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class WatchListener implements Listener {

    private final WatchItem watchItem;

    public WatchListener(WatchItem watchItem) {
        this.watchItem = watchItem;
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
        if (!watchItem.isWatch(inHand)) return;

        // consume interaction
        e.setCancelled(true);
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        e.getPlayer().setCooldown(inHand.getType(), 5);

        World w = e.getPlayer().getWorld();
        int t = (int) (w.getTime() % 24000L);

        // Convert ticks → clock time (0 = 06:00)
        int totalMinutes = ((t + 6000) % 24000) * 60 / 1000;
        int hour24 = totalMinutes / 60;
        int minute = totalMinutes % 60;
        int hour12 = hour24 % 12; if (hour12 == 0) hour12 = 12;
        String ampm = (hour24 < 12) ? "AM" : "PM";
        String timeStr = String.format("%02d:%02d%s", hour12, minute, ampm);

        FishTimeCycle cycle = resolveCycle(t);

        e.getPlayer().sendActionBar(
                Component.text("Current Time: " + timeStr + " " + cycle.getFrontTimeName(), NamedTextColor.AQUA)
        );
    }

    private static FishTimeCycle resolveCycle(int tick) {
        for (FishTimeCycle c : FishTimeCycle.values()) {
            int start = (int) c.getStartTime();
            int end   = (int) c.getEndTime();
            if (inRangeWrap(tick, start, end)) return c;
        }
        return FishTimeCycle.DAY;
    }

    private static boolean inRangeWrap(int tick, int start, int end) {
        if (start < end) {
            return tick >= start && tick < end;
        } else {
            return tick >= start || tick < end;
        }
    }
}

