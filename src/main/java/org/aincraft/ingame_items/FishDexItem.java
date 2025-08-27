package org.aincraft.ingame_items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class FishDexItem {

    public static final String KEY_FISHDEX = "fishdex_item"; // PDC key id

    private final Plugin plugin;
    private final NamespacedKey fishdexKey;

    public FishDexItem(Plugin plugin) {
        this.plugin = plugin;
        this.fishdexKey = new NamespacedKey(plugin, KEY_FISHDEX);
    }

    /** Create the FishDex itemstack. Change the material if you prefer another icon. */
    public ItemStack create() {
        ItemStack stack = new ItemStack(Material.KNOWLEDGE_BOOK, 1);
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text("FishDex", NamedTextColor.AQUA));
        meta.lore(java.util.List.of(
                Component.text("A digital catalogue for all the", NamedTextColor.GRAY),
                Component.text("fish you are destined to catch.", NamedTextColor.GRAY)));

        // ← NEW: point directly to your model id
        meta.setItemModel(NamespacedKey.fromString("longhardfish:items/fishdex"));

        // keep your PDC tag so the listener recognizes it
        meta.getPersistentDataContainer().set(fishdexKey, PersistentDataType.BYTE, (byte) 1);

        stack.setItemMeta(meta);
        return stack;
    }

    /** True if the stack is our FishDex item. */
    public boolean isFishDex(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(fishdexKey, PersistentDataType.BYTE);
    }
}

