package org.aincraft.recipes;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Parses compounded_recipes.yml into CustomRecipeListener.ItemPattern objects.
 * Supports recipes that mix vanilla materials with custom items as ingredients.
 */
public final class CompoundedRecipeParser {
    private final Plugin plugin;

    public CompoundedRecipeParser(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Parse all compounded recipes from the configuration.
     *
     * @param config The compounded_recipes.yml configuration
     * @return Map of recipe ID -> ItemPattern
     */
    public Map<String, CustomRecipeListener.ItemPattern> parse(FileConfiguration config) {
        Map<String, CustomRecipeListener.ItemPattern> recipes = new HashMap<>();

        for (String recipeId : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(recipeId);
            if (section == null) {
                plugin.getLogger().warning("[CompoundedRecipes] Skipping invalid recipe: " + recipeId);
                continue;
            }

            try {
                String resultId = section.getString("result");
                if (resultId == null || resultId.isEmpty()) {
                    plugin.getLogger().warning("[CompoundedRecipes] Recipe '" + recipeId + "' missing result");
                    continue;
                }

                List<String> shape = section.getStringList("shape");
                if (shape.isEmpty() || shape.size() != 3) {
                    plugin.getLogger().warning("[CompoundedRecipes] Recipe '" + recipeId + "' must have 3 rows");
                    continue;
                }

                ConfigurationSection ingredientsSection = section.getConfigurationSection("ingredients");
                if (ingredientsSection == null) {
                    plugin.getLogger().warning("[CompoundedRecipes] Recipe '" + recipeId + "' missing ingredients");
                    continue;
                }

                // Build 9-item pattern from shape and ingredients
                Object[] pattern = new Object[9];
                int index = 0;

                for (String row : shape) {
                    if (row.length() != 3) {
                        plugin.getLogger().warning("[CompoundedRecipes] Recipe '" + recipeId + "' row must have 3 characters");
                        continue;
                    }

                    for (char c : row.toCharArray()) {
                        Object ingredient = parseIngredient(ingredientsSection, c);
                        if (ingredient == null && c != ' ') {
                            plugin.getLogger().warning("[CompoundedRecipes] Recipe '" + recipeId + "' unknown ingredient: " + c);
                        }
                        pattern[index++] = ingredient;
                    }
                }

                CustomRecipeListener.ItemPattern itemPattern = new CustomRecipeListener.ItemPattern(pattern);
                recipes.put(resultId, itemPattern);
                plugin.getLogger().info("[CompoundedRecipes] Loaded recipe: " + recipeId);

            } catch (Exception e) {
                plugin.getLogger().severe("[CompoundedRecipes] Failed to parse recipe '" + recipeId + "': " + e.getMessage());
            }
        }

        return recipes;
    }

    /**
     * Parse a single ingredient character to its material or custom item ID.
     */
    private Object parseIngredient(ConfigurationSection section, char key) {
        String keyStr = String.valueOf(key);
        Object value = section.get(keyStr);

        if (value == null) {
            return null;
        }

        // Try as vanilla material first
        try {
            Material mat = Material.valueOf(value.toString().toUpperCase(Locale.ENGLISH));
            return mat;
        } catch (IllegalArgumentException e) {
            // Not a material, might be a custom item ID
            String customId = value.toString().toLowerCase(Locale.ENGLISH);
            return customId;
        }
    }
}
