package org.aincraft.list;

import net.kyori.adventure.text.Component;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import org.aincraft.container.FishModel;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class FishCreator {

    private final JavaPlugin plugin;
    private final Map<NamespacedKey, FishModel> modelMap;

    public FishCreator(JavaPlugin plugin, Map<NamespacedKey, FishModel> modelMap) {
        this.plugin = plugin;
        this.modelMap = modelMap;
    }

    public ItemStack createFishItem(NamespacedKey key) {
        FishModel fish = modelMap.get(key);
        if (fish == null) return null;

        ItemStack item = new ItemStack(Material.COD);

        item.setData(DataComponentTypes.ITEM_MODEL, Key.key(key.getNamespace(), key.getKey()));

        item.setData(DataComponentTypes.ITEM_NAME, Component.text(fish.getName()));
        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(Component.text(fish.getDescription()))));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "fish_id"),
                    PersistentDataType.STRING,
                    key.toString() // e.g. "longhardfish:salmon_common"
            );
            item.setItemMeta(meta);
        }

        return item;
    }
}
