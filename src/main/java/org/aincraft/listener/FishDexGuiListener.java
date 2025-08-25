package org.aincraft.listener;

import org.aincraft.gui.FishDexFishSelector;
import org.aincraft.service.InventoryBackupService;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.aincraft.sfx.FishDexSFX;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class FishDexGuiListener implements Listener {

    private final FishDexFishSelector selector;
    private final InventoryBackupService backup;

    public FishDexGuiListener(FishDexFishSelector selector, InventoryBackupService backup) {
        this.selector = selector;
        this.backup = backup;
    }

    private void backupAndClear(Player p) {
        try {
            var id = p.getUniqueId();
            if (!backup.hasBackup(id)) {
                backup.backupIfNeeded(p);  // snapshot only once
            }
            // clear main/hotbar/offhand/cursor/extras (leave armor; uncomment if you really want to clear armor too)
            p.getInventory().clear();
            p.getInventory().setExtraContents(null);
            p.getInventory().setItemInOffHand(null);
            p.setItemOnCursor(null);
            // p.getInventory().setArmorContents(null); // optional
        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Failed to backup+clear inventory for " + p.getName(), ex);
            p.sendMessage("§cCouldn't update the FishDex safely (inventory backup failed).");
        }
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
                FishDexSFX.playSelect(p);
                backupAndClear(p);
                Bukkit.getScheduler().runTask(selector.plugin(), () -> selector.open(p, fishId, false));
            }
            return;
        }

        // Click in the player inventory/hotbar while Dex is open: treat hotbar buttons as nav
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(p.getInventory())) {
            int hotbarIndex = e.getSlot(); // 0..8 for hotbar
            int current = holder.pageIndex;   // 0-based
            int max = holder.pageCount - 1;     // 0-based

            if (hotbarIndex == FishDexFishSelector.HOTBAR_NEXT_1) {
                if (current < max) {
                    FishDexSFX.playNext(p);
                    int target = current + 1;
                    backupAndClear(p);
                    Bukkit.getScheduler().runTask(selector.plugin(), () -> selector.openPage(p, target));
                }
                return;
            }
            if (hotbarIndex == FishDexFishSelector.HOTBAR_NEXT_ALL) {
                if (current < max) {
                    FishDexSFX.playNext(p);
                    backupAndClear(p);
                    Bukkit.getScheduler().runTask(selector.plugin(), () -> selector.openPage(p, max));
                }
                return;
            }
            if (hotbarIndex == FishDexFishSelector.HOTBAR_PREV_1) {
                if (current > 0) {
                    FishDexSFX.playPrevious(p);
                    int target = current - 1;
                    backupAndClear(p);
                    Bukkit.getScheduler().runTask(selector.plugin(), () -> selector.openPage(p, target));
                }
                return;
            }
            if (hotbarIndex == FishDexFishSelector.HOTBAR_PREV_ALL) {
                if (current > 0) {
                    FishDexSFX.playPrevious(p);
                    backupAndClear(p);
                    Bukkit.getScheduler().runTask(selector.plugin(), () -> selector.openPage(p, 0));
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

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!isFishDexTop(top)) return;

        Player p = (Player) e.getPlayer();

        // Delay and check if the player opened another Dex immediately (paging)
        Bukkit.getScheduler().runTaskLater(selector.plugin(), () -> {
            Inventory curTop = p.getOpenInventory().getTopInventory();
            boolean stillDex = curTop != null && curTop.getHolder() instanceof FishDexFishSelector.DexHolder;
            if (!stillDex) {
                try {
                    FishDexSFX.playClose(p);
                    backup.restoreIfPresent(p);
                } catch (Exception ex) {
                    Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                            "Failed to restore inventory for " + p.getName(), ex);
                }
            }
        }, 2L); // 2 ticks is plenty; 1 tick can also work
    }
}
