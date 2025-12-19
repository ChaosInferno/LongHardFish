package org.aincraft.recipes;

import org.bukkit.Material;
import org.bukkit.inventory.RecipeChoice;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable definition of a shaped crafting recipe.
 * Follows SOLID: Single Responsibility - only holds recipe data.
 */
public record RecipeDefinition(
    String id,
    String resultItemId,
    List<String> shape,
    Map<Character, IngredientChoice> ingredients
) {
    public RecipeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resultItemId, "resultItemId");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(ingredients, "ingredients");

        if (shape.isEmpty() || shape.size() > 3) {
            throw new IllegalArgumentException("Shape must have 1-3 rows");
        }
        for (String row : shape) {
            if (row.length() > 3 || row.isEmpty()) {
                throw new IllegalArgumentException("Each row must have 1-3 characters");
            }
        }
    }

    /**
     * Represents an ingredient that can be a single material or multiple choices.
     */
    public sealed interface IngredientChoice
        permits SingleMaterial, MaterialChoiceList {}

    /**
     * Single material ingredient choice.
     */
    public record SingleMaterial(Material material) implements IngredientChoice {
        public SingleMaterial {
            Objects.requireNonNull(material, "material");
        }
    }

    /**
     * Multiple material ingredient choice (any matching material works).
     */
    public record MaterialChoiceList(List<Material> materials) implements IngredientChoice {
        public MaterialChoiceList {
            Objects.requireNonNull(materials, "materials");
            if (materials.isEmpty()) {
                throw new IllegalArgumentException("Material choice must have at least one material");
            }
        }
    }

    /**
     * Convert ingredient choice to Bukkit RecipeChoice.
     */
    public RecipeChoice toRecipeChoice(IngredientChoice choice) {
        return switch (choice) {
            case SingleMaterial single -> new RecipeChoice.MaterialChoice(single.material());
            case MaterialChoiceList multi -> new RecipeChoice.MaterialChoice(multi.materials());
        };
    }
}
