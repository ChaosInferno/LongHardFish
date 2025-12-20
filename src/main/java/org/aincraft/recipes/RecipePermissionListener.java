package org.aincraft.recipes;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Map;
import java.util.Objects;

/**
 * Enforces permission requirements for crafting custom recipes.
 * If a recipe has a permission field and the player lacks it, the craft is prevented.
 * Follows SOLID: Single Responsibility - only handles permission enforcement.
 */
public final class RecipePermissionListener implements Listener {
    private final Map<String, RecipeDefinition> recipeDefinitions;

    public RecipePermissionListener(Map<String, RecipeDefinition> recipeDefinitions) {
        this.recipeDefinitions = Objects.requireNonNull(recipeDefinitions, "recipeDefinitions");
    }

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        Recipe recipe = inventory.getRecipe();

        if (recipe == null) {
            return;
        }

        // Only handle ShapedRecipes registered by our plugin
        if (!(recipe instanceof ShapedRecipe shapedRecipe)) {
            return;
        }

        // Extract recipe ID from NamespacedKey
        String recipeId = shapedRecipe.getKey().getKey();
        RecipeDefinition definition = recipeDefinitions.get(recipeId);

        if (definition == null || definition.permission() == null) {
            return;
        }

        // Check if player has permission
        if (!event.getViewers().isEmpty() && !event.getViewers().get(0).hasPermission(definition.permission())) {
            event.getInventory().setResult(null);
        }
    }
}
