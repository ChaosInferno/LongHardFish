// src/main/java/org/aincraft/knives/KnifeEnchantListener.java
package org.aincraft.knives;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.meta.Damageable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class KnifeEnchantListener implements Listener {
    private final JavaPlugin plugin;
    private final KnifeProvider provider;

    // Cache what we decided to show per player so clicks can apply that exact enchant
    private static final class Offered {
        final Enchantment ench; final int level;
        Offered(Enchantment e, int lvl) { this.ench = e; this.level = lvl; }
    }
    private final Map<UUID, Offered[]> pending = new ConcurrentHashMap<>();

    public KnifeEnchantListener(JavaPlugin plugin, KnifeProvider provider) {
        this.plugin = plugin;
        this.provider = provider;
    }

    private boolean isKnife(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        var pdc = it.getItemMeta().getPersistentDataContainer();
        Byte tag = pdc.get(org.aincraft.items.Keys.knife(plugin),
                org.bukkit.persistence.PersistentDataType.BYTE);
        return tag != null && tag == (byte)1;
    }

    private KnifeDefinition defOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(
                org.aincraft.items.Keys.knifeId(plugin),
                org.bukkit.persistence.PersistentDataType.STRING
        );
        return (id == null) ? null : provider.get(id);
    }

    /** All Bukkit-known enchants (works widely). */
    private static List<Enchantment> allEnchants() {
        return Arrays.asList(Enchantment.values());
    }

    /** Allowed, discoverable, applicable enchants for this knife+item. */
    private static List<Enchantment> allowedFor(KnifeDefinition def, ItemStack item) {
        return allEnchants().stream()
                .filter(Objects::nonNull)
                .filter(e -> {
                    try { return !e.isTreasure() && e.isDiscoverable(); }
                    catch (Throwable t) { return true; } // older APIs
                })
                .filter(e -> e.canEnchantItem(item))
                .filter(def::isEnchantAllowed)
                .collect(Collectors.toList());
    }

    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent e) {
        ItemStack item = e.getItem();
        if (!isKnife(item)) return;

        KnifeDefinition def = defOf(item);
        if (def == null) return;

        var offers = e.getOffers(); // 3 slots
        if (offers == null || offers.length == 0) return;

        // Build the pool we’re allowed to show
        List<Enchantment> allowed = allowedFor(def, item);
        // If nothing allowed (rare), leave vanilla alone so UI still works; EnchantItemEvent will still guard.
        if (allowed.isEmpty()) {
            pending.remove(e.getEnchanter().getUniqueId());
            return;
        }

        // Helper to clamp within enchant’s legal level bounds
        java.util.function.BiFunction<Enchantment, Integer, Integer> clamp = (ench, lvl) -> {
            int min = 1;
            try { min = Math.max(ench.getStartLevel(), 1); } catch (Throwable ignored) {}
            int max = ench.getMaxLevel();
            if (lvl <= 0) lvl = min;
            if (lvl < min) lvl = min;
            if (lvl > max) lvl = max;
            return lvl;
        };

        Offered[] shown = new Offered[3];

        for (int i = 0; i < offers.length; i++) {
            var off = offers[i];
            if (off == null) continue;

            Enchantment curr = off.getEnchantment();
            boolean ok = false;
            if (curr != null) {
                boolean treasure = false, discoverable = true;
                try { treasure = curr.isTreasure(); discoverable = curr.isDiscoverable(); } catch (Throwable ignored) {}
                ok = !treasure && discoverable && curr.canEnchantItem(item) && def.isEnchantAllowed(curr);
            }

            if (ok) {
                int lvl = clamp.apply(curr, off.getEnchantmentLevel());
                off.setEnchantmentLevel(lvl);
                shown[i] = new Offered(curr, lvl);
                continue;
            }

            // Replace with an allowed fallback; keep the same rolled level (clamped)
            Enchantment repl = allowed.get(i % allowed.size());
            int lvl = clamp.apply(repl, off.getEnchantmentLevel());
            off.setEnchantment(repl);           // NEVER null
            off.setEnchantmentLevel(lvl);
            shown[i] = new Offered(repl, lvl);
        }

        pending.put(e.getEnchanter().getUniqueId(), shown);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        if (!(e.getInventory() instanceof CraftingInventory inv)) return;

        // Collect knives in the matrix (vanilla “repair” is 2 items of same type)
        ItemStack[] matrix = inv.getMatrix();
        ItemStack a = null, b = null;
        for (ItemStack it : matrix) {
            if (isKnife(it)) {
                if (a == null) a = it;
                else if (b == null) b = it;
                else { // more than 2 knives → not a simple repair; let vanilla handle or block
                    inv.setResult(null);
                    return;
                }
            }
        }
        if (a == null || b == null) return; // not a 2-knife repair

        // Enforce same knife id
        String idA = knifeIdOf(a), idB = knifeIdOf(b);
        if (idA == null || idB == null || !idA.equals(idB)) {
            inv.setResult(null);
            return;
        }

        // Build a custom repair result that PRESERVES properties from 'a'
        ItemStack res = a.clone();
        res.setAmount(1);

        ItemMeta rm = res.getItemMeta();
        if (rm instanceof Damageable rd &&
                a.getItemMeta() instanceof Damageable ad &&
                b.getItemMeta() instanceof Damageable bd) {

            int max;
            // Paper 1.21 getMaxDamage may require a check; fall back safely
            try { max = rd.getMaxDamage(); }
            catch (Throwable t) { max = res.getType().getMaxDurability(); }

            int remA = Math.max(0, max - ad.getDamage());
            int remB = Math.max(0, max - bd.getDamage());
            int bonus = Math.max(1, (int)Math.floor(max * 0.05)); // vanilla +5%

            int newRemaining = Math.min(max, remA + remB + bonus);
            int newDamage = Math.max(0, max - newRemaining);

            rd.setDamage(newDamage);
            res.setItemMeta(rm);
        }

        // Keep enchantments from both (inventory repair normally removes them; we preserve)
        res.addEnchantments(a.getEnchantments());
        res.addEnchantments(b.getEnchantments());

        inv.setResult(res);
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent e) {
        ItemStack item = e.getItem();
        if (!isKnife(item)) return;

        KnifeDefinition def = defOf(item);
        if (def == null) return;

        Offered[] shown = pending.get(e.getEnchanter().getUniqueId());
        if (shown == null) {
            // No cache (e.g., no changes were made). Just strip disallowed as a safety net.
            e.getEnchantsToAdd().entrySet().removeIf(en -> !def.isEnchantAllowed(en.getKey()));
            return;
        }

        int idx = Math.max(0, Math.min(2, e.whichButton())); // 0..2
        Offered choice = shown[idx];
        if (choice == null || choice.ench == null) {
            // Shouldn’t happen; fall back to safety net
            e.getEnchantsToAdd().entrySet().removeIf(en -> !def.isEnchantAllowed(en.getKey()));
            pending.remove(e.getEnchanter().getUniqueId());
            return;
        }

        // Force the enchant we displayed
        e.getEnchantsToAdd().clear();
        e.getEnchantsToAdd().put(choice.ench, choice.level);

        // Cleanup
        pending.remove(e.getEnchanter().getUniqueId());
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack left  = e.getInventory().getFirstItem();
        ItemStack right = e.getInventory().getSecondItem();

        if (left == null || left.getType().isAir()) return;
        if (!isKnife(left)) return;

        // If the right side is also a knife, enforce “same knife id or no result”
        if (isKnife(right)) {
            String idL = knifeIdOf(left);
            String idR = knifeIdOf(right);
            if (idL == null || idR == null || !idL.equals(idR)) {
                e.setResult(null);  // block cross-type combination
                return;
            }
        }

        ItemStack result = e.getResult();
        if (result == null || result.getType().isAir()) return;

        // Ensure result inherits our knife properties (name/model/PDC/attributes)
        // by copying from the LEFT item (the one on the first slot).
        ItemStack fixed = left.clone();
        fixed.setAmount(1);

        // Keep the anvil’s computed repair damage if present
        var rm = fixed.getItemMeta();
        var em = result.getItemMeta();
        if (rm instanceof org.bukkit.inventory.meta.Damageable rd &&
                em instanceof org.bukkit.inventory.meta.Damageable ed) {
            // Use the anvil’s new damage value
            try { rd.setDamage(ed.getDamage()); } catch (Throwable ignored) {}
            fixed.setItemMeta(rm);
        }

        // Also carry over enchants the anvil decided to add/upgrade
        fixed.addEnchantments(result.getEnchantments());

        e.setResult(fixed);
    }

    private String knifeIdOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(
                org.aincraft.items.Keys.knifeId(plugin),
                org.bukkit.persistence.PersistentDataType.STRING
        );
    }
}