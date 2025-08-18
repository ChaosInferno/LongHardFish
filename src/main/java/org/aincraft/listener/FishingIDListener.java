package org.aincraft.listener;

import org.aincraft.items.FishKeys;
import org.aincraft.service.StatsService;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class FishingIDListener implements Listener {
    private final JavaPlugin plugin; private final StatsService stats;

    public FishingIDListener(JavaPlugin plugin, StatsService stats) { this.plugin = plugin; this.stats = stats; }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(e.getCaught() instanceof Item item)) return;

        ItemStack stack = item.getItemStack();
        String fishKey = FishKeys.getFishKey(plugin, stack);
        if (fishKey == null) return;

        stats.recordCatchAsync(e.getPlayer().getUniqueId(), fishKey, stack.hasItemMeta() ? stack.getItemMeta().getDisplayName() : null);
    }
}
