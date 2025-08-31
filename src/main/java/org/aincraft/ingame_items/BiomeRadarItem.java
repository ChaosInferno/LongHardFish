package org.aincraft.ingame_items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class BiomeRadarItem {
    public static final String KEY = "biome_radar_item";

    private final NamespacedKey pdcKey;

    public BiomeRadarItem(Plugin plugin) {
        this.pdcKey = new NamespacedKey(plugin, KEY);
    }

    public ItemStack create() {
        ItemStack stack = new ItemStack(Material.COMPASS, 1); // base doesn’t matter; model replaces it
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text("Biome Radar", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("It seems to use some sort", NamedTextColor.GRAY),
                Component.text("sea to sky '5G' technology.", NamedTextColor.GRAY),
                Component.text("Is it even safe to use?", NamedTextColor.GRAY)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        // Point this at your resource pack model: assets/longhardfish/models/item/biome_radar.json
        meta.setItemModel(NamespacedKey.fromString("longhardfish:items/biome_radar"));

        // PDC mark
        meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.BYTE, (byte)1);

        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isBiomeRadar(ItemStack stack) {
        return stack != null
                && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(pdcKey, PersistentDataType.BYTE);
    }
}
