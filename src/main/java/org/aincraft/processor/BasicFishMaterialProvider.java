package org.aincraft.processor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Default provider: 1 Fish -> 1 "Fish Fillet" (COD).
 * Maintains backward compatibility with single-item output.
 */
public final class BasicFishMaterialProvider implements FishMaterialProvider {
    @Override
    public List<ItemStack> materialFor(String fishKey, int count) {
        List<ItemStack> results = new ArrayList<>();

        if (fishKey == null || fishKey.isBlank() || count <= 0) {
            return results;
        }

        // Simple default: COD named "Fish Fillet"
        int perFish = 1; // customize per fish if needed
        int total = Math.min(64, Math.max(1, perFish * count)); // cap to stack size

        ItemStack fillet = new ItemStack(Material.COD, total);
        ItemMeta meta = fillet.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Fish Fillet")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            fillet.setItemMeta(meta);
        }

        results.add(fillet);
        return results;
    }
}
