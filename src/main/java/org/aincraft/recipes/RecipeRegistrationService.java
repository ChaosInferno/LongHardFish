package org.aincraft.recipes;

import org.aincraft.items.CustomFishItems;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Objects;

/**
 * Registers parsed recipes with Bukkit's recipe system.
 * Follows SOLID: Single Responsibility - only registration logic.
 */
public final class RecipeRegistrationService {
    private final Plugin plugin;

    public RecipeRegistrationService(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Register all recipes with Bukkit.
     * Called during plugin onEnable() after CustomFishItems are registered.
     */
    public void registerAll(Map<String, RecipeDefinition> definitions) {
        int successCount = 0;
        int failCount = 0;

        for (RecipeDefinition def : definitions.values()) {
            try {
                registerRecipe(def);
                successCount++;
            } catch (Exception e) {
                plugin.getLogger().severe("[Recipes] Failed to register recipe '"
                    + def.id() + "': " + e.getMessage());
                failCount++;
            }
        }

        plugin.getLogger().info("[Recipes] Registered " + successCount + " recipes ("
            + failCount + " failed)");
    }

    private void registerRecipe(RecipeDefinition def) {
        // Get result item from CustomFishItems registry
        ItemStack result = CustomFishItems.create(def.resultItemId());
        if (result == null) {
            throw new IllegalStateException("Unknown custom item: " + def.resultItemId());
        }

        // Create NamespacedKey (format: longhardfish:biome_radar)
        NamespacedKey key = new NamespacedKey(plugin, def.id());

        // Create ShapedRecipe
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        // Set shape (convert List<String> to String[])
        recipe.shape(def.shape().toArray(new String[0]));
        plugin.getLogger().info("[Recipes] Recipe '" + def.id() + "' shape: " + def.shape());

        // Set ingredients
        for (Map.Entry<Character, RecipeDefinition.IngredientChoice> entry : def.ingredients().entrySet()) {
            char c = entry.getKey();
            RecipeChoice choice = def.toRecipeChoice(entry.getValue());
            recipe.setIngredient(c, choice);
        }

        // Register with Bukkit
        boolean added = Bukkit.addRecipe(recipe);
        if (added) {
            plugin.getLogger().info("[Recipes] Recipe '" + def.id() + "' registered successfully");
        } else {
            plugin.getLogger().warning("[Recipes] Recipe '" + def.id()
                + "' may already exist or failed to register");
        }
    }

    /**
     * Unregister all plugin recipes on disable.
     */
    public void unregisterAll(Map<String, RecipeDefinition> definitions) {
        for (RecipeDefinition def : definitions.values()) {
            NamespacedKey key = new NamespacedKey(plugin, def.id());
            Bukkit.removeRecipe(key);
        }
        plugin.getLogger().info("[Recipes] Unregistered all recipes");
    }
}
