package org.aincraft.listener;

import org.aincraft.gui.FishDexFishSelector;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class FishDexGuiListener implements Listener {

    private final FishDexFishSelector selector;

    public FishDexGuiListener(FishDexFishSelector selector) {
        this.selector = selector;
    }

    private static FishDexFishSelector.DexHolder dex(Inventory inv) {
        InventoryHolder h = inv.getHolder();
        return (h instanceof FishDexFishSelector.DexHolder) ? (FishDexFishSelector.DexHolder) h : null;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        FishDexFishSelector.DexHolder holder = dex(e.getView().getTopInventory());
        if (holder == null) return;

        // Blanket-cancel ANY clicks while the Dex is open (blocks hotbar & player-inventory too)
        e.setCancelled(true);

        // Only react to clicks on the Dex top inventory (the 35-slot grid etc.)
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) {
            return; // bottom inv/hotbar: do nothing (already cancelled)
        }

        // If the clicked GUI slot corresponds to a fish, reopen the Dex for that fish
        int raw = e.getRawSlot();
        NamespacedKey clickedFish = holder.slotToFish.get(raw);
        if (clickedFish != null) {
            Player p = (Player) e.getWhoClicked();
            selector.open(p, clickedFish);
        }

        // Everything remains cancelled so no items move
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        FishDexFishSelector.DexHolder holder = dex(e.getView().getTopInventory());
        if (holder == null) return;

        // Cancel drags if ANY part of the drag touches the top inventory
        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) { // slot index in the top inventory
                e.setCancelled(true);
                return;
            }
        }

        // Also cancel drags that only affect the bottom inventory while Dex is open
        e.setCancelled(true);
    }

    @EventHandler
    public void onCreative(InventoryCreativeEvent e) {
        // In creative, players can do weird things; just block if Dex is open
        FishDexFishSelector.DexHolder holder = dex(e.getView().getTopInventory());
        if (holder != null) e.setCancelled(true);
    }
}
