// org/aincraft/processor/BasicFishMaterialProvider.java
package org.aincraft.processor;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Default: 1 Fish → 1 “Fish Fillet” (paper). Adjust to your liking. */
public final class BasicFishMaterialProvider implements FishMaterialProvider {
    @Override
    public ItemStack materialFor(String fishKey, int count) {
        if (fishKey == null || fishKey.isBlank() || count <= 0) return null;

        // Simple default: PAPER named "Fish Fillet"
        int perFish = 1; // customize per fish if you want
        int total = Math.min(64, Math.max(1, perFish * count)); // cap to stack size

        ItemStack fillet = new ItemStack(Material.COD, total);
        ItemMeta meta = fillet.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Fish Fillet");
        fillet.setItemMeta(meta);
        return fillet;
    }
}
