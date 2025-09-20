package org.aincraft.knives;

import org.aincraft.items.Keys;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public final class KnifeDurability {
    private KnifeDurability(){}

    public static int getRemaining(JavaPlugin plugin, ItemStack knife) {
        ItemMeta m = (knife == null) ? null : knife.getItemMeta(); if (m==null) return 0;
        Integer v = m.getPersistentDataContainer().get(Keys.knifeDurability(plugin), PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public static int getMax(JavaPlugin plugin, ItemStack knife) {
        ItemMeta m = (knife == null) ? null : knife.getItemMeta(); if (m==null) return 0;
        Integer v = m.getPersistentDataContainer().get(Keys.knifeMax(plugin), PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    /** Vanilla-like Unbreaking roll; returns how many uses were actually consumed. */
    private static int rollUsesToConsume(int uses, int unbreakingLevel) {
        if (uses <= 0) return 0;
        if (unbreakingLevel <= 0) return uses;
        int take = 0;
        for (int i = 0; i < uses; i++) {
            // 1 / (level + 1) chance to consume
            if (ThreadLocalRandom.current().nextInt(unbreakingLevel + 1) == 0) take++;
        }
        return take;
    }

    /** Lower-level: consume durability counters and update bar. Does NOT remove item when it hits 0. */
    public static void setRemaining(JavaPlugin plugin, ItemStack stack, int remaining) {
        if (stack == null || !stack.hasItemMeta()) return;
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable d)) return;

        // --- your custom counters (clamped) ---
        int customMax = Math.max(1, getMax(plugin, stack));
        int customRem = Math.max(0, Math.min(customMax, remaining));

        // --- detect per-item max safely (no reflection) ---
        boolean hasPerItemMax = false;
        int vanillaMax;
        try {
            // Paper 1.21+: throws if this meta doesn't have per-item max
            int perItem = d.getMaxDamage();
            vanillaMax = Math.max(1, perItem);
            hasPerItemMax = true;
        } catch (IllegalStateException noPerItem) {
            // message is usually "We don't have max_damage!..."
            vanillaMax = Math.max(1, stack.getType().getMaxDurability());
            hasPerItemMax = false;
        }

        // legalTop is the **inclusive** top value we are allowed to pass to setDamage()
        // per-item: 0..(vanillaMax-1), material: 0..vanillaMax
        final int legalTop = hasPerItemMax ? Math.max(0, vanillaMax - 1) : vanillaMax;

// EXACT integer mapping:  damage = ((max - rem) * legalTop) / max
// This yields: rem=max → 0; rem=0 → legalTop; and for rem>0 always < legalTop
        int mapped = (int) (((long) (customMax - customRem) * (long) legalTop) / (long) customMax);
        if (mapped < 0) mapped = 0;              // paranoia
        if (mapped > legalTop) mapped = legalTop;

        d.setDamage(mapped);
        stack.setItemMeta(meta);

// Persist your exact remaining for logic
        meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(
                org.aincraft.items.Keys.knifeDurability(plugin),
                org.bukkit.persistence.PersistentDataType.INTEGER,
                customRem
        );
        stack.setItemMeta(meta);
    }



    /**
     * Preferred: spend N uses, update the bar, and if remaining hits 0,
     * remove the item and play a break sound. Returns true if the knife broke.
     */
    public static boolean damageAndMaybeBreak(
            JavaPlugin plugin,
            Player owner,                // can be null (then no sound)
            Inventory inv,               // inventory holding the item
            int slot,                    // slot index inside inv
            ItemStack knife,             // the knife stack
            int uses) {

        if (knife == null || knife.getType().isAir() || uses <= 0) return false;

        int unb = knife.getEnchantmentLevel(Enchantment.UNBREAKING);
        int take = rollUsesToConsume(uses, unb);
        if (take <= 0) return false;

        int remaining = Math.max(0, getRemaining(plugin, knife) - take);
        setRemaining(plugin, knife, remaining);

        if (remaining <= 0) {
            // Remove the stack and play break SFX
            tryRemove(inv, slot, knife);
            playBreak(owner);
            return true;
        }
        return false;
    }

    private static void tryRemove(Inventory inv, int slot, ItemStack expected) {
        if (inv == null) return;
        try {
            ItemStack inSlot = inv.getItem(slot);
            if (inSlot != null && inSlot.isSimilar(expected)) {
                inv.setItem(slot, null);
            } else {
                // Fallback: attempt an index-based clear anyway
                inv.setItem(slot, null);
            }
        } catch (Throwable ignored) {}
    }

    private static void playBreak(Player p) {
        if (p == null) return;
        Location loc = p.getLocation();
        try {
            // Satisfying break sound; you can swap to something else if you prefer
            p.getWorld().playSound(loc, Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.7f, 1.0f);
        } catch (Throwable ignored) {}
    }

    public static boolean isKnife(JavaPlugin plugin, ItemStack stack) {
        if (stack == null) return false;
        ItemMeta m = stack.getItemMeta(); if (m==null) return false;
        Byte b = m.getPersistentDataContainer().get(Keys.knife(plugin), PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    /** Start from material durability, then only use per-item max if hasMaxDamage() returns true. */
    private static int safeVanillaMax(ItemStack it, org.bukkit.inventory.meta.Damageable d) {
        int vMax = it.getType().getMaxDurability(); // material fallback
        try {
            var has = d.getClass().getMethod("hasMaxDamage");
            if ((boolean) has.invoke(d)) vMax = d.getMaxDamage();
        } catch (Throwable ignored) {}
        return Math.max(1, vMax);
    }
}
