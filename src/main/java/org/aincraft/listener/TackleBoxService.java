package org.aincraft.listener;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.aincraft.gui.TackleBoxGui;
import org.aincraft.ingame_items.TackleBoxItem;
import org.aincraft.items.FishKeys;
import org.aincraft.items.TackleBoxStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.kyori.adventure.key.Key.key;
import static net.kyori.adventure.text.Component.text;
import net.kyori.adventure.sound.Sound;

public final class TackleBoxService implements Listener {
    private static final String NS = "longhardfish";
    private static final Component TITLE =
            text("\ue003\ua030").font(key(NS, "interface")).color(TextColor.color(0xFFFFFF));

    // Top-inventory slots to block in a 54-slot chest (0..53)
    private static final Set<Integer> BLOCKED_SLOTS = new java.util.LinkedHashSet<>();
    static {
        BLOCKED_SLOTS.addAll(java.util.List.of(7, 8, 16, 25, 26, 34));
        BLOCKED_SLOTS.addAll(IntStream.rangeClosed(36, 53).boxed().collect(Collectors.toSet()));
    }

    // Fish-only: 9–15, 18–24, 27–33
    private static final java.util.Set<Integer> FISH_SLOTS = new java.util.LinkedHashSet<>();
    static {
        FISH_SLOTS.addAll(java.util.stream.IntStream.rangeClosed(9, 15).boxed().toList());
        FISH_SLOTS.addAll(java.util.stream.IntStream.rangeClosed(18, 24).boxed().toList());
        FISH_SLOTS.addAll(java.util.stream.IntStream.rangeClosed(27, 33).boxed().toList());
    }

    // Rod-only: 17
    private static final int ROD_SLOT = 17;

    // Redstone-only: 0–6, 35
    private static final java.util.Set<Integer> REDSTONE_SLOTS = new java.util.LinkedHashSet<>();
    static {
        REDSTONE_SLOTS.addAll(java.util.stream.IntStream.rangeClosed(0, 6).boxed().toList());
        REDSTONE_SLOTS.add(35);
    }

    private final JavaPlugin plugin;
    private final TackleBoxItem tackleBoxItem;
    private final TackleBoxStorage storage;
    private final int size;

    private final NamespacedKey immovableKey;

    // Track which player has which TackleBox open and where that item lives (slot)
    private static final class Session {
        final UUID playerId;
        final Inventory inv;       // the TOP inventory we created
        final EquipmentSlot hand;  // HAND or OFF_HAND
        final int slotIndex;       // player's hand slot index (0..8) or 40 for offhand
        Session(UUID id, Inventory inv, EquipmentSlot hand, int slotIndex) {
            this.playerId = id;
            this.inv = inv;
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
        this.immovableKey = new NamespacedKey(plugin, "immovable");
    }

    private ItemStack makeBlockedIcon() {
        ItemStack it = new ItemStack(Material.PAPER); // base is irrelevant; model hides it
        ItemMeta meta = it.getItemMeta();
        // No tooltip/name/lore
        meta.displayName(null);
        meta.lore(null);
        meta.setHideTooltip(true); // Paper 1.21+
        meta.setItemModel(NamespacedKey.fromString("longhardfish:icons/empty"));
        // Mark with PDC so we can detect immovable items (if needed later)
        meta.getPersistentDataContainer().set(immovableKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isFishItem(ItemStack it) {
        return FishKeys.getFishKey(plugin, it) != null;
    }

    private boolean isFishingRod(ItemStack it) {
        return it != null && it.getType() == Material.FISHING_ROD;
    }

    private boolean isRedstoneDust(ItemStack it) {
        return it != null && it.getType() == Material.REDSTONE;
    }

    /** Does the given TOP-inventory slot accept this item? */
    private boolean slotAllowsItem(int slot, ItemStack item) {
        if (BLOCKED_SLOTS.contains(slot)) return false;                 // decorative
        if (FISH_SLOTS.contains(slot))   return isFishItem(item);       // fish-only
        if (slot == ROD_SLOT)            return isFishingRod(item);     // rod-only
        if (REDSTONE_SLOTS.contains(slot)) return isRedstoneDust(item); // redstone-only
        return true; // other top slots accept anything (still block TackleBox elsewhere)
    }

    /** Open the TackleBox GUI for the player using the TackleBox currently in-hand. */
    public void openFromHand(Player p, EquipmentSlot hand) {
        ItemStack tb = (hand == EquipmentSlot.HAND)
                ? p.getInventory().getItemInMainHand()
                : p.getInventory().getItemInOffHand();
        if (tb == null || !tackleBoxItem.isTackleBox(tb)) return;

        // Create GUI
        Inventory gui = Bukkit.createInventory(new TackleBoxGui.Holder(), size, TITLE);
        p.playSound(
                Sound.sound(Key.key("longhardfish:tacklebox.open"), Sound.Source.PLAYER, 1.0f, 1.0f)
        );

        // Load saved items into GUI
        storage.loadIntoInventory(tb, gui);
        for (int i = 0; i < gui.getSize(); i++) {
            ItemStack it = gui.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            if (!slotAllowsItem(i, it)) {
                var leftovers = p.getInventory().addItem(it);
                gui.setItem(i, null);
                if (!leftovers.isEmpty()) {
                    leftovers.values().forEach(drop -> p.getWorld().dropItemNaturally(p.getLocation(), drop));
                }
            }
        }

        // Fill blocked slots with invisible, locked icons
        ItemStack blocker = makeBlockedIcon();
        for (int slot : BLOCKED_SLOTS) {
            if (slot >= 0 && slot < gui.getSize()) {
                gui.setItem(slot, blocker);
            }
        }

        // Remember where the TackleBox is so we can save back
        int slotIndex = (hand == EquipmentSlot.HAND) ? p.getInventory().getHeldItemSlot() : 40; // 40 = offhand
        open.put(p.getUniqueId(), new Session(p.getUniqueId(), gui, hand, slotIndex));

        // Open next tick (safe)
        Bukkit.getScheduler().runTask(plugin, () -> p.openInventory(gui));
    }

    // -------------------- Listeners --------------------

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        Session s = open.get(e.getWhoClicked().getUniqueId());
        if (s == null || e.getView().getTopInventory() != s.inv) return;

        // Never allow a TackleBox inside itself
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        if ((cursor != null && tackleBoxItem.isTackleBox(cursor))
                || (current != null && tackleBoxItem.isTackleBox(current) && e.isShiftClick())) {
            e.setCancelled(true);
            return;
        }

        // ---------- TOP inventory rules ----------
        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            int slot = e.getSlot();

            // Decorative blocked slots
            if (BLOCKED_SLOTS.contains(slot)) { e.setCancelled(true); return; }

            // Determine the incoming item (what would be placed into the slot)
            ItemStack incoming = null;
            switch (e.getAction()) {
                case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR, COLLECT_TO_CURSOR -> incoming = cursor;
                case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                    int btn = e.getHotbarButton();
                    if (btn >= 0) incoming = ((Player)e.getWhoClicked()).getInventory().getItem(btn);
                }
                // NOTE: do NOT handle MOVE_TO_OTHER_INVENTORY (shift) here; we route it in the bottom branch.
                default -> { /* pickups/drops without placing are fine */ }
            }

            if (incoming != null && !slotAllowsItem(slot, incoming)) {
                e.setCancelled(true);
                return;
            }
        }

        // ---------- BOTTOM inventory protections + smart shift routing ----------
        if (e.getClickedInventory() == e.getView().getBottomInventory()) {
            int held = ((Player) e.getWhoClicked()).getInventory().getHeldItemSlot();

            // Protect held slot / offhand swap
            if (e.getSlotType() == InventoryType.SlotType.QUICKBAR && e.getSlot() == held) { e.setCancelled(true); return; }
            if (e.getHotbarButton() == held) { e.setCancelled(true); return; }
            if (e.getAction() == InventoryAction.HOTBAR_SWAP && e.getHotbarButton() == -1) { // offhand swap
                e.setCancelled(true); return;
            }

            // Smart SHIFT-CLICK: bottom -> top
            if (e.isShiftClick() && e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                ItemStack source = e.getCurrentItem();
                if (source == null || source.getType().isAir()) return;

                // Cancel vanilla routing; do our own
                e.setCancelled(true);

                int before = source.getAmount();
                int moved = routeShiftFromBottomToTop(source, s.inv);

                // If we moved items, the same 'source' reference was decremented already.
                // Update the source slot (clear if 0)
                if (moved > 0) {
                    if (source.getAmount() <= 0) {
                        e.getClickedInventory().setItem(e.getSlot(), null);
                    }
                }
                // If moved == 0, do nothing (per your requirement)
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent e) {
        Session s = open.get(e.getWhoClicked().getUniqueId());
        if (s == null || e.getInventory() != s.inv) return;

        ItemStack cursor = e.getOldCursor();
        for (int raw : e.getRawSlots()) {
            if (raw < s.inv.getSize()) { // top inv
                if (BLOCKED_SLOTS.contains(raw) || !slotAllowsItem(raw, cursor)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClose(InventoryCloseEvent e) {
        Session s = open.get(e.getPlayer().getUniqueId());
        if (s == null) return;

        // Only save if the closed TOP inventory is our GUI
        if (e.getView().getTopInventory() != s.inv) return;

        final Player p = (Player) e.getPlayer();

        // Re-fetch the tacklebox item (prefer the hand we opened from)
        ItemStack tackleBox = (s.hand == EquipmentSlot.HAND)
                ? p.getInventory().getItemInMainHand()
                : p.getInventory().getItemInOffHand();

        // If swapped out, find any tacklebox in the player inventory
        if (tackleBox == null || !tackleBoxItem.isTackleBox(tackleBox)) {
            for (ItemStack it : p.getInventory().getContents()) {
                if (it != null && tackleBoxItem.isTackleBox(it)) { tackleBox = it; break; }
            }
        }

        if (tackleBox != null && tackleBoxItem.isTackleBox(tackleBox)) {
            // Build a filtered copy of the top inventory without decorative blockers
            Inventory filtered = Bukkit.createInventory(null, s.inv.getSize());
            for (int i = 0; i < s.inv.getSize(); i++) {
                if (!BLOCKED_SLOTS.contains(i)) {
                    filtered.setItem(i, s.inv.getItem(i));
                }
            }
            storage.saveFromInventory(tackleBox, filtered);
        }

        p.playSound(
                Sound.sound(Key.key("longhardfish:tacklebox.close"), Sound.Source.PLAYER, 1.0f, 1.0f)
        );
        open.remove(p.getUniqueId());
    }

    private java.util.List<Integer> allowedSlotsFor(ItemStack item) {
        if (item == null || item.getType().isAir()) return java.util.List.of();
        if (isFishItem(item)) {
            return FISH_SLOTS.stream().filter(i -> i < size).toList();
        }
        if (isFishingRod(item)) {
            return (ROD_SLOT < size) ? java.util.List.of(ROD_SLOT) : java.util.List.of();
        }
        if (isRedstoneDust(item)) {
            return REDSTONE_SLOTS.stream().filter(i -> i < size).toList();
        }
        return java.util.List.of(); // other items are not auto-sorted
    }

    private int packIntoExisting(ItemStack moving, Inventory top, java.util.List<Integer> dest) {
        int moved = 0;
        for (int slot : dest) {
            if (moving.getAmount() <= 0) break;
            if (BLOCKED_SLOTS.contains(slot)) continue;
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
            if (BLOCKED_SLOTS.contains(slot)) continue;
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
