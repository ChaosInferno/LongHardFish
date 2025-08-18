package org.aincraft.items;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;

public final class FishKeys {
    private FishKeys() {}
    public static @Nullable String getFishKey(JavaPlugin plugin, ItemStack stack) {
        if (stack == null) return null;
        ItemMeta meta = stack.getItemMeta(); if (meta == null) return null;
        return meta.getPersistentDataContainer().get(Keys.fishId(plugin), PersistentDataType.STRING);
    }

    public static void setFishKey(JavaPlugin plugin, ItemStack stack, String fishKey) {
        ItemMeta meta = stack.getItemMeta(); assert meta != null;
        meta.getPersistentDataContainer().set(Keys.fishId(plugin), PersistentDataType.STRING, fishKey);
        stack.setItemMeta(meta);
    }
}

