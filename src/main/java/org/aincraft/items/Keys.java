package org.aincraft.items;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class Keys {
    private Keys() {}
    public static NamespacedKey fishId(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "fish_id");
    }

    public static org.bukkit.NamespacedKey knifeId(org.bukkit.plugin.java.JavaPlugin plugin) {
        return new org.bukkit.NamespacedKey(plugin, "knife_id");
    }

    public static NamespacedKey knife(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "knife");
    }

    public static NamespacedKey knifeMax(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "knife_max");
    }

    public static NamespacedKey knifeDurability(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "knife_durability");
    }
}

