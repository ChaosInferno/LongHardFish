// src/main/java/org/aincraft/items/BaitKeys.java
package org.aincraft.items;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;

public final class BaitKeys {
    private BaitKeys() {}

    public static NamespacedKey baitId(Plugin plugin) { return new NamespacedKey(plugin, "bait_id"); }
    public static NamespacedKey rodBait(Plugin plugin) { return new NamespacedKey(plugin, "rod_bait"); }
    public static NamespacedKey rodBaitCount(Plugin plugin) { return new NamespacedKey(plugin, "rod_bait_count"); }

    public static @Nullable String getBaitId(Plugin plugin, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(baitId(plugin), PersistentDataType.STRING);
    }
    public static void setBaitId(Plugin plugin, ItemStack stack, String id) {
        if (stack == null) return;
        ItemMeta m = stack.getItemMeta(); if (m == null) return;
        m.getPersistentDataContainer().set(baitId(plugin), PersistentDataType.STRING, id);
        stack.setItemMeta(m);
    }

    public static void setRodBait(Plugin plugin, ItemStack rod, String id, int count) {
        if (rod == null) return;
        ItemMeta m = rod.getItemMeta(); if (m == null) return;
        m.getPersistentDataContainer().set(rodBait(plugin), PersistentDataType.STRING, id);
        m.getPersistentDataContainer().set(rodBaitCount(plugin), PersistentDataType.INTEGER, Math.max(0, count));
        rod.setItemMeta(m);
    }
    public static @Nullable String getRodBait(Plugin plugin, ItemStack rod) {
        if (rod == null || !rod.hasItemMeta()) return null;
        return rod.getItemMeta().getPersistentDataContainer().get(rodBait(plugin), PersistentDataType.STRING);
    }
    public static int getRodBaitCount(Plugin plugin, ItemStack rod) {
        if (rod == null || !rod.hasItemMeta()) return 0;
        Integer n = rod.getItemMeta().getPersistentDataContainer().get(rodBaitCount(plugin), PersistentDataType.INTEGER);
        return n == null ? 0 : Math.max(0, n);
    }
    public static void clearRodBait(Plugin plugin, ItemStack rod) {
        if (rod == null || !rod.hasItemMeta()) return;
        ItemMeta m = rod.getItemMeta();
        var pdc = m.getPersistentDataContainer();
        pdc.remove(rodBait(plugin));
        pdc.remove(rodBaitCount(plugin));
        rod.setItemMeta(m);
    }
}
