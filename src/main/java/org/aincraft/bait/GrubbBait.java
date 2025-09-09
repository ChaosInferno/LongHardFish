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

public final class GrubbBait {

    public static final String ID = "grubb";

    public static ItemStack create(Plugin plugin, int amount) {
        ItemStack s = new ItemStack(Material.POISONOUS_POTATO, Math.max(1, amount));
        ItemMeta m = s.getItemMeta();
        m.displayName(Component.text("Grubb", NamedTextColor.AQUA));
        m.lore(List.of(
                Component.text("A type of bait found when", NamedTextColor.GRAY),
                Component.text("tilling along the soil", NamedTextColor.GRAY)
        ));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        m.setItemModel(NamespacedKey.fromString("longhardfish:bait/grubb"));

        s.setItemMeta(m);
        BaitKeys.setBaitId(plugin, s, ID);
        return s;
    }

    /**
     * Register ONLY the item factory; no per-bait foraging rules here.
     */
    public static void registerInto(Plugin plugin) {
        BaitRegistry.register(new BaitRegistry.BaitDefinition(
                ID,
                amt -> create(plugin, amt),
                java.util.List.of(),
                "Grubb",      // singular
                "Grubbs"      // plural (your stylized spelling)
        ));
    }
}
