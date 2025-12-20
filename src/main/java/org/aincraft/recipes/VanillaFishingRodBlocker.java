package org.aincraft.recipes;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Blocks vanilla fishing rod crafting attempts while allowing custom recipes.
 *
 * Checks the recipe's NamespacedKey to distinguish between:
 * - Custom "fishing-rod" recipe: Allowed (crafting succeeds)
 * - Vanilla fishing rod (3 Sticks + 2 String): Blocked (result set to null)
 *
 * Vanilla recipe pattern:
 * S S S
 *   S
 *   S
 * (3 Sticks + 2 String)
 *
 * Follows SOLID: Single Responsibility - only blocks vanilla fishing rod crafting.
 */
public final class VanillaFishingRodBlocker implements Listener {

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();

        // Check if the result would be a fishing rod
        ItemStack result = inventory.getResult();
        if (result == null || result.getType() != Material.FISHING_ROD) {
            return;
        }

        // Check if a recipe was matched
        Recipe recipe = inventory.getRecipe();

        // If we have a ShapedRecipe, check if it's our custom "fishing-rod" recipe
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            String recipeKey = shapedRecipe.getKey().getKey();
            // Allow our custom fishing-rod recipe to proceed
            if ("fishing-rod".equals(recipeKey)) {
                return;
            }
        }

        // For vanilla recipes (no recipe match or other shaped recipes),
        // verify it matches the vanilla pattern and block it
        if (isVanillaFishingRodRecipe(inventory)) {
            event.getInventory().setResult(null);
        }
    }

    /**
     * Detects if the crafting inventory matches the vanilla fishing rod recipe pattern.
     * The vanilla recipe is 3 Sticks + 2 String in a specific arrangement.
     *
     * Works for both 2x2 (player inventory) and 3x3 (crafting table) grids.
     */
    private boolean isVanillaFishingRodRecipe(CraftingInventory inventory) {
        ItemStack[] matrix = inventory.getMatrix();

        // Count materials
        int stickCount = 0;
        int stringCount = 0;

        for (ItemStack item : matrix) {
            if (item != null && !item.isEmpty()) {
                if (item.getType() == Material.STICK) {
                    stickCount++;
                } else if (item.getType() == Material.STRING) {
                    stringCount++;
                } else {
                    // Any other material present means it's not vanilla recipe
                    return false;
                }
            }
        }

        // Vanilla recipe is exactly 3 Sticks + 2 String
        return stickCount == 3 && stringCount == 2;
    }
}
