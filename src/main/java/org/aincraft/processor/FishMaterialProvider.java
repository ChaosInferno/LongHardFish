package org.aincraft.processor;

import org.bukkit.inventory.ItemStack;

/** Maps a fish key to an output material (e.g., Fish Fillet). */
public interface FishMaterialProvider {
    /**
     * @param fishKey namespaced fish id, e.g. "longhardfish:clownfish"
     * @param count   number of fish being processed (stack-aware)
     * @return an ItemStack representing output; may be null to skip this fish
     */
    ItemStack materialFor(String fishKey, int count);
}
