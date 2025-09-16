// src/main/java/org/aincraft/knives/KnifeDurability.java
package org.aincraft.knives;

import org.aincraft.items.Keys;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public final class KnifeDurability {
    private KnifeDurability(){}

    public static int getRemaining(JavaPlugin plugin, ItemStack knife) {
        ItemMeta m = knife.getItemMeta(); if (m==null) return 0;
        Integer v = m.getPersistentDataContainer().get(Keys.knifeDurability(plugin), PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }
    public static int getMax(JavaPlugin plugin, ItemStack knife) {
        ItemMeta m = knife.getItemMeta(); if (m==null) return 0;
        Integer v = m.getPersistentDataContainer().get(Keys.knifeMax(plugin), PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    /** Damage by N “uses”, honoring Unbreaking (chance to not consume). */
    public static int damage(JavaPlugin plugin, ItemStack knife, int uses) {
        int avoided = 0;
        int unb = knife.getEnchantmentLevel(Enchantment.UNBREAKING); // 0..3
        int take = 0;
        for (int i=0;i<uses;i++) {
            if (unb > 0) {
                // Vanilla-like: 1/(level+1) chance to consume
                if (ThreadLocalRandom.current().nextInt(unb + 1) == 0) take++;
                else avoided++;
            } else {
                take++;
            }
        }
        if (take > 0) {
            setRemaining(plugin, knife, Math.max(0, getRemaining(plugin, knife) - take));
        }
        return avoided;
    }

    public static void setRemaining(JavaPlugin plugin, ItemStack knife, int remaining) {
        ItemMeta m = knife.getItemMeta(); if (m==null) return;
        m.getPersistentDataContainer().set(Keys.knifeDurability(plugin), PersistentDataType.INTEGER, remaining);
        knife.setItemMeta(m);
    }

    public static boolean isKnife(JavaPlugin plugin, ItemStack stack) {
        if (stack == null) return false;
        ItemMeta m = stack.getItemMeta(); if (m==null) return false;
        Byte b = m.getPersistentDataContainer().get(Keys.knife(plugin), PersistentDataType.BYTE);
        return b != null && b == 1;
    }
}

