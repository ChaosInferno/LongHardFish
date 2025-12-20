package org.aincraft.recipes;

import org.aincraft.items.CustomFishItems;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Listener that handles custom crafting recipes using exact item matching.
 * Supports recipes with custom items as ingredients (not just vanilla materials).
 * Does not show recipes in the recipe book - purely for crafting mechanics.
 */
public final class CustomRecipeListener implements Listener {
    private final Plugin plugin;
    private final List<CustomRecipe> recipes;

    public CustomRecipeListener(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.recipes = new ArrayList<>();
    }

    /**
     * Register a custom recipe with exact item matching.
     * Pattern is a 3x3 grid where items are identified by their material or custom item ID.
     * Use null for empty slots.
     */
    public void registerRecipe(String resultItemId, ItemPattern pattern) {
        recipes.add(new CustomRecipe(resultItemId, pattern));
        plugin.getLogger().info("[CustomRecipes] Registered hidden recipe for " + resultItemId);
    }


    /**
     * Check crafting grid when items are placed.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] grid = inventory.getMatrix();

        // Check each registered recipe
        for (CustomRecipe recipe : recipes) {
            if (matchesRecipe(grid, recipe.pattern)) {
                ItemStack result = CustomFishItems.create(recipe.resultItemId);
                if (result != null) {
                    inventory.setResult(result);
                    return;
                }
            }
        }
    }

    /**
     * Check if a 3x3 crafting grid matches a recipe pattern.
     */
    private boolean matchesRecipe(ItemStack[] grid, ItemPattern pattern) {
        for (int i = 0; i < 9; i++) {
            if (!matchesItem(grid[i], pattern.items[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if an item matches a pattern slot.
     * Pattern can be: Material, custom item ID string, or null for empty.
     */
    private boolean matchesItem(ItemStack item, Object patternItem) {
        // Empty slot
        if (patternItem == null) {
            return item == null || item.getType() == Material.AIR;
        }

        // Material match
        if (patternItem instanceof Material material) {
            return item != null && item.getType() == material;
        }

        // Custom item match (by ID string)
        if (patternItem instanceof String customItemId) {
            if (item == null || item.getType() == Material.AIR) return false;
            
            // Try to match custom item by checking if CustomFishItems can create the same item
            ItemStack reference = CustomFishItems.create((String) patternItem);
            if (reference == null) return false;
            
            // Compare material type (all custom items are likely the same material)
            // For more accurate matching, you could compare NBT data here
            return item.getType() == reference.getType() && compareCustomItems(item, reference);
        }

        return false;
    }

    /**
     * Compare two custom items for equality.
     * Checks display name and lore to identify custom items.
     */
    private boolean compareCustomItems(ItemStack item1, ItemStack item2) {
        if (!item1.getType().equals(item2.getType())) return false;
        
        var meta1 = item1.getItemMeta();
        var meta2 = item2.getItemMeta();
        
        if (meta1 == null || meta2 == null) {
            return meta1 == meta2;
        }

        // Compare display names
        if ((meta1.hasDisplayName()) != (meta2.hasDisplayName())) return false;
        if (meta1.hasDisplayName() && !meta1.getDisplayName().equals(meta2.getDisplayName())) {
            return false;
        }

        // Compare lore
        if ((meta1.hasLore()) != (meta2.hasLore())) return false;
        if (meta1.hasLore() && !meta1.getLore().equals(meta2.getLore())) {
            return false;
        }

        return true;
    }

    /**
     * Represents a custom recipe pattern for a 3x3 crafting grid.
     */
    public static class ItemPattern {
        public final Object[] items; // 9 items in 3x3 grid

        public ItemPattern(Object... items) {
            if (items.length != 9) {
                throw new IllegalArgumentException("Recipe pattern must have exactly 9 items for 3x3 grid");
            }
            this.items = items;
        }

        /**
         * Create pattern from rows for readability.
         */
        public static ItemPattern of(Object[] row1, Object[] row2, Object[] row3) {
            if (row1.length != 3 || row2.length != 3 || row3.length != 3) {
                throw new IllegalArgumentException("Each row must have 3 items");
            }
            Object[] items = new Object[9];
            System.arraycopy(row1, 0, items, 0, 3);
            System.arraycopy(row2, 0, items, 3, 3);
            System.arraycopy(row3, 0, items, 6, 3);
            return new ItemPattern(items);
        }
    }

    /**
     * Internal class representing a custom recipe.
     */
    private static class CustomRecipe {
        final String resultItemId;
        final ItemPattern pattern;

        CustomRecipe(String resultItemId, ItemPattern pattern) {
            this.resultItemId = resultItemId;
            this.pattern = pattern;
        }
    }
}
