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

public final class WatchItem {
    public static final String KEY = "watch_item";

    private final NamespacedKey pdcKey;

    public WatchItem(Plugin plugin) {
        this.pdcKey = new NamespacedKey(plugin, KEY);
    }

    public ItemStack create() {
        ItemStack stack = new ItemStack(Material.KNOWLEDGE_BOOK, 1);
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text("Watch", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("Clocks are neat and all,", NamedTextColor.GRAY),
                Component.text("but nothing beats the cool", NamedTextColor.GRAY),
                Component.text("shimmer of an analog watch.", NamedTextColor.GRAY)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        // Resource-pack model: assets/longhardfish/models/item/watch.json
        meta.setItemModel(NamespacedKey.fromString("longhardfish:items/watch"));

        // Tag for listener
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(pdcKey, PersistentDataType.BYTE, (byte) 1);

        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isWatch(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer()
                .has(pdcKey, PersistentDataType.BYTE);
    }
}
