package org.aincraft.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.ingame_items.BiomeRadarItem;
import org.bukkit.block.Biome;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class BiomeRadarListener implements Listener {

    private final BiomeRadarItem itemDef;

    public BiomeRadarListener(BiomeRadarItem itemDef) {
        this.itemDef = itemDef;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        EquipmentSlot hand = e.getHand();
        if (hand == null) return;

        ItemStack item = (hand == EquipmentSlot.HAND)
                ? e.getPlayer().getInventory().getItemInMainHand()
                : e.getPlayer().getInventory().getItemInOffHand();

        if (item == null || item.getType().isAir()) return;
        if (!itemDef.isBiomeRadar(item)) return;

        // consume/use behavior control
        e.setCancelled(true);
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        e.getPlayer().setCooldown(item.getType(), 5);

        Biome biome = e.getPlayer().getLocation().getBlock().getBiome();
        String pretty = prettify(biome);

        e.getPlayer().sendActionBar(
                Component.text("Current Biome: ", NamedTextColor.AQUA)
                        .append(Component.text(pretty, NamedTextColor.AQUA))
        );
    }

    private String prettify(Biome biome) {
        // e.g. WINDSWEPT_FOREST -> Windswept Forest
        String s = biome.name().toLowerCase(java.util.Locale.ENGLISH).replace('_', ' ');
        String[] parts = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}

