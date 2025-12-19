package org.aincraft.gui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.StorageGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.kyori.adventure.key.Key.key;
import static net.kyori.adventure.text.Component.text;

/**
 * TackleBox GUI using Triumph Teams GUI library.
 * Creates a 54-slot StorageGui with custom font title and slot restrictions.
 */
public final class TackleBoxGui {
    private static final String NS = "longhardfish";
    public static final Component TITLE =
            text("\ue003\ua030").font(key(NS, "interface")).color(TextColor.color(0xFFFFFF));

    // Decorative blocked slots in a 54-slot chest (0..53)
    private static final Set<Integer> BLOCKED_SLOTS = new java.util.LinkedHashSet<>();
    static {
        BLOCKED_SLOTS.addAll(java.util.List.of(7, 8, 16, 25, 26, 34));
        BLOCKED_SLOTS.addAll(IntStream.rangeClosed(36, 53).boxed().collect(Collectors.toSet()));
    }

    // Fish-only: 9–15, 18–24, 27–33
    private static final Set<Integer> FISH_SLOTS = new java.util.LinkedHashSet<>();
    static {
        FISH_SLOTS.addAll(IntStream.rangeClosed(9, 15).boxed().toList());
        FISH_SLOTS.addAll(IntStream.rangeClosed(18, 24).boxed().toList());
        FISH_SLOTS.addAll(IntStream.rangeClosed(27, 33).boxed().toList());
    }

    // Rod-only slot
    private static final int ROD_SLOT = 17;

    // Bait slots (bin + transfer slot)
    private static final int BAIT_SLOT = 35;
    private static final Set<Integer> BAIT_SLOTS = new java.util.LinkedHashSet<>();
    static {
        BAIT_SLOTS.addAll(IntStream.rangeClosed(0, 6).boxed().toList());
        BAIT_SLOTS.add(BAIT_SLOT);
    }

    private TackleBoxGui() {}

    /**
     * Create a new StorageGui for the TackleBox.
     *
     * @param plugin  the plugin instance
     * @param handler click/drag handler for custom logic
     * @return configured StorageGui ready to be populated and opened
     */
    public static StorageGui create(JavaPlugin plugin, TackleBoxClickHandler handler) {
        StorageGui gui = Gui.storage()
                .title(TITLE)
                .rows(6)
                .create();

        // Fix: Replace internal inventory with one that has proper Component title
        // Triumph GUI doesn't preserve custom font styling, so we inject our own inventory
        try {
            var inventoryField = gui.getClass().getSuperclass().getDeclaredField("inventory");
            inventoryField.setAccessible(true);
            org.bukkit.inventory.Inventory properInventory = org.bukkit.Bukkit.createInventory(
                    gui.getInventory().getHolder(),
                    54,
                    TITLE
            );
            inventoryField.set(gui, properInventory);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to fix TackleBox title: " + e.getMessage());
        }

        // Set up blocked slot items
        NamespacedKey immovableKey = new NamespacedKey(plugin, "immovable");
        ItemStack blocker = makeBlockedIcon(immovableKey);

        for (int slot : BLOCKED_SLOTS) {
            gui.setItem(slot, new GuiItem(blocker.clone(), event -> event.setCancelled(true)));
        }

        // Default click action handles all the complex logic
        gui.setDefaultClickAction(event -> handler.handleClick(event, gui));

        // Drag action
        gui.setDragAction(event -> handler.handleDrag(event, gui));

        return gui;
    }

    private static ItemStack makeBlockedIcon(NamespacedKey immovableKey) {
        ItemStack it = new ItemStack(Material.PAPER);
        var meta = it.getItemMeta();
        meta.displayName(null);
        meta.lore(null);
        meta.setHideTooltip(true);
        meta.setItemModel(NamespacedKey.fromString("longhardfish:icons/empty"));
        meta.getPersistentDataContainer().set(immovableKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    // -------------------- Slot Accessors --------------------

    public static Set<Integer> getBlockedSlots() {
        return BLOCKED_SLOTS;
    }

    public static Set<Integer> getFishSlots() {
        return FISH_SLOTS;
    }

    public static Set<Integer> getBaitSlots() {
        return BAIT_SLOTS;
    }

    public static int getRodSlot() {
        return ROD_SLOT;
    }

    public static int getBaitBinSlot() {
        return BAIT_SLOT;
    }

    /**
     * Functional interface for click/drag handling delegation.
     */
    public interface TackleBoxClickHandler {
        void handleClick(InventoryClickEvent event, StorageGui gui);

        default void handleDrag(InventoryDragEvent event, StorageGui gui) {
            // Default implementation can be overridden
        }
    }
}
