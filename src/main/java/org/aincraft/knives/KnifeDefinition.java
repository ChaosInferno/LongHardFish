// src/main/java/org/aincraft/knives/KnifeDefinition.java
package org.aincraft.knives;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

public record KnifeDefinition(
        String id,
        String displayName,
        Material base,
        NamespacedKey modelKey,

        // Optional overrides (null = leave vanilla / use defaults)
        @Nullable Integer durability,
        @Nullable Double  attackDamage,
        @Nullable Double  attackSpeed,
        @Nullable Integer enchantability,

        // Enchantment policy
        Set<Enchantment> allowed,    // empty = allow all (except those in disallowed)
        Set<Enchantment> disallowed  // always deny if present
) {
    public KnifeDefinition {
        // null-safe sets
        allowed    = (allowed    == null) ? Collections.emptySet() : Set.copyOf(allowed);
        disallowed = (disallowed == null) ? Collections.emptySet() : Set.copyOf(disallowed);
    }

    /** True if this enchantment is permitted by this knife’s policy. */
    public boolean isEnchantAllowed(Enchantment ench) {
        if (ench == null) return false;
        if (disallowed.contains(ench)) return false;
        if (!allowed.isEmpty()) return allowed.contains(ench);
        return true; // no explicit allow-list => allow unless explicitly disallowed
    }

    /** Convenience: use a sensible default when unspecified. */
    public int durabilityOrDefault(int fallback) {
        return durability != null ? durability : fallback;
    }
}
