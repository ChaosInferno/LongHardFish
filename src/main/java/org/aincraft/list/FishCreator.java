package org.aincraft.list;

import net.kyori.adventure.text.Component;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.TextComponent;
import org.aincraft.container.FishModel;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FishCreator {

    private final Map<NamespacedKey, FishModel> modelMap;

    public FishCreator(Map<NamespacedKey, FishModel> modelMap) {
        this.modelMap = modelMap;
    }

    /**
     * Creates an ItemStack for the given fish key using the modelMap.
     *
     * @param key The NamespacedKey of the fish
     * @return ItemStack representing the fish item
     */
    public ItemStack createFishItem(NamespacedKey key) {
        FishModel fish = modelMap.get(key);
        if (fish == null) return null;

        ItemStack item = new ItemStack(Material.COD);
        item.setData(DataComponentTypes.ITEM_MODEL, key);

        // Set display name and lore
        TextComponent text = Component.text(fish.getDescription());
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(fish.getName()));
        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(text)));

        return item;
    }
}
