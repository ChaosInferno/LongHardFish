package org.aincraft.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.ingame_items.WeatherRadioItem;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class WeatherRadioListener implements Listener {

    private final WeatherRadioItem radio;

    public WeatherRadioListener(WeatherRadioItem radio) {
        this.radio = radio;
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
        if (!radio.isWeatherRadio(item)) return;

        // Consume the interaction to avoid placing/using
        e.setCancelled(true);
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        // Tiny cooldown to prevent spam if the player holds RMB (adjust/remove if you like)
        e.getPlayer().setCooldown(item.getType(), 5);

        World w = e.getPlayer().getWorld();
        // MC weather logic: storm = rain/snow, thundering = thunderstorm
        String weather = (!w.hasStorm())
                ? "Sunny"
                : (w.isThundering() ? "Stormy" : "Rainy");

        e.getPlayer().sendActionBar(Component.text(
                "Current Weather: " + weather, NamedTextColor.AQUA));
    }
}

