// src/main/java/org/aincraft/knives/KnifeRepairListener.java
package org.aincraft.knives;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                meta.getClass().getMethod("setEnchantmentGlintOverride", Boolean.class).invoke(meta, Boolean.FALSE);
            } catch (Throwable ignored) {}

            base.setItemMeta(meta);
        }

        return base;
    }

    private static int safeVanillaMax(ItemStack it, Damageable d) {
        int vMax = it.getType().getMaxDurability();
        try {
            var has = d.getClass().getMethod("hasMaxDamage");
            if ((boolean) has.invoke(d)) vMax = d.getMaxDamage();
        } catch (Throwable ignored) {}
        return Math.max(1, vMax);
    }

    /* ---------------- events ---------------- */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (matrix == null || matrix.length == 0) return;

        // Find exactly two non-air items (vanilla repair uses two items)
        List<ItemStack> nonAir = new ArrayList<>();
        for (ItemStack it : matrix) {
            if (it != null && !it.getType().isAir()) nonAir.add(it);
        }
        if (nonAir.size() != 2) {
            // Not a 2-item combine → don't interfere (keep vanilla behavior)
            return;
        }

        ItemStack a = nonAir.get(0);
        ItemStack b = nonAir.get(1);
        boolean aKnife = isKnife(a);
        boolean bKnife = isKnife(b);

        if (!aKnife && !bKnife) {
            // No knives involved → let vanilla repair happen
            return;
        }

        // At least one is a knife; only allow if BOTH are knives with the same knife id
        if (!(aKnife && bKnife)) {
            // Mixed knife + non-knife → block
            inv.setResult(null);
            return;
        }

        String idA = knifeIdOf(a);
        String idB = knifeIdOf(b);
        if (idA == null || idB == null || !Objects.equals(idA, idB)) {
            // Two knives but different types → block any vanilla combine
            inv.setResult(null);
            return;
        }

        // --- Custom knife repair path (both knives, same id) ---
        ItemStack result = buildResult(a, b);

        // Ensure no glint on preview (optional)
        try {
            var m = result.getItemMeta();
            if (m != null) {
                m.getClass().getMethod("setEnchantmentGlintOverride", Boolean.class).invoke(m, (Boolean) null);
                result.setItemMeta(m);
            }
        } catch (Throwable ignored) {}

        KnifeDefinition def = provider.get(idA);
        KnifeFactory.normalizeAfterRepair(plugin, result, def);

        int customMax = Math.max(
                org.aincraft.knives.KnifeDurability.getMax(plugin, a),
                org.aincraft.knives.KnifeDurability.getMax(plugin, b)
        );
        int combinedRem = repairedRemaining(a, b);

        var rm = result.getItemMeta();
        if (rm != null) {
            var rpdc = rm.getPersistentDataContainer();
            rpdc.set(org.aincraft.items.Keys.knifeMax(plugin),
                    org.bukkit.persistence.PersistentDataType.INTEGER, customMax);
            rpdc.set(org.aincraft.items.Keys.knifeDurability(plugin),
                    org.bukkit.persistence.PersistentDataType.INTEGER, combinedRem);
            result.setItemMeta(rm);
        }

        applyVisualBar(result, customMax, combinedRem);

        // Show our custom result; this overrides vanilla result for knives
        inv.setResult(result);
    }

    @EventHandler
    public void onCraftClick(CraftItemEvent e) {
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (matrix == null) return;

        // Collect exactly two non-air inputs
        List<ItemStack> nonAir = new ArrayList<>();
        for (ItemStack it : matrix) {
            if (it != null && !it.getType().isAir()) nonAir.add(it);
        }
        if (nonAir.size() != 2) {
            // Not our scenario → let vanilla proceed
            return;
        }

        ItemStack a = nonAir.get(0);
        ItemStack b = nonAir.get(1);
        boolean aKnife = isKnife(a);
        boolean bKnife = isKnife(b);

        if (!aKnife && !bKnife) {
            // Vanilla tool repair → allow
            return;
        }

        if (!(aKnife && bKnife)) {
            // Mixed knife + non-knife → block finalize
            e.setCancelled(true);
            return;
        }

        // Both knives; enforce same id and stamp our PDC/visuals
        String idA = knifeIdOf(a);
        String idB = knifeIdOf(b);
        if (idA == null || idB == null || !Objects.equals(idA, idB)) {
            // Two different knife types → block finalize
            e.setCancelled(true);
            return;
        }

        // Custom knife result: ensure PDC and bar are correct on the crafted item
        ItemStack crafted = e.getCurrentItem();
        if (crafted == null || crafted.getType() == Material.AIR) return;

        // Strip applied enchants (your design for knife craft-repair)
        new ArrayList<>(crafted.getEnchantments().keySet()).forEach(crafted::removeEnchantment);

        int customMax = Math.max(
                org.aincraft.knives.KnifeDurability.getMax(plugin, a),
                org.aincraft.knives.KnifeDurability.getMax(plugin, b)
        );
        int combinedRem = repairedRemaining(a, b);

        var cm = crafted.getItemMeta();
        if (cm != null) {
            var cpdc = cm.getPersistentDataContainer();
            cpdc.set(org.aincraft.items.Keys.knife(plugin),
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);
            cpdc.set(org.aincraft.items.Keys.knifeId(plugin),
                    org.bukkit.persistence.PersistentDataType.STRING, idA);
            cpdc.set(org.aincraft.items.Keys.knifeMax(plugin),
                    org.bukkit.persistence.PersistentDataType.INTEGER, customMax);
            cpdc.set(org.aincraft.items.Keys.knifeDurability(plugin),
                    org.bukkit.persistence.PersistentDataType.INTEGER, combinedRem);
            crafted.setItemMeta(cm);
        }

        applyVisualBar(crafted, customMax, combinedRem);

        KnifeDefinition def = provider.get(idA);
        KnifeFactory.normalizeAfterRepair(plugin, crafted, def);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        AnvilInventory inv = e.getInventory();
        ItemStack left  = inv.getFirstItem();
        ItemStack right = inv.getSecondItem();

        if (left == null || left.getType().isAir() || right == null || right.getType().isAir()) return;

        boolean lKnife = isKnife(left);
        boolean rKnife = isKnife(right);

        if (!lKnife && !rKnife) {
            // No knives → let vanilla anvil behavior proceed
            return;
        }
        if (!(lKnife && rKnife)) {
            // Mixed (knife + non-knife) → block anvil combine for knives
            e.setResult(null);
            return;
        }

        // Both knives: must be same knifeId
        String idL = knifeIdOf(left);
        String idR = knifeIdOf(right);
        if (idL == null || idR == null || !Objects.equals(idL, idR)) {
            e.setResult(null);
            return;
        }

        KnifeDefinition def = provider.get(idL);

        // Choose a base to preserve cosmetics; keep its enchants
        ItemStack base  = (damageOf(left) <= damageOf(right)) ? left : right;
        Map<Enchantment,Integer> keepEnchants = new java.util.HashMap<>(base.getEnchantments());

        // Start result as that base clone
        ItemStack result = base.clone();
        result.setAmount(1);

        // Compute custom max/remaining purely from PDC/definition
        int maxA = org.aincraft.knives.KnifeDurability.getMax(plugin, left);
        int maxB = org.aincraft.knives.KnifeDurability.getMax(plugin, right);
        int customMax = Math.max(maxA, maxB);
        if (customMax <= 0 && def != null && def.durability() != null) customMax = def.durability();
        if (customMax <= 0) { e.setResult(null); return; }

        int combinedRem = repairedRemaining(left, right);

        // Stamp identity + custom durability into PDC
        var rm = result.getItemMeta();
        if (rm != null) {
            var pdc = rm.getPersistentDataContainer();
            pdc.set(org.aincraft.items.Keys.knife(plugin), org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);
            pdc.set(org.aincraft.items.Keys.knifeId(plugin), org.bukkit.persistence.PersistentDataType.STRING, idL);
            pdc.set(org.aincraft.items.Keys.knifeMax(plugin), org.bukkit.persistence.PersistentDataType.INTEGER, customMax);
            pdc.set(org.aincraft.items.Keys.knifeDurability(plugin), org.bukkit.persistence.PersistentDataType.INTEGER, combinedRem);
            result.setItemMeta(rm);
        }

        // Bar + cosmetics
        applyVisualBar(result, customMax, combinedRem);
        if (def != null) KnifeFactory.normalizeAfterRepair(plugin, result, def);

        // IMPORTANT: keep enchants for anvil repair
        restoreEnchantsRespectingAllowList(result, def, keepEnchants);

        e.setResult(result);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilResultClick(InventoryClickEvent e) {
        if (!(e.getInventory() instanceof AnvilInventory inv)) return;
        if (e.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (e.getRawSlot() != 2) return; // result slot

        ItemStack left  = inv.getFirstItem();
        ItemStack right = inv.getSecondItem();
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;

        boolean lKnife = isKnife(left);
        boolean rKnife = isKnife(right);

        if (!lKnife && !rKnife) {
            // Vanilla tools/items → allow vanilla result click
            return;
        }
        if (!(lKnife && rKnife)) {
            // Mixed (knife + non-knife) → block
            e.setCancelled(true);
            return;
        }

        String idL = knifeIdOf(left);
        String idR = knifeIdOf(right);
        if (idL == null || idR == null || !Objects.equals(idL, idR)) {
            e.setCancelled(true);
            return;
        }

        KnifeDefinition def = provider.get(idL);

        // Keep enchants from the better (less-damaged) base
        ItemStack base = (damageOf(left) <= damageOf(right)) ? left : right;
        Map<Enchantment,Integer> keepEnchants = new java.util.HashMap<>(base.getEnchantments());

        int maxA = org.aincraft.knives.KnifeDurability.getMax(plugin, left);
        int maxB = org.aincraft.knives.KnifeDurability.getMax(plugin, right);
        int customMax = Math.max(maxA, maxB);
        if (customMax <= 0 && def != null && def.durability() != null) customMax = def.durability();
        if (customMax <= 0) { e.setCancelled(true); return; }

        int combinedRem = repairedRemaining(left, right);

        // Stamp your PDC onto the *result stack the player will take*
        var rm = result.getItemMeta();
        if (rm != null) {
            var pdc = rm.getPersistentDataContainer();
            pdc.set(org.aincraft.items.Keys.knife(plugin), org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);
            pdc.set(org.aincraft.items.Keys.knifeId(plugin), org.bukkit.persistence.PersistentDataType.STRING, idL);
            pdc.set(org.aincraft.items.Keys.knifeMax(plugin), org.bukkit.persistence.PersistentDataType.INTEGER, customMax);
            pdc.set(org.aincraft.items.Keys.knifeDurability(plugin), org.bukkit.persistence.PersistentDataType.INTEGER, combinedRem);
            result.setItemMeta(rm);
        }

        applyVisualBar(result, customMax, combinedRem);
        if (def != null) KnifeFactory.normalizeAfterRepair(plugin, result, def);

        // Preserve enchants for anvil repair
        restoreEnchantsRespectingAllowList(result, def, keepEnchants);

        // Put updated stack back so the player takes the corrected item
        e.setCurrentItem(result);
    }

    private int repairedRemaining(ItemStack a, ItemStack b) {
        int maxA = org.aincraft.knives.KnifeDurability.getMax(plugin, a);
        int maxB = org.aincraft.knives.KnifeDurability.getMax(plugin, b);
        int remA = org.aincraft.knives.KnifeDurability.getRemaining(plugin, a);
        int remB = org.aincraft.knives.KnifeDurability.getRemaining(plugin, b);

        int max = Math.max(maxA, maxB);
        if (max <= 0) return 0;

        int bonus = Math.max(1, (int) Math.floor(max * 0.05)); // vanilla 5% bonus
        return Math.min(max, Math.max(0, remA) + Math.max(0, remB) + bonus);
    }

    // Map your custom durability to the visible vanilla Damageable bar (cosmetic only)
    private void applyVisualBar(ItemStack stack, int customMax, int customRemaining) {
        if (stack == null || !stack.hasItemMeta()) return;
        var meta = stack.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.Damageable d)) return;

        int vanillaMax;
        boolean hasPerItemMax;
        try {
            int perItem = d.getMaxDamage();              // throws if not present
            vanillaMax = Math.max(1, perItem);
            hasPerItemMax = true;
        } catch (IllegalStateException noPerItem) {
            vanillaMax = Math.max(1, stack.getType().getMaxDurability());
            hasPerItemMax = false;
        }

        if (customMax <= 0) customMax = 1;

        final int legalTop = hasPerItemMax ? Math.max(0, vanillaMax - 1) : vanillaMax;

        double usedFrac = 1.0 - (Math.min(customMax, Math.max(0, customRemaining)) / (double) customMax);
        int mapped = (int) Math.floor(usedFrac * legalTop);
        if (mapped < 0) mapped = 0;
        if (mapped > legalTop) mapped = legalTop;

        d.setDamage(mapped);
        stack.setItemMeta(meta);
    }


    private int vanillaRemaining(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        var meta = it.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.Damageable d)) return 0;

        // Start from the material’s max durability
        int vMax = it.getType().getMaxDurability();

        // Paper 1.21.7: ONLY call getMaxDamage() if hasMaxDamage() is true
        try {
            var hasMaxMethod = d.getClass().getMethod("hasMaxDamage");
            boolean has = (boolean) hasMaxMethod.invoke(d);
            if (has) vMax = d.getMaxDamage();
        } catch (Throwable ignored) {
            // Older API or no per-item max; stick with material max
        }

        if (vMax <= 0) return 0;
        return Math.max(0, vMax - d.getDamage());
    }

    private static void restoreEnchantsRespectingAllowList(ItemStack stack, KnifeDefinition def, Map<Enchantment,Integer> toRestore) {
        if (stack == null || toRestore == null || toRestore.isEmpty()) return;
        var meta = stack.getItemMeta();
        if (meta == null) return;

        for (var e : toRestore.entrySet()) {
            var ench = e.getKey();
            int lvl  = e.getValue();
            // If you want to honor your allow/disallow lists:
            if (def == null || def.isEnchantAllowed(ench)) {
                meta.addEnchant(ench, lvl, true);
            }
        }

        // Make sure shimmer isn't forced off
        try {
            meta.getClass().getMethod("setEnchantmentGlintOverride", Boolean.class).invoke(meta, (Boolean) null);
        } catch (Throwable ignored) {}

        stack.setItemMeta(meta);
    }
}