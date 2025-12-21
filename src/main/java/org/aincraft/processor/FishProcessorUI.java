// org/aincraft/processor/FishProcessorUI.java
package org.aincraft.processor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.aincraft.events.PacketBlockInteractEvent;
import org.aincraft.items.FishKeys;
import org.aincraft.items.Keys;
import org.aincraft.knives.KnifeDurability; // << add
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import org.aincraft.container.FishModel;

import static net.kyori.adventure.key.Key.key;
import static net.kyori.adventure.text.Component.text;

public final class FishProcessorUI implements Listener {

    private static final int SIZE = 27; // 9x3
    private static final String NS = "longhardfish";
    private static final String NAV_EMPTY_TEX = "icons/empty";
    private final NamespacedKey IMMOVABLE_KEY;
    private final Map<NamespacedKey, FishModel> fishModelMap;
    private static final Component TITLE =
            Component.empty()
                    // your icon glyphs (using the RP "interface" font)
                    .append(text("\ue004\ua040\ue005")
                            .font(key(NS, "interface"))
                            .color(TextColor.color(0xFFFFFF)))
                    // a little spacing + the label in classic gold & bold, not italic
                    .append(text("Fish Processing Station",
                            NamedTextColor.DARK_GRAY))
                    .decoration(TextDecoration.ITALIC, false);

    // Inputs: leftmost 2x3 (cols 0-1, rows 0-2)
    private static final int[] INPUT_SLOTS = {
            slot(0,0), slot(1,0),
            slot(0,1), slot(1,1),
            slot(0,2), slot(1,2)
    };

    // Outputs: rightmost 4x3 (cols 5-8, rows 0-2)
    private static final int[] OUTPUT_SLOTS = buildOutputSlots();

    // Knife at (3,0), Process at (3,2)
    private static final int KNIFE_SLOT   = slot(3,0);
    private static final int PROCESS_SLOT = slot(3,2);

    private final Map<BlockKey, TableState> tables = new java.util.concurrent.ConcurrentHashMap<>();

    private final JavaPlugin plugin;
    private final FishMaterialProvider provider;

    public FishProcessorUI(JavaPlugin plugin, FishMaterialProvider provider, Map<NamespacedKey, FishModel> fishModelMap) {
        this.plugin = plugin;
        this.provider = provider;
        this.fishModelMap = fishModelMap != null ? fishModelMap : new HashMap<>();
        this.IMMOVABLE_KEY = new NamespacedKey(plugin, "immovable");
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(null), SIZE, TITLE); // null => ephemeral
        paint(inv);
        player.openInventory(inv);
    }

    public void open(Player player, BlockKey key) {
        Inventory inv = Bukkit.createInventory(new Holder(key), SIZE, TITLE);
        paint(inv);
        loadStateInto(inv, stateFor(key)); // restore persisted contents
        player.openInventory(inv);
    }

    /* ---------------- layout helpers ---------------- */

    private static int slot(int col, int row) { return row * 9 + col; }

    private static int[] buildOutputSlots() {
        List<Integer> out = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int col = 5; col <= 8; col++) out.add(slot(col, row));
        }
        return out.stream().mapToInt(i -> i).toArray();
    }

    private void paint(Inventory inv) {
        ItemStack filler = emptySlotItem();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        // Clear interactive slots
        for (int s : INPUT_SLOTS) inv.setItem(s, null);
        for (int s : OUTPUT_SLOTS) inv.setItem(s, null);
        inv.setItem(KNIFE_SLOT, null);

        // Put real widgets back
        inv.setItem(PROCESS_SLOT, processButton());
    }

    private ItemStack processButton() {
        ItemStack it = new ItemStack(Material.KNOWLEDGE_BOOK); // GUI sprite carrier
        ItemMeta m = it.getItemMeta();

        // point to assets/longhardfish/items/icons/process-button.json
        m.setItemModel(new NamespacedKey(NS, "icons/process-button"));

        // optional: show a simple title; comment these two lines to fully hide tooltip
        m.displayName(net.kyori.adventure.text.Component.text("Process")
                .color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("Converts fish → materials")
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(net.kyori.adventure.text.Component.text("Consumes knife durability")
                .color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        m.lore(lore);

        // keep it immovable like other chrome
        m.getPersistentDataContainer().set(IMMOVABLE_KEY, PersistentDataType.BYTE, (byte)1);
        try { m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS); } catch (Throwable ignored) {}

        it.setItemMeta(m);
        return it;
    }

    /* ---------------- event wiring ---------------- */

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        if (e.getClickedInventory() == null) return;

        final int raw = e.getRawSlot();
        final boolean top = raw < SIZE;

        if (top) {
            if (raw == PROCESS_SLOT && e.getClick() == ClickType.LEFT) {
                e.setCancelled(true);
                if (e.getWhoClicked() instanceof Player p) processAll(p, e.getView().getTopInventory());
                return;
            }

            if (isOutputSlot(raw)) {
                return; // <-- no cancellation, let vanilla behavior happen
            }

            if (isInputSlot(raw) || raw == KNIFE_SLOT) {
                if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) e.setCancelled(true);
                return;
            }

            e.setCancelled(true);
            return;
        }

        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack moving = e.getCurrentItem();
            if (moving == null || moving.getType().isAir()) return;

            // We'll do custom routing
            e.setCancelled(true);

            Inventory topInv = e.getView().getTopInventory();

            if (isFishItem(moving)) {
                // fish → inputs then outputs
                routeStackIntoSlots(topInv, moving, INPUT_SLOTS);
                routeStackIntoSlots(topInv, moving, OUTPUT_SLOTS);
            } else if (isKnife(moving)) {
                // knife → knife slot then outputs
                tryPlaceKnifeInSlot(topInv, moving, KNIFE_SLOT);
                routeStackIntoSlots(topInv, moving, OUTPUT_SLOTS);
            } else {
                // everything else → outputs
                routeStackIntoSlots(topInv, moving, OUTPUT_SLOTS);
            }

            e.setCurrentItem(moving.getAmount() <= 0 ? null : moving);
            if (e.getWhoClicked() instanceof Player p) p.updateInventory();
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        for (int raw : e.getRawSlots()) {
            if (raw < SIZE) {
                boolean allowed = isInputSlot(raw) || isOutputSlot(raw) || raw == KNIFE_SLOT || raw == PROCESS_SLOT;
                if (!allowed) { e.setCancelled(true); return; }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        Inventory inv = e.getInventory();

        // Block-backed UI: persist and keep items in the table
        if (h.key != null) {
            saveStateFrom(inv, stateFor(h.key));
            return;
        }

        // Ephemeral (old behavior): give items back
        HumanEntity he = e.getPlayer();
        List<ItemStack> giveBack = new ArrayList<>();
        for (int s : INPUT_SLOTS) {
            ItemStack it = inv.getItem(s);
            if (it != null && it.getType() != Material.AIR) { giveBack.add(it); inv.setItem(s, null); }
        }
        ItemStack knife = inv.getItem(KNIFE_SLOT);
        if (knife != null && knife.getType() != Material.AIR) { giveBack.add(knife); inv.setItem(KNIFE_SLOT, null); }

        if (!giveBack.isEmpty() && he instanceof Player p) {
            for (ItemStack g : giveBack) {
                HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(g);
                overflow.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
            }
        }
    }

    private boolean isFishItem(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        String key = FishKeys.getFishKey(plugin, it);
        return key != null && !key.isBlank();
    }

    private boolean moveIntoInputs(Inventory top, ItemStack moving) {
        if (moving == null || moving.getType().isAir()) return false;
        int toMove = moving.getAmount();

        for (int s : INPUT_SLOTS) {
            if (toMove <= 0) break;
            ItemStack cur = top.getItem(s);
            if (cur == null || cur.getType().isAir()) continue;
            if (!canStack(cur, moving)) continue;
            int max = Math.min(cur.getMaxStackSize(), 64);
            int add = Math.min(toMove, max - cur.getAmount());
            if (add > 0) { cur.setAmount(cur.getAmount() + add); toMove -= add; }
        }

        for (int s : INPUT_SLOTS) {
            if (toMove <= 0) break;
            ItemStack cur = top.getItem(s);
            if (cur != null && !cur.getType().isAir()) continue;
            ItemStack place = moving.clone();
            int placeAmt = Math.min(place.getMaxStackSize(), Math.min(64, toMove));
            place.setAmount(placeAmt);
            top.setItem(s, place);
            toMove -= placeAmt;
        }

        moving.setAmount(toMove);
        return true;
    }

    /* ---------------- processing core ---------------- */

    private void processAll(Player player, Inventory inv) {
        TableState st = null;
        if (inv.getHolder() instanceof Holder h && h.key != null) {
            st = stateFor(h.key);
            st.processing = true;   // mark "in progress"
        }

        try {

            int totalFish = 0;
            for (int s : INPUT_SLOTS) {
                ItemStack it = inv.getItem(s);
                if (it == null || it.getType().isAir()) continue;
                String key = FishKeys.getFishKey(plugin, it);
                if (key == null || key.isBlank()) continue;
                totalFish += it.getAmount();
            }
            if (totalFish == 0) {
                player.sendMessage(ChatColor.GRAY + "[FishProc] Place fish into the input slots.");
                return;
            }

            ItemStack knife = inv.getItem(KNIFE_SLOT);
            if (!isKnife(knife)) {
                player.sendMessage(ChatColor.RED + "[FishProc] Insert a valid knife into the knife slot.");
                return;
            }

            int max = KnifeDurability.getMax(plugin, knife);
            int remaining = KnifeDurability.getRemaining(plugin, knife);
            if (max <= 0) {
                player.sendMessage(ChatColor.RED + "[FishProc] Knife has no durability.");
                return;
            }
            if (remaining <= 0) {
                player.sendMessage(ChatColor.RED + "[FishProc] Knife is broken. Repair or replace it.");
                return;
            }
            int durabilityBudget = remaining;
            int consumedThisRun = 0;

            // Pre-calculate ALL outputs and cache them to ensure consistency
            // Key: slot index, Value: list of (fish_count, cached_outputs)
            List<List<ItemStack>> preCalculatedOutputs = new ArrayList<>();
            Map<Material, Integer> requiredSpace = new HashMap<>();
            int totalFishToProcess = 0;
            boolean hasAnyOutput = false;

            for (int s : INPUT_SLOTS) {
                ItemStack in = inv.getItem(s);
                if (in == null || in.getType().isAir()) continue;
                String fk = FishKeys.getFishKey(plugin, in);
                if (fk == null || fk.isBlank()) continue;

                // Check if fish can be gutted
                NamespacedKey fishNsKey = NamespacedKey.fromString(fk);
                FishModel model = fishNsKey != null ? fishModelMap.get(fishNsKey) : null;
                if (model != null && !model.canBeGutted()) {
                    continue;
                }

                int amt = in.getAmount();
                for (int i = 0; i < amt; i++) {
                    List<ItemStack> outputs = provider.materialFor(fk, 1);
                    if (outputs == null || outputs.isEmpty()) continue;

                    // Cache these outputs for later use
                    preCalculatedOutputs.add(new ArrayList<>(outputs));

                    for (ItemStack out : outputs) {
                        if (out != null && !out.getType().isAir() && out.getAmount() > 0) {
                            hasAnyOutput = true;
                            Material mat = out.getType();
                            requiredSpace.merge(mat, out.getAmount(), Integer::sum);
                        }
                    }
                    totalFishToProcess++;
                }
            }

            if (!hasAnyOutput) {
                player.sendMessage(ChatColor.GRAY + "[FishProc] Nothing to produce from these fish.");
                return;
            }

            // Check if we have enough space for ALL materials
            // Use the actual output items to check capacity (accounts for custom metadata)
            for (List<ItemStack> outputs : preCalculatedOutputs) {
                for (ItemStack out : outputs) {
                    if (out == null || out.getType().isAir() || out.getAmount() <= 0) continue;

                    int available = calcCapacity(inv, out, OUTPUT_SLOTS);
                    if (available < out.getAmount()) {
                        player.sendMessage(ChatColor.RED + "[FishProc] Not enough output space. Clear some slots and try again.");
                        return;
                    }
                }
            }

            int processed = 0, produced = 0;
            boolean broke = false;
            int outputIndex = 0; // Track which pre-calculated output to use

            outer:
            for (int s : INPUT_SLOTS) {
                ItemStack in = inv.getItem(s);
                if (in == null || in.getType().isAir()) continue;
                String fk = FishKeys.getFishKey(plugin, in);
                if (fk == null || fk.isBlank()) continue;

                // Check if fish can be gutted
                NamespacedKey fishNsKey = NamespacedKey.fromString(fk);
                FishModel model = fishNsKey != null ? fishModelMap.get(fishNsKey) : null;
                if (model != null && !model.canBeGutted()) {
                    continue;
                }

                int amt = in.getAmount();
                while (amt > 0 && outputIndex < preCalculatedOutputs.size()) {
                    // Use pre-calculated outputs instead of calling provider again
                    List<ItemStack> outputs = preCalculatedOutputs.get(outputIndex);
                    outputIndex++;

                    if (outputs == null || outputs.isEmpty()) { amt--; continue; }

                    // Calculate total yield
                    int totalYield = 0;
                    for (ItemStack out : outputs) {
                        if (out != null && !out.getType().isAir() && out.getAmount() > 0) {
                            totalYield += out.getAmount();
                        }
                    }

                    if (totalYield <= 0) { amt--; continue; }

                    // consume 1 fish
                    amt--;
                    processed++;
                    produced += totalYield;

                    // Deposit outputs immediately to ensure space tracking is accurate
                    for (ItemStack outItem : outputs) {
                        if (outItem != null && !outItem.getType().isAir() && outItem.getAmount() > 0) {
                            depositMany(inv, outItem.clone(), outItem.getAmount(), OUTPUT_SLOTS);
                        }
                    }

                    int beforeRem = KnifeDurability.getRemaining(plugin, knife);

                    boolean justBroke = KnifeDurability.damageAndMaybeBreak(
                            plugin, player, inv, KNIFE_SLOT, knife, 1
                    );
                    if (justBroke) {
                        writeBack(inv, s, in, amt);
                        broke = true;
                        updateViewers(inv);
                        break outer;
                    } else {
                        inv.setItem(KNIFE_SLOT, knife); // force client refresh
                        updateViewers(inv);
                    }

                    int afterRem = justBroke ? 0 : KnifeDurability.getRemaining(plugin, knife);
                    if (afterRem < beforeRem) {
                        consumedThisRun++;
                        if (!justBroke && consumedThisRun >= durabilityBudget) {
                            writeBack(inv, s, in, amt);
                            updateViewers(inv);
                            break outer;
                        }
                    }

                    if (justBroke) {
                        writeBack(inv, s, in, amt);
                        broke = true;
                        updateViewers(inv);
                        break outer;
                    } else {
                        inv.setItem(KNIFE_SLOT, knife);
                        updateViewers(inv);
                    }
                }

                writeBack(inv, s, in, amt);
            }

            // Outputs are already deposited immediately during processing
            if (produced > 0) {
                player.playSound(player, Sound.UI_LOOM_TAKE_RESULT, 1.0f, 1.1f);

                // Count total items in output slots
                int itemsInTable = 0;
                for (int slot : OUTPUT_SLOTS) {
                    ItemStack stack = inv.getItem(slot);
                    if (stack != null && !stack.getType().isAir()) {
                        itemsInTable += stack.getAmount();
                    }
                }

                int left = Math.max(0, totalFish - processed);
                player.sendMessage(ChatColor.GOLD + "" + processed + " Fish " + ChatColor.GREEN + "processed. " +
                        ChatColor.GOLD + itemsInTable + " Items " + ChatColor.GREEN + "left in table.");
                if (broke) {
                    player.sendMessage(ChatColor.RED + "Knife broken. " + ChatColor.GOLD + left + " Fish " + ChatColor.RED + "left in table.");
                    player.playSound(player, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.9f);
                }
            } else {
                player.sendMessage(ChatColor.GRAY + "Nothing to produce.");
            }

            updateViewers(inv); // final safety refresh

        } finally {
            // ALWAYS clear processing flag and persist the current inventory snapshot
            if (st != null) {
                st.processing = false;
                saveStateFrom(inv, st);
            }
        }
    }


    /* ---------------- helpers ---------------- */

    private static void writeBack(Inventory inv, int slot, ItemStack in, int remaining) {
        if (remaining <= 0) {
            inv.setItem(slot, null);
        } else {
            ItemStack copy = in.clone();
            copy.setAmount(remaining);
            inv.setItem(slot, copy);
        }
    }

    private static void updateViewers(Inventory inv) {
        for (HumanEntity he : inv.getViewers()) {
            if (he instanceof Player p) p.updateInventory();
        }
    }

    private static int maxDamageFor(ItemStack tool, Damageable dmg) {
        if (dmg != null) {
            try { return (int) dmg.getClass().getMethod("getMaxDamage").invoke(dmg); }
            catch (Throwable ignore) {}
        }
        return tool.getType().getMaxDurability();
    }

    private static void depositMany(Inventory inv, ItemStack proto, int amount, int[] slots) {
        int left = amount;
        int maxStack = Math.min(proto.getMaxStackSize(), 64);
        for (int s : slots) {
            if (left <= 0) break;
            ItemStack cur = inv.getItem(s);
            if (cur == null || cur.getType().isAir()) continue;
            if (!canStack(cur, proto)) continue;
            int add = Math.min(left, maxStack - cur.getAmount());
            if (add > 0) { cur.setAmount(cur.getAmount() + add); left -= add; }
        }
        for (int s : slots) {
            if (left <= 0) break;
            ItemStack cur = inv.getItem(s);
            if (cur != null && !cur.getType().isAir()) continue;
            ItemStack place = proto.clone();
            place.setAmount(Math.min(maxStack, left));
            inv.setItem(s, place);
            left -= place.getAmount();
        }
    }

    private static boolean canStack(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        ItemMeta am = a.getItemMeta(), bm = b.getItemMeta();
        return Objects.equals(am, bm);
    }

    private static int calcCapacity(Inventory inv, ItemStack kind, int[] slots) {
        int cap = 0, maxStack = Math.min(kind.getMaxStackSize(), 64);
        for (int s : slots) {
            ItemStack cur = inv.getItem(s);
            if (cur == null || cur.getType() == Material.AIR) cap += maxStack;
            else if (canStack(cur, kind)) cap += Math.max(0, maxStack - cur.getAmount());
        }
        return cap;
    }

    private boolean isInputSlot(int rawSlot) {
        for (int s : INPUT_SLOTS) if (s == rawSlot) return true;
        return false;
    }
    private boolean isOutputSlot(int rawSlot) {
        for (int s : OUTPUT_SLOTS) if (s == rawSlot) return true;
        return false;
    }

    private boolean isKnife(ItemStack it) {
        return KnifeDurability.isKnife(plugin, it);
    }

    private static final class Holder implements InventoryHolder {
        final BlockKey key; // null means "ephemeral" GUI
        Holder() { this.key = null; }
        Holder(BlockKey key) { this.key = key; }
        @Override public Inventory getInventory() { return null; }
    }

    private void hideTooltipTop(Inventory gui, int slot) {
        ItemStack s = gui.getItem(slot);
        if (s == null) return;
        ItemMeta m = s.getItemMeta();
        if (m == null) return;
        m.setHideTooltip(true);  // Paper 1.21.7
        m.displayName(null);
        m.lore(null);
        s.setItemMeta(m);
        gui.setItem(slot, s);
    }

    private ItemStack emptySlotItem() {
        ItemStack it = new ItemStack(Material.COD);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;

        // Use Bukkit NamespacedKey here:
        m.setItemModel(new NamespacedKey(NS, NAV_EMPTY_TEX));

        m.setHideTooltip(true);
        m.displayName(null);
        m.lore(null);

        m.getPersistentDataContainer().set(IMMOVABLE_KEY, PersistentDataType.BYTE, (byte)1);

        try { m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS); } catch (Throwable ignored) {}

        it.setItemMeta(m);
        return it;
    }

    /** Merge as much of `moving` as possible into `slots` (stack-merge then fill empties). */
    private static void routeStackIntoSlots(Inventory top, ItemStack moving, int[] slots) {
        if (moving == null || moving.getType().isAir()) return;
        int toMove = moving.getAmount();
        if (toMove <= 0) return;

        // a) merge into existing stacks
        for (int s : slots) {
            if (toMove <= 0) break;
            ItemStack cur = top.getItem(s);
            if (cur == null || cur.getType().isAir()) continue;
            if (!canStack(cur, moving)) continue;
            int max = Math.min(cur.getMaxStackSize(), 64);
            int add = Math.min(toMove, max - cur.getAmount());
            if (add > 0) { cur.setAmount(cur.getAmount() + add); toMove -= add; }
        }

        // b) fill empties
        for (int s : slots) {
            if (toMove <= 0) break;
            ItemStack cur = top.getItem(s);
            if (cur != null && !cur.getType().isAir()) continue;
            ItemStack place = moving.clone();
            int placeAmt = Math.min(place.getMaxStackSize(), Math.min(64, toMove));
            place.setAmount(placeAmt);
            top.setItem(s, place);
            toMove -= placeAmt;
        }

        moving.setAmount(toMove);
    }

    /** Try to place exactly one knife into the KNIFE_SLOT if it's empty; otherwise do nothing. */
    private void tryPlaceKnifeInSlot(Inventory top, ItemStack moving, int knifeSlot) {
        if (moving == null || moving.getType().isAir()) return;
        if (!isKnife(moving)) return;

        ItemStack cur = top.getItem(knifeSlot);
        if (cur == null || cur.getType().isAir()) {
            ItemStack one = moving.clone();
            one.setAmount(1);
            top.setItem(knifeSlot, one);
            moving.setAmount(moving.getAmount() - 1);
        }
    }

    public static final class BlockKey {
        public final java.util.UUID worldId;
        public final int x, y, z;

        public BlockKey(java.util.UUID worldId, int x, int y, int z) {
            this.worldId = worldId; this.x = x; this.y = y; this.z = z;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockKey)) return false;
            BlockKey k = (BlockKey) o;
            return x == k.x && y == k.y && z == k.z &&
                    java.util.Objects.equals(worldId, k.worldId);
        }
        @Override public int hashCode() {
            return java.util.Objects.hash(worldId, x, y, z);
        }
    }

    // Persistent state per table block
    private static final class TableState {
        final ItemStack[] inputs  = new ItemStack[INPUT_SLOTS.length];  // 6
        final ItemStack[] outputs = new ItemStack[OUTPUT_SLOTS.length]; // 12
        ItemStack knife = null;
        boolean processing = false; // set true while processAll runs
    }

    private TableState stateFor(BlockKey key) {
        return tables.computeIfAbsent(key, k -> new TableState());
    }

    private void loadStateInto(Inventory inv, TableState st) {
        for (int s : INPUT_SLOTS)  inv.setItem(s, null);
        for (int s : OUTPUT_SLOTS) inv.setItem(s, null);
        inv.setItem(KNIFE_SLOT, null);

        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            ItemStack it = st.inputs[i];
            if (it != null && !it.getType().isAir()) inv.setItem(INPUT_SLOTS[i], it.clone());
        }
        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            ItemStack it = st.outputs[i];
            if (it != null && !it.getType().isAir()) inv.setItem(OUTPUT_SLOTS[i], it.clone());
        }
        if (st.knife != null && !st.knife.getType().isAir()) inv.setItem(KNIFE_SLOT, st.knife.clone());
    }

    private void saveStateFrom(Inventory inv, TableState st) {
        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            ItemStack it = inv.getItem(INPUT_SLOTS[i]);
            st.inputs[i] = (it == null || it.getType().isAir()) ? null : it.clone();
        }
        for (int i = 0; i < OUTPUT_SLOTS.length; i++) {
            ItemStack it = inv.getItem(OUTPUT_SLOTS[i]);
            st.outputs[i] = (it == null || it.getType().isAir()) ? null : it.clone();
        }
        ItemStack knife = inv.getItem(KNIFE_SLOT);
        st.knife = (knife == null || knife.getType().isAir()) ? null : knife.clone();
    }

    public void handleBlockBreak(World world, BlockKey key, Location dropAt) {
        TableState st = tables.remove(key);
        if (st == null) return;

        // Close any open viewers of this table to avoid dupes
        for (Player p : world.getPlayers()) {
            var view = p.getOpenInventory();
            if (view != null && view.getTopInventory() != null &&
                    view.getTopInventory().getHolder() instanceof Holder h &&
                    key.equals(h.key)) {
                p.closeInventory();
            }
        }

        if (st.processing) {
            // Lose everything except the knife (anti-dupe rule)
            if (st.knife != null && !st.knife.getType().isAir()) {
                world.dropItemNaturally(dropAt, st.knife.clone());
            }
            java.util.Arrays.fill(st.inputs, null);
            java.util.Arrays.fill(st.outputs, null);
            st.knife = null;
            return;
        }

        // Not processing: drop all like a chest
        for (ItemStack it : st.inputs)  if (it != null && !it.getType().isAir()) world.dropItemNaturally(dropAt, it.clone());
        for (ItemStack it : st.outputs) if (it != null && !it.getType().isAir()) world.dropItemNaturally(dropAt, it.clone());
        if (st.knife != null && !st.knife.getType().isAir()) world.dropItemNaturally(dropAt, st.knife.clone());

        java.util.Arrays.fill(st.inputs, null);
        java.util.Arrays.fill(st.outputs, null);
        st.knife = null;
    }

    private static BlockKey blockKeyFrom(Location loc) {
        World w = loc.getWorld();
        return new BlockKey(
                (w != null ? w.getUID() : new java.util.UUID(0,0)),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
        );
    }

    // open at a specific block location
    public void openAt(Player player, Location loc) {
        open(player, blockKeyFrom(loc)); // delegate to the state-loading overload
    }

    // convenience
    public void openAt(Player player, Block block) {
        openAt(player, block.getLocation());
    }

    public void onTableBroken(org.bukkit.block.Block block) {
        if (block == null) return;
        org.bukkit.Location loc = block.getLocation();
        handleBlockBreak(block.getWorld(), blockKeyFrom(loc), loc.toCenterLocation());
    }

    // Persistence methods for saving/loading table states across restarts
    public void saveToDisk() {
        File dataFile = new File(plugin.getDataFolder(), "gutting_tables.yml");
        FileConfiguration config = new YamlConfiguration();

        for (Map.Entry<BlockKey, TableState> entry : tables.entrySet()) {
            BlockKey key = entry.getKey();
            TableState state = entry.getValue();

            String path = key.worldId.toString() + "." + key.x + "." + key.y + "." + key.z;

            // Save inputs
            for (int i = 0; i < state.inputs.length; i++) {
                if (state.inputs[i] != null && !state.inputs[i].getType().isAir()) {
                    config.set(path + ".inputs." + i, state.inputs[i]);
                }
            }

            // Save outputs
            for (int i = 0; i < state.outputs.length; i++) {
                if (state.outputs[i] != null && !state.outputs[i].getType().isAir()) {
                    config.set(path + ".outputs." + i, state.outputs[i]);
                }
            }

            // Save knife
            if (state.knife != null && !state.knife.getType().isAir()) {
                config.set(path + ".knife", state.knife);
            }
        }

        try {
            config.save(dataFile);
            plugin.getLogger().info("Saved " + tables.size() + " gutting table states");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save gutting table states: " + e.getMessage());
        }
    }

    public void loadFromDisk() {
        File dataFile = new File(plugin.getDataFolder(), "gutting_tables.yml");
        if (!dataFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        int loaded = 0;

        for (String worldIdStr : config.getKeys(false)) {
            java.util.UUID worldId;
            try {
                worldId = java.util.UUID.fromString(worldIdStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid world UUID in gutting_tables.yml: " + worldIdStr);
                continue;
            }

            ConfigurationSection worldSection = config.getConfigurationSection(worldIdStr);
            if (worldSection == null) continue;

            for (String xStr : worldSection.getKeys(false)) {
                ConfigurationSection xSection = worldSection.getConfigurationSection(xStr);
                if (xSection == null) continue;

                for (String yStr : xSection.getKeys(false)) {
                    ConfigurationSection ySection = xSection.getConfigurationSection(yStr);
                    if (ySection == null) continue;

                    for (String zStr : ySection.getKeys(false)) {
                        int x, y, z;
                        try {
                            x = Integer.parseInt(xStr);
                            y = Integer.parseInt(yStr);
                            z = Integer.parseInt(zStr);
                        } catch (NumberFormatException e) {
                            continue;
                        }

                        BlockKey key = new BlockKey(worldId, x, y, z);
                        TableState state = new TableState();

                        ConfigurationSection tableSection = ySection.getConfigurationSection(zStr);
                        if (tableSection == null) continue;

                        // Load inputs
                        ConfigurationSection inputsSection = tableSection.getConfigurationSection("inputs");
                        if (inputsSection != null) {
                            for (String slotStr : inputsSection.getKeys(false)) {
                                try {
                                    int slot = Integer.parseInt(slotStr);
                                    if (slot >= 0 && slot < state.inputs.length) {
                                        state.inputs[slot] = inputsSection.getItemStack(slotStr);
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }

                        // Load outputs
                        ConfigurationSection outputsSection = tableSection.getConfigurationSection("outputs");
                        if (outputsSection != null) {
                            for (String slotStr : outputsSection.getKeys(false)) {
                                try {
                                    int slot = Integer.parseInt(slotStr);
                                    if (slot >= 0 && slot < state.outputs.length) {
                                        state.outputs[slot] = outputsSection.getItemStack(slotStr);
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }

                        // Load knife
                        if (tableSection.contains("knife")) {
                            state.knife = tableSection.getItemStack("knife");
                        }

                        tables.put(key, state);
                        loaded++;
                    }
                }
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " gutting table states");
    }
}