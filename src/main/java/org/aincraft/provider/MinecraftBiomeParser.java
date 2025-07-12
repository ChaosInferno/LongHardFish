package org.aincraft.provider;

import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.EventHandler;

public class MinecraftBiomeParser {

    public static Biome getBiomeAtFishHook(PlayerFishEvent event) {
        if (event.getHook() == null || event.getHook().getLocation() == null) {
                return null; // Safety check
            }

        Location hookLocation = event.getHook().getLocation();
        return hookLocation.getWorld().getBiome(hookLocation);
    }
}
