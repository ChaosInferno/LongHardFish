// org/aincraft/rods/RodKeys.java
package org.aincraft.rods;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class RodKeys {
    private RodKeys() {}
    public static NamespacedKey rodId(Plugin plugin)  { return new NamespacedKey(plugin, "rod_id"); }
    public static NamespacedKey rodTier(Plugin plugin){ return new NamespacedKey(plugin, "rod_tier"); }
}
