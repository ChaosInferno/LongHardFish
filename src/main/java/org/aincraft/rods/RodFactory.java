// org/aincraft/rods/RodFactory.java
package org.aincraft.rods;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Factory for creating RodItemStack instances.
 * Provides static create method for use with CustomFishItems registry.
 * Delegates to RodItemFactory for the actual item creation.
 */
public final class RodFactory {
    private RodFactory() {}

    /**
     * Create a rod item from a RodDefinition.
     * Follows the same pattern as KnifeFactory.create() for consistency.
     *
     * @param plugin The plugin instance
     * @param definition The rod definition
     * @return A new ItemStack representing the rod
     */
    public static ItemStack create(Plugin plugin, RodDefinition definition) {
        RodItemFactory factory = new RodItemFactory(plugin);
        return factory.create(definition);
    }
}
