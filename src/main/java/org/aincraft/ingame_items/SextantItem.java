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

public final class SextantItem {
    public static final String KEY_SEXTANT = "sextant_item";

    private final Plugin plugin;
    private final NamespacedKey pdcKey;

    public SextantItem(Plugin plugin) {
        this.plugin = plugin;
        this.pdcKey = new NamespacedKey(plugin, KEY_SEXTANT);
    }

    public ItemStack create() {
        // Pick any base; SPYGLASS feels on-theme
        ItemStack stack = new ItemStack(Material.KNOWLEDGE_BOOK, 1);
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text("Sextant", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("Used by the pioneers to read", NamedTextColor.GRAY),
                Component.text("the heavens from the earth.", NamedTextColor.GRAY)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        // NEW in 1.21: bind the item model directly (no CustomModelData)
        meta.setItemModel(NamespacedKey.fromString("longhardfish:items/sextant"));

        // Tag so our listener can identify it
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(pdcKey, PersistentDataType.BYTE, (byte) 1);

        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isSextant(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer()
                .has(pdcKey, PersistentDataType.BYTE);
    }
}