// src/main/java/org/aincraft/knives/KnifeSmithingListener.java
package org.aincraft.knives;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class KnifeSmithingListener implements Listener {
    private final JavaPlugin plugin;
    private final KnifeProvider provider;

    public KnifeSmithingListener(JavaPlugin plugin, KnifeProvider provider) {
        this.plugin = plugin;
        this.provider = provider;
    }

    /* ---------- helpers (similar to your other listeners) ---------- */

    private boolean isKnife(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        Byte tag = it.getItemMeta().getPersistentDataContainer()
                .get(org.aincraft.items.Keys.knife(plugin), PersistentDataType.BYTE);
        return tag != null && tag == (byte)1;
    }

    private String knifeIdOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer()
                .get(org.aincraft.items.Keys.knifeId(plugin), PersistentDataType.STRING);
    }

    private int vanillaRemaining(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        var meta = it.getItemMeta();
        if (!(meta instanceof Damageable d)) return 0;

        // Start from material max
        int vMax = it.getType().getMaxDurability();

        // Paper 1.21.7: only call getMaxDamage() if hasMaxDamage() is true
        try {
            var hasMax = d.getClass().getMethod("hasMaxDamage");
            boolean has = (boolean) hasMax.invoke(d);
            if (has) vMax = d.getMaxDamage();
        } catch (Throwable ignored) {
            // Older API or no per-item max; stick with material max
        }

        if (vMax <= 0) return 0;
        return Math.max(0, vMax - d.getDamage());
    }

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

    private boolean templateMatches(String yamlKey, ItemStack templateStack) {
        if (yamlKey == null || templateStack == null) return false;
        yamlKey = yamlKey.toLowerCase();
        // For now only vanilla netherite upgrade template; easy to extend later
        if (yamlKey.equals("netherite_upgrade")) {
            return templateStack.getType() == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
        }
        // Future: support custom namespaced template items here
        return false;
    }

    private ItemStack makeUpgrade(ItemStack baseKnife, KnifeDefinition fromDef, SmithingRecipe rule) {
        // Validate target def
        KnifeDefinition toDef = provider.get(rule.targetId());
        if (toDef == null) return null;

        // Carry durability proportionally
        int oldMax = KnifeDurability.getMax(plugin, baseKnife);
        int oldRem = KnifeDurability.getRemaining(plugin, baseKnife);
        if (oldMax <= 0) {
            oldMax = Math.max(1, vanillaRemaining(baseKnife)); // fallback; shouldn't happen for your knives
            oldRem = oldMax;
        }
        int newMax = (toDef.durability() != null) ? toDef.durability() : oldMax;
        double frac = (oldMax > 0) ? (oldRem / (double) oldMax) : 0.0;
        int newRem = Math.min(newMax, Math.max(0, (int) Math.round(frac * newMax)));

        // 1) Build a proper target knife (sets name/model/attrs AND per-item max when defined in YAML)
        ItemStack result = KnifeFactory.create(plugin, toDef);

        // 2) Copy applied enchants from the base (optional: filter via toDef.isEnchantAllowed)
        if (baseKnife.hasItemMeta()) {
            var bm = baseKnife.getItemMeta();
            if (bm != null) {
                var rm = result.getItemMeta();
                if (rm != null) {
                    for (var entry : bm.getEnchants().entrySet()) {
                        var ench = entry.getKey();
                        int lvl  = entry.getValue();
                        // honor allow-list if you want; otherwise always copy:
                        if (toDef.isEnchantAllowed(ench)) {
                            rm.addEnchant(ench, lvl, true);
                        }
                    }
                    result.setItemMeta(rm);
                }
            }
        }

        // 3) Stamp custom durability counters (PDC)
        var meta = result.getItemMeta();
        var pdc  = meta.getPersistentDataContainer();
        pdc.set(org.aincraft.items.Keys.knife(plugin),        PersistentDataType.BYTE,    (byte)1);
        pdc.set(org.aincraft.items.Keys.knifeId(plugin),      PersistentDataType.STRING,  toDef.id());
        pdc.set(org.aincraft.items.Keys.knifeMax(plugin),     PersistentDataType.INTEGER, newMax);
        pdc.set(org.aincraft.items.Keys.knifeDurability(plugin), PersistentDataType.INTEGER, newRem);
        result.setItemMeta(meta);

        // 4) Mirror to visible bar
        applyVisualBar(result, newMax, newRem);

        return result;
    }


    /* ---------- smithing events ---------- */

    // Preview result
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent e) {
        SmithingInventory inv = e.getInventory();
        ItemStack template = inv.getInputTemplate();
        ItemStack base     = inv.getInputEquipment();
        ItemStack addition = inv.getInputMineral();
        if (template == null || base == null || addition == null) return;

        if (!isKnife(base)) return;
        String baseId = knifeIdOf(base);
        if (baseId == null) return;
        KnifeDefinition fromDef = provider.get(baseId);
        if (fromDef == null || fromDef.smithing() == null) return;

        SmithingRecipe rule = fromDef.smithing();
        if (!templateMatches(rule.templateKey(), template)) return;
        if (addition.getType() != rule.addition()) return;

        ItemStack result = makeUpgrade(base, fromDef, rule);
        if (result != null) e.setResult(result);
    }

    // Finalize on pickup (Spigot-safe, like your anvil click handler)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmithingResultClick(InventoryClickEvent e) {
        if (!(e.getInventory() instanceof SmithingInventory inv)) return;
        if (e.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (e.getRawSlot() != 3) return; // smithing result slot index

        ItemStack template = inv.getInputTemplate();
        ItemStack base     = inv.getInputEquipment();
        ItemStack addition = inv.getInputMineral();
        ItemStack result   = e.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;

        if (!isKnife(base)) return;
        String baseId = knifeIdOf(base);
        if (baseId == null) return;
        KnifeDefinition fromDef = provider.get(baseId);
        if (fromDef == null || fromDef.smithing() == null) return;

        SmithingRecipe rule = fromDef.smithing();
        if (!templateMatches(rule.templateKey(), template)) return;
        if (addition == null || addition.getType() != rule.addition()) return;

        ItemStack fixed = makeUpgrade(base, fromDef, rule);
        if (fixed != null) e.setCurrentItem(fixed);
    }
}

