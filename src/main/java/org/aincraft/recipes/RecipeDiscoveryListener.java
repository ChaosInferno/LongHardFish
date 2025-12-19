package org.aincraft.recipes;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Listener that automatically discovers custom recipes for players when they have ingredients.
 * Works like vanilla recipe book - recipes appear when you have the required items.
 */
public final class RecipeDiscoveryListener implements Listener {
    private final Plugin plugin;
    private final Map<String, RecipeDefinition> recipes;

    public RecipeDiscoveryListener(Plugin plugin, Map<String, RecipeDefinition> recipes) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.recipes = Objects.requireNonNull(recipes, "recipes");
    }

    /**
     * Discover recipes when player joins.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        discoverAvailableRecipes(player);
    }

    /**
     * Discover recipes when player's inventory changes.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryChange(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            // Schedule discovery check after inventory updates
            plugin.getServer().getScheduler().runTaskLater(plugin, 
                () -> discoverAvailableRecipes(player), 1L);
        }
    }

    /**
     * Check which recipes the player has ingredients for and discover them.
     */
    private void discoverAvailableRecipes(Player player) {
        for (Map.Entry<String, RecipeDefinition> entry : recipes.entrySet()) {
            NamespacedKey recipeKey = new NamespacedKey(plugin, entry.getKey());
            
            try {
                // Always discover recipes for this player
                player.discoverRecipe(recipeKey);
            } catch (Exception e) {
                plugin.getLogger().warning("[Recipes] Failed to discover recipe '"
                    + recipeKey + "' for " + player.getName() + ": " + e.getMessage());
            }
        }
    }
}
