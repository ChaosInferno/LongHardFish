// src/main/java/org/aincraft/knives/KnifeDefinition.java
package org.aincraft.knives;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

public record KnifeDefinition(
        String id,
        String displayName,
        Material base,
        NamespacedKey modelKey,

        @Nullable Integer durability,
        @Nullable Double  attackDamage,
        @Nullable Double  attackSpeed,
        @Nullable Integer enchantability,

        Set<Enchantment> allowed,
        Set<Enchantment> disallowed,

        @Nullable SmithingRecipe smithing     // << NEW (nullable)
) {
    public KnifeDefinition {
        allowed    = (allowed    == null) ? Collections.emptySet() : Set.copyOf(allowed);
        disallowed = (disallowed == null) ? Collections.emptySet() : Set.copyOf(disallowed);
    }

    public boolean isEnchantAllowed(Enchantment ench) {
        if (ench == null) return false;
        if (disallowed.contains(ench)) return false;
        if (!allowed.isEmpty()) return allowed.contains(ench);
        return true;
    }

    public int durabilityOrDefault(int fallback) {
        return durability != null ? durability : fallback;
    }
}
