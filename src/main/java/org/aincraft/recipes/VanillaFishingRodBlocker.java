package org.aincraft.recipes;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Blocks the vanilla fishing rod crafting recipe (3 Sticks + 2 String pattern).
 * Forces players to use only the custom fishing-rod recipe from recipe_rods.yml.
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

        // Check if this is a vanilla fishing rod crafting attempt
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
