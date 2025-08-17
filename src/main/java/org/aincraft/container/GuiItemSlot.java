package org.aincraft.container;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

public class GuiItemSlot {

    public static ItemStack immovableIcon(Material base,
                                          Key modelKey,
                                          NamespacedKey immovableKey,
                                          boolean hideTooltip) {
        ItemStack item = new ItemStack(base);

        // Custom model (from your resource pack)
        item.setData(DataComponentTypes.ITEM_MODEL, modelKey);

        // No custom name (prevents any text), and tag as immovable
        item.editMeta(m -> {
            m.displayName(null);
            m.getPersistentDataContainer().set(immovableKey, PersistentDataType.BYTE, (byte) 1);
        });

        // Tooltip visibility
        if (hideTooltip) {
            item.setData(
                    DataComponentTypes.TOOLTIP_DISPLAY,
                    TooltipDisplay.tooltipDisplay().hideTooltip(true).build()
            );
        } else {
            // Explicitly remove any tooltip-display override so vanilla tooltip shows
            item.resetData(DataComponentTypes.TOOLTIP_DISPLAY);
        }

        return item;
    }

    public static ItemStack immovableIcon(Material base,
                                          Key modelKey,
                                          NamespacedKey immovableKey) {
        return immovableIcon(base, modelKey, immovableKey, true);
    }

    /* === Chest/any Inventory === */
    public static void putImmovableIcon(Inventory inv,
                                        int slot,
                                        Material base,
                                        Key modelKey,
                                        NamespacedKey immovableKey,
                                        boolean hideTooltip) {
        inv.setItem(slot, immovableIcon(base, modelKey, immovableKey, hideTooltip));
    }

    public static void putImmovableIcon(Inventory inv,
                                        int slot,
                                        Material base,
                                        Key modelKey,
                                        NamespacedKey immovableKey) {
        putImmovableIcon(inv, slot, base, modelKey, immovableKey, true);
    }

    public static void putImmovableIcon(PlayerInventory inv,
                                        int storageSlot,
                                        Material base,
                                        Key modelKey,
                                        NamespacedKey immovableKey,
                                        boolean hideTooltip) {
        if (storageSlot < 0 || storageSlot >= 36) {
            throw new IllegalArgumentException("storageSlot must be in 0..35 (player storage)");
        }
        ItemStack[] storage = inv.getStorageContents();
        storage[storageSlot] = immovableIcon(base, modelKey, immovableKey, hideTooltip);
        inv.setStorageContents(storage);
    }

    public static void putImmovableIcon(PlayerInventory inv,
                                        int storageSlot,
                                        Material base,
                                        Key modelKey,
                                        NamespacedKey immovableKey) {
        putImmovableIcon(inv, storageSlot, base, modelKey, immovableKey, true);
    }

    public static int hotbar(int col0to8) {
        if (col0to8 < 0 || col0to8 > 8) throw new IllegalArgumentException("hotbar col must be 0..8");
        return col0to8; // storage 0..8
    }

    public static int main(int row0to2, int col0to8) {
        if (row0to2 < 0 || row0to2 > 2) throw new IllegalArgumentException("row must be 0..2");
        if (col0to8 < 0 || col0to8 > 8) throw new IllegalArgumentException("col must be 0..8");
        return 9 + row0to2 * 9 + col0to8; // storage 9..35
    }
}
