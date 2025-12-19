package org.aincraft.recipes;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Parses recipes.yml into RecipeDefinition objects.
 * Follows SOLID: Single Responsibility - only parsing logic.
 */
public final class RecipeConfigParser {
    private final Plugin plugin;

    public RecipeConfigParser(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Parse all recipes from the configuration.
     *
     * @param config The recipes.yml configuration
     * @return Map of recipe ID -> RecipeDefinition
     */
    public Map<String, RecipeDefinition> parse(FileConfiguration config) {
        Map<String, RecipeDefinition> recipes = new LinkedHashMap<>();

        for (String recipeId : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(recipeId);
            if (section == null) {
                plugin.getLogger().warning("[Recipes] Skipping invalid recipe: " + recipeId);
                continue;
            }

            try {
                RecipeDefinition def = parseRecipe(recipeId, section);
                recipes.put(recipeId, def);
                plugin.getLogger().info("[Recipes] Loaded recipe: " + recipeId);
            } catch (Exception e) {
                plugin.getLogger().severe("[Recipes] Failed to parse recipe '" + recipeId
                    + "': " + e.getMessage());
            }
        }

        return recipes;
    }

    private RecipeDefinition parseRecipe(String id, ConfigurationSection section) {
        String resultId = section.getString("result");
        if (resultId == null || resultId.isEmpty()) {
            throw new IllegalArgumentException("Missing 'result' field");
        }

        List<String> shape = section.getStringList("shape");
        if (shape.isEmpty()) {
            throw new IllegalArgumentException("Missing 'shape' field");
        }

        ConfigurationSection ingredientsSection = section.getConfigurationSection("ingredients");
        if (ingredientsSection == null) {
            throw new IllegalArgumentException("Missing 'ingredients' field");
        }

        Map<Character, RecipeDefinition.IngredientChoice> ingredients = new HashMap<>();
        for (String key : ingredientsSection.getKeys(false)) {
            if (key.length() != 1) {
                throw new IllegalArgumentException("Ingredient key must be single character: " + key);
            }
            char c = key.charAt(0);
            ingredients.put(c, parseIngredient(ingredientsSection, key));
        }

        // Validate all shape characters have ingredients
        for (String row : shape) {
            for (char c : row.toCharArray()) {
                if (c != ' ' && !ingredients.containsKey(c)) {
                    throw new IllegalArgumentException("Shape character '" + c
                        + "' has no ingredient mapping");
                }
            }
        }

        return new RecipeDefinition(id, resultId, shape, ingredients);
    }

    private RecipeDefinition.IngredientChoice parseIngredient(
        ConfigurationSection section, String key
    ) {
        Object value = section.get(key);

        // Simple case: "B: BUCKET"
        if (value instanceof String materialName) {
            Material mat = parseMaterial(materialName);
            return new RecipeDefinition.SingleMaterial(mat);
        }

        // Complex case: nested section with type and materials
        if (section.isConfigurationSection(key)) {
            ConfigurationSection nested = section.getConfigurationSection(key);
            String type = nested.getString("type");

            if ("material_choice".equals(type)) {
                List<String> materialNames = nested.getStringList("materials");
                if (materialNames.isEmpty()) {
                    throw new IllegalArgumentException("material_choice must have non-empty 'materials' list");
                }

                List<Material> materials = new ArrayList<>();
                for (String name : materialNames) {
                    materials.add(parseMaterial(name));
                }
                return new RecipeDefinition.MaterialChoiceList(materials);
            } else {
                throw new IllegalArgumentException("Unknown ingredient type: " + type);
            }
        }

        throw new IllegalArgumentException("Invalid ingredient format for key: " + key);
    }

    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid material name: " + name);
        }
    }
}
