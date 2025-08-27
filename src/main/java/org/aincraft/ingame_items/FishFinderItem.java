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

public final class FishFinderItem {
    public static final String KEY = "fish_finder_item";

    private final NamespacedKey pdcKey;

    public FishFinderItem(Plugin plugin) {
        this.pdcKey = new NamespacedKey(plugin, KEY);
    }

    public ItemStack create() {
        // Pick a base that feels “gadgety”
        ItemStack stack = new ItemStack(Material.KNOWLEDGE_BOOK, 1);
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text("Fish Finder", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("They said I was mad", NamedTextColor.GRAY),
                Component.text("to combine all of them.", NamedTextColor.GRAY),
                Component.text("This'll show them I am.", NamedTextColor.GRAY)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        // Bind your RP model: assets/longhardfish/models/item/fish_finder.json
        meta.setItemModel(NamespacedKey.fromString("longhardfish:items/fish_finder"));

        // Tag so our listener can recognize it
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(pdcKey, PersistentDataType.BYTE, (byte) 1);

        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isFishFinder(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer()
                .has(pdcKey, PersistentDataType.BYTE);
    }
}
