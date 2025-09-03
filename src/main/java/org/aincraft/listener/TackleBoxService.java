package org.aincraft.listener;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.aincraft.gui.TackleBoxGui;
import org.aincraft.ingame_items.TackleBoxItem;
import org.aincraft.items.BaitKeys;
import org.aincraft.items.FishKeys;
import org.aincraft.items.TackleBoxStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
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

public final class TackleBoxService implements Listener {
    private static final String NS = "longhardfish";
    private static final Component TITLE =
            text("\ue003\ua030").font(key(NS, "interface")).color(TextColor.color(0xFFFFFF));

    // Decorative blocked slots in a 54-slot chest (0..53)
    private static final Set<Integer> BLOCKED_SLOTS = new java.util.LinkedHashSet<>();
    static {
        BLOCKED_SLOTS.addAll(java.util.List.of(7, 8, 16, 25, 26, 34));
        BLOCKED_SLOTS.addAll(IntStream.rangeClosed(36, 53).boxed().collect(Collectors.toSet()));
    }

    // Fish-only: 9–15, 18–24, 27–33
    private static final java.util.Set<Integer> FISH_SLOTS = new java.util.LinkedHashSet<>();
    static {
        FISH_SLOTS.addAll(IntStream.rangeClosed(9, 15).boxed().toList());
        FISH_SLOTS.addAll(IntStream.rangeClosed(18, 24).boxed().toList());
        FISH_SLOTS.addAll(IntStream.rangeClosed(27, 33).boxed().toList());
    }

    // Rod-only + Bait bin
    private static final int ROD_SLOT  = 17;
    private static final int BAIT_SLOT = 35;

    // Redstone-only: 0–6, 35 (35 is also our bait bin)
    private static final java.util.Set<Integer> REDSTONE_SLOTS = new java.util.LinkedHashSet<>();
    static {
        REDSTONE_SLOTS.addAll(IntStream.rangeClosed(0, 6).boxed().toList());
        REDSTONE_SLOTS.add(BAIT_SLOT);
    }

    private final JavaPlugin plugin;
    private final TackleBoxItem tackleBoxItem;
    private final TackleBoxStorage storage;
    private final int size;

    private final NamespacedKey immovableKey;

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
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(null);
        meta.lore(null);
        meta.setHideTooltip(true);
        meta.setItemModel(NamespacedKey.fromString("longhardfish:icons/empty"));
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
        if (REDSTONE_SLOTS.contains(slot)) return isRedstoneDust(item); // redstone-only (bait bin is redstone-based bait)
        return true;
    }

    /** Open the TackleBox GUI for the player using the TackleBox currently in-hand. */
    public void openFromHand(Player p, EquipmentSlot hand) {
        ItemStack tb = (hand == EquipmentSlot.HAND)
                ? p.getInventory().getItemInMainHand()
                : p.getInventory().getItemInOffHand();
        if (tb == null || !tackleBoxItem.isTackleBox(tb)) return;

        Inventory gui = Bukkit.createInventory(new TackleBoxGui.Holder(), size, TITLE);

        // Sound: open
        p.playSound(Sound.sound(Key.key("longhardfish:tacklebox.open"), Sound.Source.PLAYER, 1.0f, 1.0f));

        storage.loadIntoInventory(tb, gui);

        // Clean any item in invalid slots back to player
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

        // Fill decorative blocked slots
        ItemStack blocker = makeBlockedIcon();
        for (int slot : BLOCKED_SLOTS) {
            if (slot >= 0 && slot < gui.getSize()) {
                gui.setItem(slot, blocker);
            }
        }

        int slotIndex = (hand == EquipmentSlot.HAND) ? p.getInventory().getHeldItemSlot() : 40;
        open.put(p.getUniqueId(), new Session(p.getUniqueId(), gui, hand, slotIndex));

        Bukkit.getScheduler().runTask(plugin, () -> p.openInventory(gui));
    }

    // -------------------- Bait helpers --------------------

    private void updateRodLore(ItemStack rod) {
        if (rod == null || !rod.hasItemMeta()) return;
        String id = BaitKeys.getRodBait(plugin, rod);
        int count = BaitKeys.getRodBaitCount(plugin, rod);

        ItemMeta meta = rod.getItemMeta();
        java.util.List<Component> lore = new java.util.ArrayList<>();
        if (meta.lore() != null) lore.addAll(meta.lore());

        // remove any previous "Bait:" lines
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        lore.removeIf(c -> {
            String s = plain.serialize(c).toLowerCase();
            return s.startsWith("bait: ");
        });

        if (id != null && count > 0) {
            lore.add(Component.text("Bait: " + id + " (x" + count + ")", NamedTextColor.GOLD));
        }
        meta.lore(lore);
        rod.setItemMeta(meta);
    }

    /** Attach from slot 35 into the rod in slot 17, only when the rod is being picked up. */
    private void attachFromBinToRod(Inventory top) {
        if (top.getSize() <= Math.max(ROD_SLOT, BAIT_SLOT)) return;

        ItemStack rod = top.getItem(ROD_SLOT);
        if (rod == null || rod.getType() != Material.FISHING_ROD) return;

        ItemStack bin = top.getItem(BAIT_SLOT);
        if (bin == null || bin.getType().isAir()) return;

        String feedId = BaitKeys.getBaitId(plugin, bin);
        if (feedId == null) return;

        String rodId = BaitKeys.getRodBait(plugin, rod);
        int rodCount = BaitKeys.getRodBaitCount(plugin, rod);

        if (rodId == null || rodCount <= 0) rodId = feedId;
        if (!rodId.equals(feedId)) return; // different bait type—do nothing

        int cap = Math.min(64, bin.getMaxStackSize());
        int canAdd = cap - rodCount;
        if (canAdd <= 0) return;

        int add = Math.min(canAdd, bin.getAmount());
        rodCount += add;
        bin.setAmount(bin.getAmount() - add);
        if (bin.getAmount() <= 0) top.setItem(BAIT_SLOT, null);

        BaitKeys.setRodBait(plugin, rod, rodId, rodCount);
        updateRodLore(rod);
        top.setItem(ROD_SLOT, rod);
    }

    /** Unload all bait from an incoming rod *before* it lands in slot 17, stacking into slot 35 if possible. */
    private void unloadIncomingRodToBin(ItemStack incomingRod, Inventory top) {
        if (incomingRod == null || incomingRod.getType() != Material.FISHING_ROD) return;

        String rodId = BaitKeys.getRodBait(plugin, incomingRod);
        int rodCount = BaitKeys.getRodBaitCount(plugin, incomingRod);
        if (rodId == null || rodCount <= 0) return;

        ItemStack bin = top.getItem(BAIT_SLOT);
        if (bin == null || bin.getType().isAir()) {
            ItemStack made = org.aincraft.items.BaitRegistry.create(rodId, Math.min(rodCount, 64));
            if (made == null) { // fallback if that bait got deregistered
                made = new ItemStack(Material.REDSTONE, Math.min(rodCount, 64));
                BaitKeys.setBaitId(plugin, made, rodId);
            }
            top.setItem(BAIT_SLOT, made);
            rodCount -= made.getAmount();
        } else {
            String binId = BaitKeys.getBaitId(plugin, bin);
            if (binId != null && binId.equals(rodId)) {
                int max = Math.min(bin.getMaxStackSize(), 64);
                int canAdd = max - bin.getAmount();
                if (canAdd > 0) {
                    int add = Math.min(canAdd, rodCount);
                    bin.setAmount(bin.getAmount() + add);
                    rodCount -= add;
                    top.setItem(BAIT_SLOT, bin);
                }
            }
            // else: different bait already in bin — leave rod's bait as-is
        }

        if (rodCount <= 0) {
            BaitKeys.clearRodBait(plugin, incomingRod);
        } else {
            BaitKeys.setRodBait(plugin, incomingRod, rodId, rodCount);
        }
        updateRodLore(incomingRod);
    }

    /** Move all bait from rod into slot 35 if 35 is empty. */
    private void unloadRodBaitToSlot35(Inventory top) {
        if (top.getSize() <= Math.max(ROD_SLOT, BAIT_SLOT)) return;
        ItemStack rod = top.getItem(ROD_SLOT);
        if (rod == null || rod.getType() != Material.FISHING_ROD) return;

        String rodId = BaitKeys.getRodBait(plugin, rod);
        int rodCount = BaitKeys.getRodBaitCount(plugin, rod);
        if (rodId == null || rodCount <= 0) return;

        ItemStack slot35 = top.getItem(BAIT_SLOT);
        if (slot35 != null && !slot35.getType().isAir()) return; // require empty

        ItemStack bait = org.aincraft.items.BaitRegistry.create(rodId, Math.min(rodCount, 64));
        if (bait == null) {
            // fallback if someone removed the bait definition
            bait = new ItemStack(Material.REDSTONE, Math.min(rodCount, 64));
            BaitKeys.setBaitId(plugin, bait, rodId);
        }

        top.setItem(BAIT_SLOT, bait);

        rodCount -= bait.getAmount(); // move all (up to 64)
        if (rodCount <= 0) {
            BaitKeys.clearRodBait(plugin, rod);
        } else {
            BaitKeys.setRodBait(plugin, rod, rodId, rodCount);
        }
        updateRodLore(rod);
        top.setItem(ROD_SLOT, rod);
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
            int slot = e.getSlot();

            // Decorative blocked slots
            if (BLOCKED_SLOTS.contains(slot)) { e.setCancelled(true); return; }

            // (Optional) Right-click rod with empty cursor → manual unload to bin (if empty)
            if (slot == ROD_SLOT
                    && e.getClick() == ClickType.RIGHT
                    && (e.getCursor() == null || e.getCursor().getType().isAir())) {
                unloadRodBaitToSlot35(s.inv);
                e.setCancelled(true);
                return;
            }

            // Validate incoming item for whitelists
            ItemStack incoming = null;
            switch (e.getAction()) {
                case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR, COLLECT_TO_CURSOR -> incoming = e.getCursor();
                case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                    int btn = e.getHotbarButton();
                    if (btn >= 0) incoming = ((Player)e.getWhoClicked()).getInventory().getItem(btn);
                }
                default -> { /* pickups/drops without placing are fine */ }
            }
            if (incoming != null && !slotAllowsItem(slot, incoming)) {
                e.setCancelled(true);
                return;
            }

            // ====== Deferred bait logic ======

            // A) If placing a rod INTO slot 17: unload bait to bin first
            if (slot == ROD_SLOT && incoming != null && incoming.getType() == Material.FISHING_ROD) {
                unloadIncomingRodToBin(incoming, s.inv);
                // don't cancel; let Bukkit place the (possibly updated) rod
                return;
            }

            // B) If TAKING the rod OUT of slot 17: attach from bin now
            if (slot == ROD_SLOT) {
                switch (e.getAction()) {
                    case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                         MOVE_TO_OTHER_INVENTORY, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                        attachFromBinToRod(s.inv);
                        // let the pickup proceed
                    }
                    default -> { /* nothing special */ }
                }
            }

            if (slot == ROD_SLOT && e.getAction() == InventoryAction.PICKUP_ALL) {
                ItemStack rod = e.getCurrentItem();
                if (rod != null && isFishingRod(rod)) {
                    String baitId = BaitKeys.getRodBait(plugin, rod);
                    int count = BaitKeys.getRodBaitCount(plugin, rod);
                    if (baitId != null && count > 0) {
                        ((Player)e.getWhoClicked()).playSound(
                                Sound.sound(Key.key("longhardfish:bait.attach"), Sound.Source.PLAYER, 1.0f, 1.0f)
                        );
                    }
                }
            }
        }

        // ---------- Player inventory (BOTTOM) ----------
        if (e.getClickedInventory() == e.getView().getBottomInventory()) {
            int held = ((Player) e.getWhoClicked()).getInventory().getHeldItemSlot();

            // Protect held slot / offhand swap
            if (e.getSlotType() == InventoryType.SlotType.QUICKBAR && e.getSlot() == held) { e.setCancelled(true); return; }
            if (e.getHotbarButton() == held) { e.setCancelled(true); return; }
            if (e.getAction() == InventoryAction.HOTBAR_SWAP && e.getHotbarButton() == -1) { e.setCancelled(true); return; }

            // Smart SHIFT-CLICK: bottom -> top (keeps your sorter)
            if (e.isShiftClick() && e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                ItemStack source = e.getCurrentItem();
                if (source == null || source.getType().isAir()) return;

                e.setCancelled(true);
                int moved = routeShiftFromBottomToTop(source, s.inv);

                if (moved > 0 && (source.getAmount() <= 0)) {
                    e.getClickedInventory().setItem(e.getSlot(), null);
                }
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

        if (e.getView().getTopInventory() != s.inv) return;

        final Player p = (Player) e.getPlayer();

        // Save (skip decorative blocked slots)
        ItemStack tackleBox = (s.hand == EquipmentSlot.HAND)
                ? p.getInventory().getItemInMainHand()
                : p.getInventory().getItemInOffHand();

        if (tackleBox == null || !tackleBoxItem.isTackleBox(tackleBox)) {
            for (ItemStack it : p.getInventory().getContents()) {
                if (it != null && tackleBoxItem.isTackleBox(it)) { tackleBox = it; break; }
            }
        }

        if (tackleBox != null && tackleBoxItem.isTackleBox(tackleBox)) {
            Inventory filtered = Bukkit.createInventory(null, s.inv.getSize());
            for (int i = 0; i < s.inv.getSize(); i++) {
                if (!BLOCKED_SLOTS.contains(i)) {
                    filtered.setItem(i, s.inv.getItem(i));
                }
            }
            storage.saveFromInventory(tackleBox, filtered);
        }

        // Sound: close
        p.playSound(Sound.sound(Key.key("longhardfish:tacklebox.close"), Sound.Source.PLAYER, 1.0f, 1.0f));

        open.remove(p.getUniqueId());
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onPlayerDrop(PlayerDropItemEvent e) {
        Session s = open.get(e.getPlayer().getUniqueId());
        if (s == null) return;

        ItemStack dropped = e.getItemDrop().getItemStack();
        if (dropped != null && tackleBoxItem.isTackleBox(dropped)) {
            e.setCancelled(true);
            e.getPlayer().setCooldown(dropped.getType(), 5);
        }
    }

    // -------------------- Sorting helpers (unchanged) --------------------

    private java.util.List<Integer> allowedSlotsFor(ItemStack item) {
        if (item == null || item.getType().isAir()) return java.util.List.of();
        if (isFishItem(item))        return FISH_SLOTS.stream().filter(i -> i < size).toList();
        if (isFishingRod(item))      return (ROD_SLOT < size) ? java.util.List.of(ROD_SLOT) : java.util.List.of();
        if (isRedstoneDust(item))    return REDSTONE_SLOTS.stream().filter(i -> i < size).toList();
        return java.util.List.of();
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
