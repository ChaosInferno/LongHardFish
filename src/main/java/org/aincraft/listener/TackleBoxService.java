package org.aincraft.listener;

import dev.triumphteam.gui.guis.StorageGui;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.aincraft.gui.TackleBoxGui;
import org.aincraft.ingame_items.TackleBoxItem;
import org.aincraft.items.BaitKeys;
import org.aincraft.items.BaitRegistry;
import org.aincraft.items.FishKeys;
import org.aincraft.items.TackleBoxStorage;
import org.aincraft.rods.RodItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TackleBoxService implements TackleBoxGui.TackleBoxClickHandler {

    private final JavaPlugin plugin;
    private final TackleBoxItem tackleBoxItem;
    private final TackleBoxStorage storage;
    private final int size;

    private static final class Session {
        final UUID playerId;
        final StorageGui gui;
        final EquipmentSlot hand;
        final int slotIndex;

        Session(UUID id, StorageGui gui, EquipmentSlot hand, int slotIndex) {
            this.playerId = id;
            this.gui = gui;
            this.hand = hand;
            this.slotIndex = slotIndex;
        }
    }

    private final Map<UUID, Session> open = new ConcurrentHashMap<>();

    public TackleBoxService(JavaPlugin plugin, TackleBoxItem tackleBoxItem, int size) {
        this.plugin = plugin;
        this.tackleBoxItem = tackleBoxItem;
        this.size = size;
        this.storage = new TackleBoxStorage(plugin, size);
    }

    /**
     * Check if a player has an open tacklebox session.
     */
    public boolean hasOpenSession(Player player) {
        return open.containsKey(player.getUniqueId());
    }

    /**
     * Get the TackleBoxItem for external use (e.g., drop listener).
     */
    public TackleBoxItem getTackleBoxItem() {
        return tackleBoxItem;
    }

    /**
     * Open the TackleBox GUI for the player using the TackleBox currently in-hand.
     */
    public void openFromHand(Player p, EquipmentSlot hand) {
        ItemStack tb = (hand == EquipmentSlot.HAND)
                ? p.getInventory().getItemInMainHand()
                : p.getInventory().getItemInOffHand();
        if (tb == null || !tackleBoxItem.isTackleBox(tb)) return;

        // Create the GUI using this service as the handler
        StorageGui gui = TackleBoxGui.create(plugin, this);

        // Sound: open
        p.playSound(Sound.sound(Key.key("longhardfish:tacklebox.open"), Sound.Source.PLAYER, 1.0f, 1.0f));

        // Load saved contents into GUI
        loadContentsIntoGui(tb, gui, p);

        // Set close action
        int slotIndex = (hand == EquipmentSlot.HAND) ? p.getInventory().getHeldItemSlot() : 40;
        Session session = new Session(p.getUniqueId(), gui, hand, slotIndex);
        open.put(p.getUniqueId(), session);

        gui.setCloseGuiAction(event -> handleClose(event, session));

        // Open the GUI
        gui.open(p);
    }

    private void loadContentsIntoGui(ItemStack tackleBox, StorageGui gui, Player p) {
        // Create temp inventory for storage to load into
        Inventory temp = Bukkit.createInventory(null, size);
        storage.loadIntoInventory(tackleBox, temp);

        // Transfer items, validating slot restrictions
        for (int i = 0; i < temp.getSize(); i++) {
            ItemStack it = temp.getItem(i);
            if (it == null || it.getType().isAir()) continue;

            if (TackleBoxGui.getBlockedSlots().contains(i)) {
                // Return invalid items to player
                returnToPlayer(p, it);
                continue;
            }

            if (!slotAllowsItem(i, it)) {
                returnToPlayer(p, it);
                continue;
            }

            gui.getInventory().setItem(i, it);
        }
    }

    private void returnToPlayer(Player p, ItemStack item) {
        var leftovers = p.getInventory().addItem(item);
        leftovers.values().forEach(drop ->
                p.getWorld().dropItemNaturally(p.getLocation(), drop));
    }

    // -------------------- Item Type Helpers --------------------

    private boolean isFishItem(ItemStack it) {
        return FishKeys.getFishKey(plugin, it) != null;
    }

    private boolean isFishingRod(ItemStack it) {
        return it != null && it.getType() == Material.FISHING_ROD;
    }

    private boolean isBaitItem(ItemStack it) {
        return BaitKeys.getBaitId(plugin, it) != null;
    }

    /**
     * Does the given TOP-inventory slot accept this item?
     */
    private boolean slotAllowsItem(int slot, ItemStack item) {
        if (TackleBoxGui.getBlockedSlots().contains(slot)) return false;
        if (TackleBoxGui.getFishSlots().contains(slot)) return isFishItem(item);
        if (slot == TackleBoxGui.getRodSlot()) return isFishingRod(item);
        if (TackleBoxGui.getBaitSlots().contains(slot)) return isBaitItem(item);
        return true;
    }

    // -------------------- Bait Helpers --------------------

    /**
     * Attach from slot 35 into the rod in slot 17, only when the rod is being picked up.
     */
    private void attachFromBinToRod(Inventory top) {
        int rodSlot = TackleBoxGui.getRodSlot();
        int baitSlot = TackleBoxGui.getBaitBinSlot();
        if (top.getSize() <= Math.max(rodSlot, baitSlot)) return;

        ItemStack rod = top.getItem(rodSlot);
        if (rod == null || rod.getType() != Material.FISHING_ROD) return;

        ItemStack bin = top.getItem(baitSlot);
        if (bin == null || bin.getType().isAir()) return;

        String feedId = BaitKeys.getBaitId(plugin, bin);
        if (feedId == null) return;

        String rodId = BaitKeys.getRodBait(plugin, rod);
        int rodCount = BaitKeys.getRodBaitCount(plugin, rod);

        if (rodId == null || rodCount <= 0) rodId = feedId;
        if (!rodId.equals(feedId)) return;

        int cap = Math.min(64, bin.getMaxStackSize());
        int canAdd = cap - rodCount;
        if (canAdd <= 0) return;

        int add = Math.min(canAdd, bin.getAmount());
        rodCount += add;
        bin.setAmount(bin.getAmount() - add);
        if (bin.getAmount() <= 0) top.setItem(baitSlot, null);

        BaitKeys.setRodBait(plugin, rod, rodId, rodCount);
        RodItemFactory.updateBaitStatusLine(plugin, rod, rodId);
        top.setItem(rodSlot, rod);
    }

    /**
     * Unload all bait from an incoming rod *before* it lands in slot 17, stacking into slot 35 if possible.
     */
    private void unloadIncomingRodToBin(ItemStack incomingRod, Inventory top, Player player) {
        if (incomingRod == null || incomingRod.getType() != Material.FISHING_ROD) return;

        String rodId = BaitKeys.getRodBait(plugin, incomingRod);
        int rodCount = BaitKeys.getRodBaitCount(plugin, incomingRod);
        if (rodId == null || rodCount <= 0) return;

        // Track if bait was actually removed for sound effect
        boolean baitRemoved = false;

        int baitSlot = TackleBoxGui.getBaitBinSlot();
        ItemStack bin = top.getItem(baitSlot);
        if (bin == null || bin.getType().isAir()) {
            ItemStack made = BaitRegistry.create(rodId, Math.min(rodCount, 64));
            if (made == null) {
                made = new ItemStack(Material.POISONOUS_POTATO, Math.min(rodCount, 64));
                BaitKeys.setBaitId(plugin, made, rodId);
            }
            top.setItem(baitSlot, made);
            rodCount -= made.getAmount();
            baitRemoved = true;
        } else {
            String binId = BaitKeys.getBaitId(plugin, bin);
            if (binId != null && binId.equals(rodId)) {
                int max = Math.min(bin.getMaxStackSize(), 64);
                int canAdd = max - bin.getAmount();
                if (canAdd > 0) {
                    int add = Math.min(canAdd, rodCount);
                    bin.setAmount(bin.getAmount() + add);
                    rodCount -= add;
                    top.setItem(baitSlot, bin);
                    baitRemoved = true;
                }
            }
        }

        if (rodCount <= 0) {
            BaitKeys.clearRodBait(plugin, incomingRod);
            RodItemFactory.updateBaitStatusLine(plugin, incomingRod, null);
        } else {
            BaitKeys.setRodBait(plugin, incomingRod, rodId, rodCount);
            RodItemFactory.updateBaitStatusLine(plugin, incomingRod, rodId);
        }

        // Play detach sound when bait was removed
        if (baitRemoved && player != null) {
            player.playSound(Sound.sound(Key.key("longhardfish:bait.detach"), Sound.Source.PLAYER, 1.0f, 1.0f));
        }
    }

    /**
     * Move all bait from rod into slot 35 if 35 is empty.
     */
    private void unloadRodBaitToSlot35(Inventory top, Player player) {
        int rodSlot = TackleBoxGui.getRodSlot();
        int baitSlot = TackleBoxGui.getBaitBinSlot();
        if (top.getSize() <= Math.max(rodSlot, baitSlot)) return;

        ItemStack rod = top.getItem(rodSlot);
        if (rod == null || rod.getType() != Material.FISHING_ROD) return;

        String rodId = BaitKeys.getRodBait(plugin, rod);
        int rodCount = BaitKeys.getRodBaitCount(plugin, rod);
        if (rodId == null || rodCount <= 0) return;

        ItemStack slot35 = top.getItem(baitSlot);
        if (slot35 != null && !slot35.getType().isAir()) return;

        ItemStack bait = BaitRegistry.create(rodId, Math.min(rodCount, 64));
        if (bait == null) {
            bait = new ItemStack(Material.POISONOUS_POTATO, Math.min(rodCount, 64));
            BaitKeys.setBaitId(plugin, bait, rodId);
        }

        top.setItem(baitSlot, bait);

        rodCount -= bait.getAmount();
        if (rodCount <= 0) {
            BaitKeys.clearRodBait(plugin, rod);
            RodItemFactory.updateBaitStatusLine(plugin, rod, null);
        } else {
            BaitKeys.setRodBait(plugin, rod, rodId, rodCount);
            RodItemFactory.updateBaitStatusLine(plugin, rod, rodId);
        }
        top.setItem(rodSlot, rod);

        // Play detach sound when bait was unloaded
        if (player != null) {
            player.playSound(Sound.sound(Key.key("longhardfish:bait.detach"), Sound.Source.PLAYER, 1.0f, 1.0f));
        }
    }

    // -------------------- Click Handler Implementation --------------------

    @Override
    public void handleClick(InventoryClickEvent e, StorageGui gui) {
        Session s = open.get(e.getWhoClicked().getUniqueId());
        if (s == null) return;

        Player player = (Player) e.getWhoClicked();
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();

        // Never allow a TackleBox inside itself
        if ((cursor != null && tackleBoxItem.isTackleBox(cursor))
                || (current != null && tackleBoxItem.isTackleBox(current) && e.isShiftClick())) {
            e.setCancelled(true);
            return;
        }

        // Block dropping the tacklebox while open (Q / Ctrl+Q)
        switch (e.getAction()) {
            case DROP_ONE_CURSOR, DROP_ALL_CURSOR, DROP_ONE_SLOT, DROP_ALL_SLOT -> {
                ItemStack dropSrc = switch (e.getAction()) {
                    case DROP_ONE_CURSOR, DROP_ALL_CURSOR -> cursor;
                    default -> current;
                };
                if (dropSrc != null && tackleBoxItem.isTackleBox(dropSrc)) {
                    e.setCancelled(true);
                    return;
                }
            }
            default -> {}
        }

        // ---------- TOP inventory (GUI) ----------
        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            handleTopInventoryClick(e, s, player);
        }
        // ---------- Player inventory (BOTTOM) ----------
        else if (e.getClickedInventory() == e.getView().getBottomInventory()) {
            handleBottomInventoryClick(e, s, player);
        }
    }

    private void handleTopInventoryClick(InventoryClickEvent e, Session s, Player player) {
        int slot = e.getSlot();

        // Blocked slots already have GuiItems that cancel - but double-check
        if (TackleBoxGui.getBlockedSlots().contains(slot)) {
            e.setCancelled(true);
            return;
        }

        // Right-click rod with empty cursor -> manual unload to bin
        if (slot == TackleBoxGui.getRodSlot()
                && e.getClick() == ClickType.RIGHT
                && (e.getCursor() == null || e.getCursor().getType().isAir())) {
            unloadRodBaitToSlot35(s.gui.getInventory(), player);
            e.setCancelled(true);
            return;
        }

        // Validate incoming item for whitelists
        ItemStack incoming = null;
        switch (e.getAction()) {
            case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR, COLLECT_TO_CURSOR -> incoming = e.getCursor();
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                int btn = e.getHotbarButton();
                if (btn >= 0) incoming = player.getInventory().getItem(btn);
            }
            default -> { /* pickups/drops without placing are fine */ }
        }
        if (incoming != null && !slotAllowsItem(slot, incoming)) {
            e.setCancelled(true);
            return;
        }

        // ====== Deferred bait logic ======

        // A) If placing a rod INTO slot 17: unload bait to bin first
        if (slot == TackleBoxGui.getRodSlot() && incoming != null && incoming.getType() == Material.FISHING_ROD) {
            unloadIncomingRodToBin(incoming, s.gui.getInventory(), player);
            return;
        }

        // B) If TAKING the rod OUT of slot 17: attach from bin now
        if (slot == TackleBoxGui.getRodSlot()) {
            switch (e.getAction()) {
                case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                     MOVE_TO_OTHER_INVENTORY, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                    attachFromBinToRod(s.gui.getInventory());
                }
                default -> { /* nothing special */ }
            }
        }

        // Play sound when picking up rod with bait attached
        if (slot == TackleBoxGui.getRodSlot() && e.getAction() == InventoryAction.PICKUP_ALL) {
            ItemStack rod = e.getCurrentItem();
            if (rod != null && isFishingRod(rod)) {
                String baitId = BaitKeys.getRodBait(plugin, rod);
                int count = BaitKeys.getRodBaitCount(plugin, rod);
                if (baitId != null && count > 0) {
                    player.playSound(Sound.sound(Key.key("longhardfish:bait.attach"), Sound.Source.PLAYER, 1.0f, 1.0f));
                }
            }
        }
    }

    private void handleBottomInventoryClick(InventoryClickEvent e, Session s, Player player) {
        int held = player.getInventory().getHeldItemSlot();

        // Protect held slot / offhand swap
        if (e.getSlotType() == InventoryType.SlotType.QUICKBAR && e.getSlot() == held) {
            e.setCancelled(true);
            return;
        }
        if (e.getHotbarButton() == held) {
            e.setCancelled(true);
            return;
        }
        if (e.getAction() == InventoryAction.HOTBAR_SWAP && e.getHotbarButton() == -1) {
            e.setCancelled(true);
            return;
        }

        // Smart SHIFT-CLICK: bottom -> top
        if (e.isShiftClick() && e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack source = e.getCurrentItem();
            if (source == null || source.getType().isAir()) return;

            e.setCancelled(true);
            int moved = routeShiftFromBottomToTop(source, s.gui.getInventory());

            if (moved > 0 && source.getAmount() <= 0) {
                e.getClickedInventory().setItem(e.getSlot(), null);
            }
        }
    }

    @Override
    public void handleDrag(InventoryDragEvent e, StorageGui gui) {
        Session s = open.get(e.getWhoClicked().getUniqueId());
        if (s == null) return;

        ItemStack cursor = e.getOldCursor();
        for (int raw : e.getRawSlots()) {
            if (raw < gui.getInventory().getSize()) {
                if (TackleBoxGui.getBlockedSlots().contains(raw) || !slotAllowsItem(raw, cursor)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    private void handleClose(InventoryCloseEvent e, Session s) {
        final Player p = (Player) e.getPlayer();

        // Save (skip decorative blocked slots)
        ItemStack tackleBox = (s.hand == EquipmentSlot.HAND)
                ? p.getInventory().getItemInMainHand()
                : p.getInventory().getItemInOffHand();

        if (tackleBox == null || !tackleBoxItem.isTackleBox(tackleBox)) {
            for (ItemStack it : p.getInventory().getContents()) {
                if (it != null && tackleBoxItem.isTackleBox(it)) {
                    tackleBox = it;
                    break;
                }
            }
        }

        if (tackleBox != null && tackleBoxItem.isTackleBox(tackleBox)) {
            Inventory filtered = Bukkit.createInventory(null, s.gui.getInventory().getSize());
            for (int i = 0; i < s.gui.getInventory().getSize(); i++) {
                if (!TackleBoxGui.getBlockedSlots().contains(i)) {
                    filtered.setItem(i, s.gui.getInventory().getItem(i));
                }
            }
            storage.saveFromInventory(tackleBox, filtered);
        }

        // Sound: close
        p.playSound(Sound.sound(Key.key("longhardfish:tacklebox.close"), Sound.Source.PLAYER, 1.0f, 1.0f));

        open.remove(p.getUniqueId());
    }

    // -------------------- Sorting Helpers --------------------

    private java.util.List<Integer> allowedSlotsFor(ItemStack item) {
        if (item == null || item.getType().isAir()) return java.util.List.of();
        if (isFishItem(item)) return TackleBoxGui.getFishSlots().stream().filter(i -> i < size).toList();
        if (isFishingRod(item)) return (TackleBoxGui.getRodSlot() < size) ? java.util.List.of(TackleBoxGui.getRodSlot()) : java.util.List.of();
        if (isBaitItem(item)) return TackleBoxGui.getBaitSlots().stream().filter(i -> i < size).toList();
        return java.util.List.of();
    }

    private int packIntoExisting(ItemStack moving, Inventory top, java.util.List<Integer> dest) {
        int moved = 0;
        for (int slot : dest) {
            if (moving.getAmount() <= 0) break;
            if (TackleBoxGui.getBlockedSlots().contains(slot)) continue;
            ItemStack in = top.getItem(slot);
            if (in == null || in.getType().isAir()) continue;
            if (!in.isSimilar(moving)) continue;

            int max = Math.min(in.getMaxStackSize(), 64);
            int canAdd = max - in.getAmount();
            if (canAdd <= 0) continue;

            int add = Math.min(canAdd, moving.getAmount());
            in.setAmount(in.getAmount() + add);
            moving.setAmount(moving.getAmount() - add);
            moved += add;
        }
        return moved;
    }

    private int fillEmpties(ItemStack moving, Inventory top, java.util.List<Integer> dest) {
        int moved = 0;
        for (int slot : dest) {
            if (moving.getAmount() <= 0) break;
            if (TackleBoxGui.getBlockedSlots().contains(slot)) continue;
            if (!slotAllowsItem(slot, moving)) continue;

            ItemStack in = top.getItem(slot);
            if (in != null && !in.getType().isAir()) continue;

            int place = Math.min(moving.getAmount(), Math.min(moving.getMaxStackSize(), 64));
            ItemStack copy = moving.clone();
            copy.setAmount(place);
            top.setItem(slot, copy);
            moving.setAmount(moving.getAmount() - place);
            moved += place;
        }
        return moved;
    }

    private int routeShiftFromBottomToTop(ItemStack source, Inventory top) {
        var dest = allowedSlotsFor(source);
        if (dest.isEmpty()) return 0;
        int moved = 0;
        moved += packIntoExisting(source, top, dest);
        moved += fillEmpties(source, top, dest);
        return moved;
    }
}
