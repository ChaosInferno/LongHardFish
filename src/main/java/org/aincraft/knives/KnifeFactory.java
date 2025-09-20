// src/main/java/org/aincraft/knives/KnifeFactory.java
package org.aincraft.knives;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KnifeFactory {
    private KnifeFactory() {}

    /** MAIN_HAND on modern servers; fallback to HAND on older ones. */
    private static AttributeModifier modMainHand(String name, double amount, AttributeModifier.Operation op) {
        // Prefer the legacy, single-slot ctor: (…, EquipmentSlot)
        try {
            var ctor = AttributeModifier.class.getConstructor(
                    UUID.class, String.class, double.class,
                    AttributeModifier.Operation.class, org.bukkit.inventory.EquipmentSlot.class
            );
            return ctor.newInstance(UUID.randomUUID(), name, amount, op, org.bukkit.inventory.EquipmentSlot.HAND);
        } catch (Throwable ignore) {
            // Fallback: newer group-based ctor: (…, EquipmentSlotGroup)
            try {
                var ctor = AttributeModifier.class.getConstructor(
                        UUID.class, String.class, double.class,
                        AttributeModifier.Operation.class, org.bukkit.inventory.EquipmentSlotGroup.class
                );
                Object main;
                try {
                    main = org.bukkit.inventory.EquipmentSlotGroup.class.getField("MAIN_HAND").get(null);
                } catch (NoSuchFieldException noMain) {
                    main = org.bukkit.inventory.EquipmentSlotGroup.class.getField("HAND").get(null);
                }
                return ctor.newInstance(UUID.randomUUID(), name, amount, op, main);
            } catch (Throwable ignore2) {
                // Last resort (shouldn’t be needed)
                return new AttributeModifier(UUID.randomUUID(), name, amount, op);
            }
        }
    }



    /** Resolve Attribute across: legacy enum → "attack_*" → "generic.attack_*" → registry scan. */
    private static Attribute resolveAttr(String legacyConst, String... candidates) {
        // 1) legacy enum (pre-1.21)
        try {
            var f = Attribute.class.getField(legacyConst);
            Object v = f.get(null);
            if (v instanceof Attribute a) return a;
        } catch (Throwable ignored) {}

        // 2) direct candidate keys (1.21+ prefers "attack_damage"/"attack_speed")
        for (String key : candidates) {
            try {
                Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
                if (a != null) return a;
            } catch (Throwable ignored) {}
        }

        // 3) last-resort: scan the registry for a suffix match
        try {
            for (Attribute a : Registry.ATTRIBUTE) {
                var k = Registry.ATTRIBUTE.getKey(a);
                if (k != null) {
                    String path = k.getKey(); // e.g. "attack_damage"
                    for (String want : candidates) {
                        String wantPath = want.contains(":") ? want.substring(want.indexOf(':') + 1) : want;
                        if (path.equalsIgnoreCase(wantPath) || path.endsWith(wantPath)) return a;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static String fmt(Double v) {
        if (v == null) return null;
        if (Math.abs(v - Math.rint(v)) < 1e-9) return Integer.toString((int) Math.rint(v));
        return new DecimalFormat("#.##").format(v);
    }

    public static ItemStack create(org.bukkit.plugin.java.JavaPlugin plugin, KnifeDefinition def) {
        ItemStack item = new ItemStack(def.base());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Name (supports &-codes), non-italic
        String rawName = ChatColor.translateAlternateColorCodes('&', def.displayName());
        Component niceName = LegacyComponentSerializer.legacySection()
                .deserialize(rawName)
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(niceName);

        // Model
        meta.setItemModel(def.modelKey());

        // Per-item durability (Paper 1.21+)
        if (meta instanceof Damageable dmg && def.durability() != null) {
            dmg.setMaxDamage(def.durability());
            dmg.setDamage(0);
        }

        if (def.enchantability() != null) {
            try {
                // Paper method name on 1.21+: setEnchantmentValue(int)
                var m = ItemMeta.class.getMethod("setEnchantmentValue", int.class);
                m.invoke(meta, def.enchantability());
            } catch (NoSuchMethodException ignored) {
                // Older API – cannot set per-item enchantability; falls back to Material’s value.
            } catch (Throwable t) {
                plugin.getLogger().warning("[Knives] Failed to set enchantability: " + t.getMessage());
            }
        }

        Multimap<Attribute, AttributeModifier> mods = HashMultimap.create();
        boolean any = false;

        if (def.attackDamage() != null) {
            double amount = def.attackDamage() - 1.0;
            Attribute a = resolveAttr(
                    "GENERIC_ATTACK_DAMAGE",
                    "attack_damage",               // 1.21+
                    "generic.attack_damage"        // older naming
            );
            if (a != null) {
                mods.put(a, modMainHand("lhf_knife_damage", amount, AttributeModifier.Operation.ADD_NUMBER));
                any = true;
            }
        }
        if (def.attackSpeed() != null) {
            double amount = def.attackSpeed() - 4.0;
            Attribute a = resolveAttr(
                    "GENERIC_ATTACK_SPEED",
                    "attack_speed",                // 1.21+
                    "generic.attack_speed"         // older naming
            );
            if (a != null) {
                mods.put(a, modMainHand("lhf_knife_speed", amount, AttributeModifier.Operation.ADD_NUMBER));
                any = true;
            }
        }
        if (any) {
            // Replace vanilla sword defaults (6 / 1.6) entirely
            meta.setAttributeModifiers(mods);
        }

        // ---- Presentation: hide vanilla gray block; show our DARK_GREEN, non-italic lines ----
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(" ").decoration(TextDecoration.ITALIC, false)); // spacer
        lore.add(Component.text("When in Main Hand:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

        String dmgTxt = fmt(def.attackDamage());
        if (dmgTxt != null) {
            lore.add(Component.text(" " + dmgTxt + " Attack Damage", NamedTextColor.DARK_GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        }
        String spdTxt = fmt(def.attackSpeed());
        if (spdTxt != null) {
            lore.add(Component.text(" " + spdTxt + " Attack Speed", NamedTextColor.DARK_GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        // Identity tags for your UI and for later lookups during repairs
        meta.getPersistentDataContainer().set(
                org.aincraft.items.Keys.knife(plugin),
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1
        );
        meta.getPersistentDataContainer().set(
                org.aincraft.items.Keys.knifeId(plugin),
                org.bukkit.persistence.PersistentDataType.STRING,
                def.id()
        );

        var pdc = meta.getPersistentDataContainer();
        pdc.set(org.aincraft.items.Keys.knife(plugin),   org.bukkit.persistence.PersistentDataType.BYTE,   (byte)1);
        pdc.set(org.aincraft.items.Keys.knifeId(plugin), org.bukkit.persistence.PersistentDataType.STRING, def.id());

        Integer max = def.durability(); // from knives.yml
        if (max != null && max > 0) {
            pdc.set(org.aincraft.items.Keys.knifeMax(plugin),
                    org.bukkit.persistence.PersistentDataType.INTEGER, max);
            pdc.set(org.aincraft.items.Keys.knifeDurability(plugin),
                    org.bukkit.persistence.PersistentDataType.INTEGER, max);
        }

        item.setItemMeta(meta);
        return item;
    }

    public static void updateDurabilityLore(org.bukkit.plugin.java.JavaPlugin plugin, ItemStack item) {
        // no-op (we’re using vanilla bar + green lore)
    }

    // Ensure the crafted/preview knife has the same visible/meta state as a freshly-created one.
    public static void normalizeAfterRepair(
            org.bukkit.plugin.java.JavaPlugin plugin,
            ItemStack item,
            KnifeDefinition def
    ) {
        if (item == null || !item.hasItemMeta() || def == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // ---- name & model ----
        String rawName = ChatColor.translateAlternateColorCodes('&', def.displayName());
        net.kyori.adventure.text.Component niceName =
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize(rawName)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        meta.displayName(niceName);
        if (def.modelKey() != null) meta.setItemModel(def.modelKey());

        // ---- attributes (same logic you use in create) ----
        com.google.common.collect.Multimap<Attribute, AttributeModifier> mods =
                com.google.common.collect.HashMultimap.create();

        if (def.attackDamage() != null) {
            double amount = def.attackDamage() - 1.0; // final = 1.0 + amount
            Attribute a = resolveAttr("GENERIC_ATTACK_DAMAGE", "generic.attack_damage");
            if (a != null) mods.put(a, modMainHand("lhf_knife_damage", amount, AttributeModifier.Operation.ADD_NUMBER));
        }
        if (def.attackSpeed() != null) {
            double amount = def.attackSpeed() - 4.0; // final = 4.0 + amount
            Attribute a = resolveAttr("GENERIC_ATTACK_SPEED", "generic.attack_speed");
            if (a != null) mods.put(a, modMainHand("lhf_knife_speed", amount, AttributeModifier.Operation.ADD_NUMBER));
        }
        if (!mods.isEmpty()) meta.setAttributeModifiers(mods);

        // ---- green “vanilla-like” lore + hide vanilla block ----
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text(" ")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(net.kyori.adventure.text.Component.text("When in Main Hand:", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        if (def.attackDamage() != null) {
            lore.add(net.kyori.adventure.text.Component.text(" " + (def.attackDamage() % 1 == 0 ? String.valueOf(def.attackDamage().intValue()) : String.valueOf(def.attackDamage()))
                            + " Attack Damage", net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        if (def.attackSpeed() != null) {
            lore.add(net.kyori.adventure.text.Component.text(" " + (def.attackSpeed() % 1 == 0 ? String.valueOf(def.attackSpeed().intValue()) : String.valueOf(def.attackSpeed()))
                            + " Attack Speed", net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        // ---- knife identity PDC ----
        var pdc = meta.getPersistentDataContainer();
        pdc.set(org.aincraft.items.Keys.knife(plugin), org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);
        pdc.set(org.aincraft.items.Keys.knifeId(plugin), org.bukkit.persistence.PersistentDataType.STRING, def.id());

        item.setItemMeta(meta);
    }

}
