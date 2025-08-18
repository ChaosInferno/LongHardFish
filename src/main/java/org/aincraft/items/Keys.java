package org.aincraft.items;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class Keys {
    private Keys() {}
    public static NamespacedKey fishId(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "fish_id");
    }
}

