package org.aincraft.ingame_items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class WeatherRadioItem {
    public static final String KEY_WEATHER_RADIO = "weather_radio_item";

    private final NamespacedKey pdcKey;

    public WeatherRadioItem(Plugin plugin) {
        this.pdcKey = new NamespacedKey(plugin, KEY_WEATHER_RADIO);
    }

    public ItemStack create() {
        ItemStack stack = new ItemStack(Material.KNOWLEDGE_BOOK, 1);
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text("Weather Radio", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("Always tuned to the weather", NamedTextColor.GRAY),
                Component.text("station. How does Willy always", NamedTextColor.GRAY),
                Component.text("have time to host it?", NamedTextColor.GRAY)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        // Bind the model from your resource pack: assets/longhardfish/models/item/weather_radio.json
        meta.setItemModel(NamespacedKey.fromString("longhardfish:items/weather_radio"));

        // Tag so our listener recognizes it
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(pdcKey, PersistentDataType.BYTE, (byte) 1);

        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isWeatherRadio(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer()
                .has(pdcKey, PersistentDataType.BYTE);
    }
}
