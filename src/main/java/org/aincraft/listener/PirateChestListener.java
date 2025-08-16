package org.aincraft.listener;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Chest;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import static net.kyori.adventure.key.Key.*;
import static net.kyori.adventure.text.Component.*;

public class PirateChestListener implements Listener, CommandExecutor {

    private final JavaPlugin plugin;
    private final NamespacedKey IMMOVABLE_KEY;

    public PirateChestListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.IMMOVABLE_KEY = new NamespacedKey(plugin, "immovable");
    }

    /** Simple marker holder so we can recognize our GUI in events */
    private static final class PirateGuiHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    @Override
    public boolean onCommand(
            @org.jetbrains.annotations.NotNull CommandSender sender,
            @org.jetbrains.annotations.NotNull Command command,
            @org.jetbrains.annotations.NotNull String label,
            @org.jetbrains.annotations.NotNull String[] args
    ) {
        if (sender instanceof Player player) {
            Inventory gui = Bukkit.createInventory(
                    new PirateGuiHolder(),
                    54,
                    text("\ue001\ua020\ue002\ua021").font(key("longhardfish", "interface")).color(TextColor.color(0xFFFFFF))
            );
            // Create an immovable item (use your textured item/material here)
            ItemStack title = new ItemStack(Material.EMERALD);
            title.setData(DataComponentTypes.ITEM_MODEL, Key.key("longhardfish", "icons/moon-icon"));
            ItemMeta meta = title.getItemMeta();
            meta.displayName(text(""));
            // Tag as immovable
            meta.getPersistentDataContainer().set(IMMOVABLE_KEY, PersistentDataType.BYTE, (byte) 1);
            title.setItemMeta(meta);

            gui.setItem(2, title); // center of a 6-row chest
            player.openInventory(gui);
        }
        return false;
    }

    private boolean isOurGui(InventoryView view) {
        return view.getTopInventory().getHolder() instanceof PirateGuiHolder;
    }

    private boolean isImmovable(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(IMMOVABLE_KEY, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (!isOurGui(e.getView())) return;

        int topSize = e.getView().getTopInventory().getSize();
        int raw = e.getRawSlot();

        // Block interactions that touch an immovable item in the top inventory
        if (raw < topSize) {
            ItemStack inSlot = e.getView().getTopInventory().getItem(raw);
            if (isImmovable(inSlot)) {
                e.setCancelled(true);
                return;
            }
        }

        // Prevent shift-clicking the immovable item out
        if (e.isShiftClick()
                && e.getClickedInventory() == e.getView().getTopInventory()
                && isImmovable(e.getCurrentItem())) {
            e.setCancelled(true);
            return;
        }

        // Prevent hotbar swaps onto an immovable slot
        if (e.getClick() == ClickType.NUMBER_KEY && raw < topSize) {
            ItemStack inSlot = e.getView().getTopInventory().getItem(raw);
            if (isImmovable(inSlot)) {
                e.setCancelled(true);
                return;
            }
        }

        // Prevent double-click “collect to cursor” affecting immovable items
        if (e.getClick() == ClickType.DOUBLE_CLICK && isImmovable(e.getCursor())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!isOurGui(e.getView())) return;
        int topSize = e.getView().getTopInventory().getSize();

        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                ItemStack inSlot = e.getView().getTopInventory().getItem(raw);
                if (isImmovable(inSlot)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }
}