package org.aincraft.processor;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Maps a fish key to output materials (e.g., Fish Fillets, Fish Oil, etc.). */
public interface FishMaterialProvider {
    /**
     * @param fishKey namespaced fish id, e.g. "longhardfish:clownfish"
     * @param count   number of fish being processed (stack-aware)
     * @return a list of ItemStacks representing outputs; may be empty to skip this fish
     */
    List<ItemStack> materialFor(String fishKey, int count);
}
