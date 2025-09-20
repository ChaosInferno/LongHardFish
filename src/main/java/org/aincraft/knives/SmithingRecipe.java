package org.aincraft.knives;

import org.bukkit.Material;

public record SmithingRecipe(
        String templateKey, // e.g. "netherite_upgrade" (later: namespaced key for custom templates)
        String targetId,    // e.g. "netherite-knife"
        Material addition   // e.g. Material.NETHERITE_INGOT
) {}
