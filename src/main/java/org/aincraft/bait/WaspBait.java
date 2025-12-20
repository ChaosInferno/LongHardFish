package org.aincraft.bait;

import org.aincraft.items.BaitKeys;
import org.aincraft.items.BaitRegistry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class WaspBait {
    public static final String ID = "wasp";

    public static ItemStack create(Plugin plugin, int amount) {
        ItemStack s = new ItemStack(Material.POISONOUS_POTATO, Math.max(1, amount));
        ItemMeta m = s.getItemMeta();
        m.displayName(Component.text("Wasp", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Common", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        // Adjust to your RP path if needed
        m.setItemModel(NamespacedKey.fromString("longhardfish:bait/wasp"));
        s.setItemMeta(m);

        BaitKeys.setBaitId(plugin, s, ID);
        return s;
    }

    /** Register factory only; drops handled by shared forage tables. */
    public static void registerInto(Plugin plugin) {
        BaitRegistry.register(new BaitRegistry.BaitDefinition(
                ID,
                amt -> create(plugin, amt),
                java.util.List.of(),
                "Wasp",     // singular
                "Wasps"    // plural
        ));
    }
}

