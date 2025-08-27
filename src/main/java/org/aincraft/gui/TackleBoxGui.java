package org.aincraft.gui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.BiConsumer;

import static net.kyori.adventure.key.Key.key;
import static net.kyori.adventure.text.Component.text;

/**
 * Simple 54-slot panel with a custom-font title using \ue001\ua030.
 */
public final class TackleBoxGui {
    private static final String NS = "longhardfish"; // your resource-pack namespace
    private static final Component TITLE =
            text("\ue003\ua030").font(key(NS, "interface")).color(TextColor.color(0xFFFFFF));

    private TackleBoxGui() {}

    /** Holder so you can detect this GUI in listeners if needed. */
    public static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    /**
     * Open the panel for a player.
     *
     * @param plugin your plugin instance
     * @param player the viewer
     * @param mask   optional: run before opening (e.g., backup+clear inventory)
     * @return the created Inventory
     */
    public static Inventory open(JavaPlugin plugin, Player player, BiConsumer<Player, Inventory> mask) {
        Holder holder = new Holder();
        Inventory gui = Bukkit.createInventory(holder, 54, TITLE);
        holder.inv = gui;

        if (mask != null) {
            try { mask.accept(player, gui); } catch (Throwable ignored) {}
        }

        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(gui));
        return gui;
    }

    /** Convenience: check if an inventory is this panel. */
    public static boolean isPanel(Inventory inv) {
        return inv != null && inv.getHolder() instanceof Holder;
    }
}

