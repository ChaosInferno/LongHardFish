package org.aincraft.recipes;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;

/**
 * Blocks vanilla fishing rod recipe from appearing in the recipe book
 * while allowing custom fishing-rod recipes to be discovered.
 *
 * Vanilla: minecraft:fishing_rod (blocked)
 * Custom: longhardfish:fishing_rod (allowed)
 */
public final class RecipeDiscoveryBlocker implements Listener {

    @EventHandler
    public void onPlayerRecipeDiscover(PlayerRecipeDiscoverEvent event) {
        NamespacedKey recipe = event.getRecipe();

        // Block vanilla fishing rod only
        if ("minecraft".equals(recipe.getNamespace()) && "fishing_rod".equals(recipe.getKey())) {
            event.setCancelled(true);
        }
    }
}
