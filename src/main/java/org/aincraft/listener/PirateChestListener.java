package org.aincraft.listener;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;
import org.aincraft.gui.GuiItemSlot;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.entity.EntityPickupItemEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.kyori.adventure.key.Key.*;
import static net.kyori.adventure.text.Component.*;

public class PirateChestListener implements Listener, CommandExecutor {

    private final JavaPlugin plugin;
    private final NamespacedKey IMMOVABLE_KEY;

    private final Map<UUID, ItemStack[]> savedStorage = new HashMap<>();
    private final ItemStack MASK_ITEM;

    public PirateChestListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.IMMOVABLE_KEY = new NamespacedKey(plugin, "immovable");
        this.MASK_ITEM = createMaskItem();
    }

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
            applyInventoryMask(player);
            GuiItemSlot.putImmovableIcon(gui,7,Material.COD,Key.key("longhardfish", "icons/morning-icon"),IMMOVABLE_KEY);
            GuiItemSlot.putImmovableIcon(gui,8,Material.COD,Key.key("longhardfish", "icons/sun-icon"),IMMOVABLE_KEY);
            GuiItemSlot.putImmovableIcon(gui,17,Material.COD,Key.key("longhardfish", "icons/evening-icon"),IMMOVABLE_KEY);
            GuiItemSlot.putImmovableIcon(gui,26,Material.COD,Key.key("longhardfish", "icons/moon-icon"),IMMOVABLE_KEY);

            GuiItemSlot.putImmovableIcon(gui,35,Material.COD,Key.key("longhardfish", "icons/full_moon"),IMMOVABLE_KEY);
            GuiItemSlot.putImmovableIcon(gui,44,Material.COD,Key.key("longhardfish", "icons/waning_gibbous"),IMMOVABLE_KEY);
            GuiItemSlot.putImmovableIcon(gui,53,Material.COD,Key.key("longhardfish", "icons/last_quarter"),IMMOVABLE_KEY);
            GuiItemSlot.putImmovableIcon(player.getInventory(),GuiItemSlot.main(0,7),Material.COD,Key.key("longhardfish", "icons/waning_crescent"),IMMOVABLE_KEY);
            GuiItemSlot.putImmovableIcon(player.getInventory(),GuiItemSlot.hotbar(8),Material.COD,Key.key("longhardfish", "icons/new_moon"),IMMOVABLE_KEY);
            GuiItemSlot.putImmovableIcon(player.getInventory(),GuiItemSlot.main(1,8),Material.COD,Key.key("longhardfish", "icons/waxing_crescent"),IMMOVABLE_KEY);
            GuiItemSlot.putImmovableIcon(player.getInventory(),GuiItemSlot.main(0,6),Material.COD,Key.key("longhardfish", "icons/first_quarter"),IMMOVABLE_KEY);
            GuiItemSlot.putImmovableIcon(player.getInventory(),GuiItemSlot.main(0,8),Material.COD,Key.key("longhardfish", "icons/waxing_gibbous"),IMMOVABLE_KEY);

            GuiItemSlot.putImmovableIcon(gui,45,Material.COD,Key.key("longhardfish", "icons/rarity-rare"),IMMOVABLE_KEY);

            GuiItemSlot.putImmovableIcon(player.getInventory(),GuiItemSlot.main(1,0),Material.COD,Key.key("longhardfish", "icons/4-star"),IMMOVABLE_KEY);

            GuiItemSlot.putImmovableIcon(gui,9,Material.COD,Key.key("longhardfish", "offsets/abyssal-jetfin-offset"),IMMOVABLE_KEY);

            GuiItemSlot.putImmovableIcon(gui,0,Material.COD,Key.key("longhardfish", "icons/fish-menu"),IMMOVABLE_KEY);

            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(1,3), Material.COD, Key.key("longhardfish", "icons/env-river"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(1,1), Material.COD, Key.key("longhardfish", "icons/env-shallow-saltwater"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(1,2), Material.COD, Key.key("longhardfish", "icons/env-cold-saltwater"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(2,0), Material.COD, Key.key("longhardfish", "icons/env-war-saltwater"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(2,1), Material.COD, Key.key("longhardfish", "icons/env-caves"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(2,2), Material.COD, Key.key("longhardfish", "icons/env-weird"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(2,3), Material.COD, Key.key("longhardfish", "icons/env-mountain"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(1,6), Material.COD, Key.key("longhardfish", "icons/env-hot-places"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(1,7), Material.COD, Key.key("longhardfish", "icons/env-woodlands"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(1,5), Material.COD, Key.key("longhardfish", "icons/env-cold-places"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(2,6), Material.COD, Key.key("longhardfish", "icons/env-rain-forest"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(2,7), Material.COD, Key.key("longhardfish", "icons/env-flat-lands"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(2,8), Material.COD, Key.key("longhardfish", "icons/env-wet-lands"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(2,5), Material.COD, Key.key("longhardfish", "icons/env-deep-saltwater"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(1,4), Material.COD, Key.key("longhardfish", "icons/env-deep-saltwater"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(2,4), Material.COD, Key.key("longhardfish", "icons/env-deep-saltwater"), IMMOVABLE_KEY, false);

            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(0,3), Material.COD, Key.key("longhardfish", "icons/bait-icon-full"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(0,4), Material.COD, Key.key("longhardfish", "icons/description-gem-icon"), IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(), GuiItemSlot.main(0,5), Material.COD, Key.key("longhardfish", "icons/sunny-icon"), IMMOVABLE_KEY, false);

            GuiItemSlot.putImmovableIcon(gui,10,Material.COD,Key.key("longhardfish", "albino-grayling"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,11,Material.COD,Key.key("longhardfish", "albino-kreken"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,12,Material.COD,Key.key("longhardfish", "alto-falloshimp"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,13,Material.COD,Key.key("longhardfish", "amethysite-minnoe"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,14,Material.COD,Key.key("longhardfish", "amethyst-cichilid"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,15,Material.COD,Key.key("longhardfish", "amethyst-gripper"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,16,Material.COD,Key.key("longhardfish", "amethyst-riptideon"),IMMOVABLE_KEY,false);

            GuiItemSlot.putImmovableIcon(gui,19,Material.COD,Key.key("longhardfish", "amethyst-sculblade"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,20,Material.COD,Key.key("longhardfish", "anchor-mimook"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,21,Material.COD,Key.key("longhardfish", "ancient-boopsnoot"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,22,Material.COD,Key.key("longhardfish", "ancient-dorado"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,23,Material.COD,Key.key("longhardfish", "ancient-elara"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,24,Material.COD,Key.key("longhardfish", "aquamarine-estuary"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,25,Material.COD,Key.key("longhardfish", "arctic-bylowin"),IMMOVABLE_KEY,false);

            GuiItemSlot.putImmovableIcon(gui,28,Material.COD,Key.key("longhardfish", "arctic-grayling"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,29,Material.COD,Key.key("longhardfish", "arctic-horizonfin"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,30,Material.COD,Key.key("longhardfish", "ascended-puckerfish"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,31,Material.COD,Key.key("longhardfish", "astrological-mindwatcher"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,32,Material.COD,Key.key("longhardfish", "autoboros"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,33,Material.COD,Key.key("longhardfish", "azalea-grubshark"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,34,Material.COD,Key.key("longhardfish", "bean-gripper"),IMMOVABLE_KEY,false);

            GuiItemSlot.putImmovableIcon(gui,37,Material.COD,Key.key("longhardfish", "beeper-sneepihr"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,38,Material.COD,Key.key("longhardfish", "berserker-doomageddon"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,39,Material.COD,Key.key("longhardfish", "betafish"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,40,Material.COD,Key.key("longhardfish", "bigbyte-piranha"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,41,Material.COD,Key.key("longhardfish", "bismuth-estuary"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,42,Material.COD,Key.key("longhardfish", "bloodfin-phyra"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,43,Material.COD,Key.key("longhardfish", "blue-jellyjelly"),IMMOVABLE_KEY,false);

            GuiItemSlot.putImmovableIcon(gui,46,Material.COD,Key.key("longhardfish", "bola-claymore"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,47,Material.COD,Key.key("longhardfish", "bone-gripper"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,48,Material.COD,Key.key("longhardfish", "boopsnoototron"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,49,Material.COD,Key.key("longhardfish", "boreal-albacore"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,50,Material.COD,Key.key("longhardfish", "anchor-mimook"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,51,Material.COD,Key.key("longhardfish", "ancient-boopsnoot"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(gui,52,Material.COD,Key.key("longhardfish", "ancient-dorado"),IMMOVABLE_KEY,false);

            GuiItemSlot.putImmovableIcon(player.getInventory(),GuiItemSlot.hotbar(6),Material.COD,Key.key("longhardfish", "icons/next-1"),IMMOVABLE_KEY,false);
            GuiItemSlot.putImmovableIcon(player.getInventory(),GuiItemSlot.hotbar(7),Material.COD,Key.key("longhardfish", "icons/next-all"),IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(),GuiItemSlot.hotbar(2),Material.COD,Key.key("longhardfish", "icons/previous-1"),IMMOVABLE_KEY, false);
            GuiItemSlot.putImmovableIcon(player.getInventory(),GuiItemSlot.hotbar(1),Material.COD,Key.key("longhardfish", "icons/previous-all"),IMMOVABLE_KEY, false);

            Bukkit.getScheduler().runTask(plugin,()->{
                player.openInventory(gui);
            });
        }
        return false;
    }

    private boolean isOurGui(InventoryView view) {
        return view.getTopInventory().getHolder() instanceof PirateGuiHolder;
    }

    private ItemStack createMaskItem() {
        ItemStack it = new ItemStack(Material.PAPER); // base doesn't matter; we'll model-swap it
        // Make it visually invisible via your RP (transparent 16x16):
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("longhardfish", "icons/empty"));
        // Hide tooltip entirely:
        it.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        it.editMeta(m -> {
            m.displayName(null);
            m.getPersistentDataContainer().set(IMMOVABLE_KEY, PersistentDataType.BYTE, (byte) 1);
        });
        return it;
    }

    private void applyInventoryMask(Player p) {
        UUID u = p.getUniqueId();
        if (savedStorage.containsKey(u)) return; // already masked
        PlayerInventory inv = p.getInventory();

        // snapshot only the 36 storage slots (not armor/offhand)
        ItemStack[] snapshot = inv.getStorageContents();
        ItemStack[] copy = new ItemStack[snapshot.length];
        for (int i = 0; i < snapshot.length; i++) copy[i] = snapshot[i] == null ? null : snapshot[i].clone();
        savedStorage.put(u, copy);

        // fill with mask items
        ItemStack[] mask = new ItemStack[snapshot.length];
        for (int i = 0; i < mask.length; i++) mask[i] = MASK_ITEM.clone();
        inv.setStorageContents(mask);
        p.updateInventory();
    }

    private void restoreInventoryMask(Player p) {
        UUID u = p.getUniqueId();
        ItemStack[] saved = savedStorage.remove(u);
        if (saved != null) {
            p.getInventory().setStorageContents(saved);
            p.updateInventory();
        }
    }

    private boolean isMasked(Player p) {
        return savedStorage.containsKey(p.getUniqueId());
    }

    private boolean isImmovable(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte flag = item.getItemMeta().getPersistentDataContainer().get(IMMOVABLE_KEY, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isOurGui(e.getView())) return;

        final int topSize = e.getView().getTopInventory().getSize();
        final int raw = e.getRawSlot();

        // Block ALL interactions with bottom inventory while GUI open
        if (raw >= topSize || e.getClickedInventory() == e.getView().getBottomInventory()) {
            e.setCancelled(true);
            return;
        }

        // Block shift-click transfers, hotbar number key swaps, double-click collect, and any DROP actions
        if (e.isShiftClick()
                || e.getClick() == ClickType.NUMBER_KEY
                || e.getClick() == ClickType.DOUBLE_CLICK
                || e.getAction().name().contains("DROP")) {
            e.setCancelled(true);
            return;
        }

        if (e.getAction() == InventoryAction.CLONE_STACK) {
            e.setCancelled(true);
            return;
        }

        // Extra safety: if a top slot contains an immovable marker, block
        if (raw < topSize) {
            ItemStack inTop = e.getView().getTopInventory().getItem(raw);
            if (isImmovable(inTop)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!isOurGui(e.getView())) return;
        int topSize = e.getView().getTopInventory().getSize();

        for (int raw : e.getRawSlots()) {
            // If drag touches bottom inventory → cancel
            if (raw >= topSize) {
                e.setCancelled(true);
                return;
            }
            // Or hits an immovable top slot → cancel
            ItemStack inSlot = e.getView().getTopInventory().getItem(raw);
            if (isImmovable(inSlot)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!isMasked(p)) return;
        if (p.getOpenInventory() != null && isOurGui(p.getOpenInventory())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!isMasked(p)) return;
        if (p.getOpenInventory() != null && isOurGui(p.getOpenInventory())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!isMasked(p)) return;
        if (p.getOpenInventory() != null && isOurGui(p.getOpenInventory())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent e) {
        Player p = e.getPlayer();
        if (p.getOpenInventory() != null && isOurGui(p.getOpenInventory())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (isMasked(p) && !isOurGui(e.getView())) {
            restoreInventoryMask(p);
        }
    }


    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!isOurGui(e.getView())) return;
        restoreInventoryMask(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Safety: if they log out while masked, restore immediately
        restoreInventoryMask(e.getPlayer());
    }

    public void restoreAllMasks() {
        java.util.Set<java.util.UUID> toRestore = new java.util.HashSet<>(savedStorage.keySet());
        for (java.util.UUID id : toRestore) {
            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(id);
            if (p != null) {
                try {
                    if (p.getOpenInventory() != null && isOurGui(p.getOpenInventory())) {
                        p.closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.PLUGIN);
                    }
                    restoreInventoryMask(p);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to restore inventory for " + p.getName() + ": " + ex.getMessage());
                }
            }
        }
        savedStorage.clear();
    }

}