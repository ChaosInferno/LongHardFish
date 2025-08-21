package org.aincraft.listener;

import org.aincraft.gui.FishDexFishSelector;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;

public final class FishDexGuiListener implements Listener {

    private final FishDexFishSelector selector;

    public FishDexGuiListener(FishDexFishSelector selector) {
        this.selector = selector;
    }

    private static boolean isFishDexTop(Inventory inv) {
        return inv != null && inv.getHolder() instanceof FishDexFishSelector.DexHolder;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!isFishDexTop(top)) return;

        // Always cancel default behavior so nothing moves
        e.setCancelled(true);

        FishDexFishSelector.DexHolder holder = (FishDexFishSelector.DexHolder) top.getHolder();
        Player p = (Player) e.getWhoClicked();

        // Click in the top GUI? (grid fish selection)
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(top)) {
            int slot = e.getSlot();
            Map<Integer, NamespacedKey> map = holder.slotToFish;
            if (map != null && map.containsKey(slot)) {
                NamespacedKey fishId = map.get(slot);
                // reopen focused on clicked fish (page recalculated from its model number)
                Bukkit.getScheduler().runTask(selector.plugin(), () -> selector.open(p, fishId));
            }
            return;
        }

        // Click in the player inventory/hotbar while Dex is open: treat hotbar buttons as nav
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(p.getInventory())) {
            int hotbarIndex = e.getSlot(); // 0..8 for hotbar
            int pageIndex   = holder.pageIndex;
            int pageCount   = holder.pageCount <= 0 ? 1 : holder.pageCount;
            List<NamespacedKey> ordered = holder.ordered;

            // guard
            if (ordered == null || ordered.isEmpty()) return;

            if (hotbarIndex == FishDexFishSelector.HOTBAR_NEXT_1) {
                if (pageIndex < pageCount - 1) {
                    int start = (pageIndex + 1) * 35;
                    NamespacedKey target = ordered.get(Math.min(start, ordered.size() - 1));
                    Bukkit.getScheduler().runTask(selector.plugin(), () -> selector.open(p, target));
                }
                return;
            }
            if (hotbarIndex == FishDexFishSelector.HOTBAR_NEXT_ALL) {
                if (pageIndex < pageCount - 1) {
                    int start = (pageCount - 1) * 35;
                    NamespacedKey target = ordered.get(Math.min(start, ordered.size() - 1));
                    Bukkit.getScheduler().runTask(selector.plugin(), () -> selector.open(p, target));
                }
                return;
            }
            if (hotbarIndex == FishDexFishSelector.HOTBAR_PREV_1) {
                if (pageIndex > 0) {
                    int start = (pageIndex - 1) * 35;
                    NamespacedKey target = ordered.get(Math.min(start, ordered.size() - 1));
                    Bukkit.getScheduler().runTask(selector.plugin(), () -> selector.open(p, target));
                }
                return;
            }
            if (hotbarIndex == FishDexFishSelector.HOTBAR_PREV_ALL) {
                if (pageIndex > 0) {
                    NamespacedKey target = ordered.get(0);
                    Bukkit.getScheduler().runTask(selector.plugin(), () -> selector.open(p, target));
                }
                return;
            }

            // otherwise ignore (but still cancelled so nothing moves)
            return;
        }

        // Block shift-clicks, collects, swaps, etc that could move items
        switch (e.getAction()) {
            case MOVE_TO_OTHER_INVENTORY:
            case HOTBAR_SWAP:
            case HOTBAR_MOVE_AND_READD:
            case COLLECT_TO_CURSOR:
            case UNKNOWN:
            case DROP_ALL_CURSOR:
            case DROP_ONE_CURSOR:
            case DROP_ALL_SLOT:
            case DROP_ONE_SLOT:
                e.setCancelled(true);
                break;
            default:
                e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!isFishDexTop(top)) return;

        // Cancel if any dragged slot touches the top inventory
        int topSize = top.getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
        // Also cancel to block moving items around in the bottom while Dex is open
        e.setCancelled(true);
    }
}
