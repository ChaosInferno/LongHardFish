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

public final class TackleBoxItem {
    public static final String KEY = "tacklebox_item";

    private final NamespacedKey pdcKey;

    public TackleBoxItem(Plugin plugin) {
        this.pdcKey = new NamespacedKey(plugin, KEY);
    }

    public ItemStack create() {
        // Base material can be anything; BARREL feels thematic and avoids some chest placement expectations
        ItemStack stack = new ItemStack(Material.KNOWLEDGE_BOOK, 1);
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text("Tacklebox", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("A simple tacklebox that doubles as a cooler.", NamedTextColor.GRAY)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        // Direct model binding (1.21+ item model component)
        // Ensure you have assets/longhardfish/models/item/tacklebox.json in your RP
        meta.setItemModel(NamespacedKey.fromString("longhardfish:items/tacklebox"));

        // Tag with PDC so the listener can identify it
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(pdcKey, PersistentDataType.BYTE, (byte) 1);

        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isTackleBox(ItemStack stack) {
        return stack != null
                && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(pdcKey, PersistentDataType.BYTE);
    }
}
