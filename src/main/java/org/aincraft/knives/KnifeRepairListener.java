// src/main/java/org/aincraft/knives/KnifeRepairListener.java
package org.aincraft.knives;

import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class KnifeRepairListener implements Listener {
    private final JavaPlugin plugin;
    private final KnifeProvider provider;

    public KnifeRepairListener(JavaPlugin plugin, KnifeProvider provider) {
        this.plugin = plugin;
        this.provider = provider;
    }

    /* ---------------- helpers ---------------- */

    private boolean isKnife(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        Byte tag = it.getItemMeta().getPersistentDataContainer().get(
                org.aincraft.items.Keys.knife(plugin),
                org.bukkit.persistence.PersistentDataType.BYTE
        );
        return tag != null && tag == (byte)1;
    }

    private String knifeIdOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(
                org.aincraft.items.Keys.knifeId(plugin),
                org.bukkit.persistence.PersistentDataType.STRING
        );
    }

    private static int maxOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        var meta = it.getItemMeta();
        if (meta instanceof Damageable d) {
            try {
                // Paper 1.21+ has hasMaxDamage()
                var m = d.getClass().getMethod("hasMaxDamage");
                boolean has = (boolean) m.invoke(d);
                if (has) return d.getMaxDamage();
            } catch (Throwable ignore) {
                // fall through
            }
            // Fallback to vanilla type max
            return it.getType().getMaxDurability();
        }
        return it.getType().getMaxDurability();
    }

    private static int damageOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        var meta = it.getItemMeta();
        if (meta instanceof Damageable d) return d.getDamage();
        return 0;
    }

    /** Vanilla crafting-repair rule: remaining = min(max, remA + remB + floor(5% * max)) */
    private static int repairedDamage(ItemStack a, ItemStack b) {
        int max = Math.max(maxOf(a), maxOf(b));
        if (max <= 0) return 0;
        int remA = Math.max(0, max - damageOf(a));
        int remB = Math.max(0, max - damageOf(b));
        int bonus = Math.max(1, (int)Math.floor(max * 0.05)); // vanilla grants 5% bonus, at least 1
        int combined = Math.min(max, remA + remB + bonus);
        return Math.max(0, max - combined);
    }

    /** Build the repaired result from two matching knives: copy identity, clear enchants, set new damage. */
    private ItemStack buildResult(ItemStack a, ItemStack b) {
        // Prefer cloning the less-damaged one to preserve display/model/etc.
        ItemStack base = (damageOf(a) <= damageOf(b)) ? a.clone() : b.clone();
        base.setAmount(1);

        var meta = base.getItemMeta();
        if (meta != null) {
            // Remove all **applied** enchants from the meta (not just the stack)
            new ArrayList<>(meta.getEnchants().keySet()).forEach(meta::removeEnchant);

            // Also remove stored enchants if somehow present (paranoia/safety)
            try {
                if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta esm) {
                    new ArrayList<>(esm.getStoredEnchants().keySet()).forEach(esm::removeStoredEnchant);
                }
            } catch (Throwable ignored) {}

            // Force-disable the shimmer even if something set a glint override
            try {
                meta.getClass()
                        .getMethod("setEnchantmentGlintOverride", Boolean.class)
                        .invoke(meta, Boolean.FALSE);
            } catch (Throwable ignored) {}

            // Apply repaired damage
            if (meta instanceof Damageable d) {
                d.setDamage(repairedDamage(a, b));
            }
            base.setItemMeta(meta);
        }

        return base;
    }


    /* ---------------- events ---------------- */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (matrix == null || matrix.length == 0) return;

        List<ItemStack> knives = new ArrayList<>();
        for (ItemStack it : matrix) {
            if (it == null || it.getType().isAir()) continue;
            if (!isKnife(it)) {
                // Any non-knife item present → not our repair; avoid colliding with other recipes
                inv.setResult(null);
                return;
            }
            knives.add(it);
        }

        if (knives.isEmpty()) { inv.setResult(null); return; }

        // Only allow 2-knife repair (vanilla style). Anything else → no result.
        if (knives.size() != 2) { inv.setResult(null); return; }

        String id0 = knifeIdOf(knives.get(0));
        String id1 = knifeIdOf(knives.get(1));
        if (id0 == null || id1 == null || !Objects.equals(id0, id1)) {
            inv.setResult(null); // must be the same knife type
            return;
        }

        // Build repaired result; keep name/model/PDC; remove enchants
        ItemStack result = buildResult(knives.get(0), knives.get(1));

        // EXTRA: make sure preview has no glint (like vanilla). Safe on older APIs.
        try {
            var m = result.getItemMeta();
            if (m != null) {
                // Paper 1.21+: setEnchantmentGlintOverride(Boolean)
                m.getClass().getMethod("setEnchantmentGlintOverride", Boolean.class).invoke(m, (Boolean) null);
                result.setItemMeta(m);
            }
        } catch (Throwable ignored) {}

        // Normalize preview so it looks exactly like one of your knives
        // (restores your name/model/attributes/green lore, keeps knife-id in PDC)
        KnifeDefinition def = provider.get(id0); // same id for both inputs
        KnifeFactory.normalizeAfterRepair(plugin, result, def);

        // Show this in the crafting output preview
        inv.setResult(result);
    }

    @EventHandler
    public void onCraftClick(CraftItemEvent e) {
        // Re-validate right before the craft finalizes (in case another plugin changed the result)
        CraftingInventory inv = e.getInventory();
        ItemStack result = inv.getResult();
        if (result == null || result.getType() == Material.AIR) return;

        ItemStack[] matrix = inv.getMatrix();
        List<ItemStack> knives = new ArrayList<>();
        for (ItemStack it : matrix) {
            if (it == null || it.getType().isAir()) continue;
            if (!isKnife(it)) { e.setCancelled(true); return; }
            knives.add(it);
        }
        if (knives.size() != 2) { e.setCancelled(true); return; }
        String id0 = knifeIdOf(knives.get(0));
        String id1 = knifeIdOf(knives.get(1));
        if (id0 == null || id1 == null || !Objects.equals(id0, id1)) { e.setCancelled(true); return; }

        // Ensure enchantments are stripped on the actual crafted stack
        var crafted = e.getCurrentItem();
        if (crafted != null) {
            new ArrayList<>(crafted.getEnchantments().keySet()).forEach(crafted::removeEnchantment);
            // Damage should already be set from PrepareItemCraftEvent; compute again just in case:
            if (crafted.hasItemMeta() && crafted.getItemMeta() instanceof Damageable d) {
                d.setDamage(repairedDamage(knives.get(0), knives.get(1)));
                crafted.setItemMeta(d);
            }
        }
        KnifeDefinition def = provider.get(id0);
        KnifeFactory.normalizeAfterRepair(plugin, crafted, def);
    }
}