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

public final class ScarabBait {
    public static final String ID = "scarab";

    public static ItemStack create(Plugin plugin, int amount) {
        ItemStack s = new ItemStack(Material.POISONOUS_POTATO, Math.max(1, amount));
        ItemMeta m = s.getItemMeta();
        m.displayName(Component.text("Scarab", NamedTextColor.AQUA));
        m.lore(List.of(
                Component.text("A type of bait found when", NamedTextColor.GRAY),
                Component.text("scavenging around dead bushes", NamedTextColor.GRAY)
        ));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        m.setItemModel(NamespacedKey.fromString("longhardfish:bait/scarab"));
        s.setItemMeta(m);
        BaitKeys.setBaitId(plugin, s, ID);
        return s;
    }

    public static void registerInto(Plugin plugin) {
        BaitRegistry.register(new BaitRegistry.BaitDefinition(
                ID,
                amt -> create(plugin, amt),
                java.util.List.of(),
                "Scarab",     // singular
                "Scarabs"    // plural
        ));
    }
}
