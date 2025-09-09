package org.aincraft.rods;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class RodUtil {
    private RodUtil() {}
    public static String getRodId(Plugin plugin, ItemStack rod) {
        if (rod == null || rod.getItemMeta() == null) return null;
        ItemMeta m = rod.getItemMeta();
        return m.getPersistentDataContainer().get(RodKeys.rodId(plugin), PersistentDataType.STRING);
    }
}