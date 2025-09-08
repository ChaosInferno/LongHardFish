// org/aincraft/ingame_items/OmegaFishFinderItem.java
package org.aincraft.ingame_items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Creates the omega_fish_finder item and tags it so we can detect it later. */
public final class OmegaFishFinderItem {
    public static final String NS = "longhardfish";
    public static final String MODEL = "items/omega_fish_finder"; // use any model you like
    public static final String ID = "omega_fish_finder";

    private final JavaPlugin plugin;
    private final NamespacedKey tagKey;

    public OmegaFishFinderItem(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tagKey = new NamespacedKey(plugin, ID);
    }

    public NamespacedKey tagKey() { return tagKey; }

    public ItemStack create() {
        ItemStack it = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = it.getItemMeta();

        meta.displayName(Component.text("Omega Fish Finder ZX", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("A tool of the gods not meant", NamedTextColor.GRAY),
                Component.text("for mortal men. If you have", NamedTextColor.GRAY),
                Component.text("this please tell an admin.", NamedTextColor.GRAY)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        // (Optional) resource pack model
        try {
            meta.setItemModel(new NamespacedKey(NS, MODEL));
        } catch (Throwable ignored) {}

        // Tag it so we can detect later
        meta.getPersistentDataContainer().set(tagKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);
        it.setItemMeta(meta);
        return it;
    }

    /** Utility: true if this item is the omega_fish_finder. */
    public boolean isOmega(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        Byte b = stack.getItemMeta().getPersistentDataContainer()
                .get(tagKey, org.bukkit.persistence.PersistentDataType.BYTE);
        return b != null && b == (byte)1;
    }
}

