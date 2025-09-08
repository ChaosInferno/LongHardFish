package org.aincraft.listener;

import org.aincraft.items.BaitKeys;
import org.aincraft.items.BaitRegistry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class RodBaitConsumeListener implements Listener {
    private final Plugin plugin;

    public RodBaitConsumeListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent e) {
        // Only consume on catch or miss
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH
                && e.getState() != PlayerFishEvent.State.FAILED_ATTEMPT) {
            return;
        }

        final FishHook hook = e.getHook();
        if (hook == null) return;

        // Debounce per hook to avoid double-consume if both states fire
        final NamespacedKey CONSUMED = new NamespacedKey(plugin, "bait_consumed");
        if (hook.getPersistentDataContainer().has(CONSUMED, PersistentDataType.BYTE)) return;
        hook.getPersistentDataContainer().set(CONSUMED, PersistentDataType.BYTE, (byte)1);

        final Player p = e.getPlayer();
        final ItemStack rod = getActiveRod(p);
        if (rod == null) return;

        String id  = BaitKeys.getRodBait(plugin, rod);
        int    cnt = BaitKeys.getRodBaitCount(plugin, rod);
        if (id == null || cnt <= 0) return;

        cnt -= 1;
        if (cnt <= 0) {
            BaitKeys.clearRodBait(plugin, rod);
        } else {
            BaitKeys.setRodBait(plugin, rod, id, cnt);
        }
        updateRodLore(plugin, rod); // keeps the tooltip in sync immediately
    }

    private ItemStack getActiveRod(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack main = inv.getItemInMainHand();
        if (main != null && main.getType() == Material.FISHING_ROD) return main;
        ItemStack off = inv.getItemInOffHand();
        if (off != null && off.getType() == Material.FISHING_ROD) return off;
        return null;
    }

    private void updateRodLore(Plugin plugin, ItemStack rod) {
        if (rod == null || !rod.hasItemMeta()) return;
        String id = BaitKeys.getRodBait(plugin, rod);
        int count = BaitKeys.getRodBaitCount(plugin, rod);

        ItemMeta meta = rod.getItemMeta();
        java.util.List<Component> lore = new java.util.ArrayList<>();
        if (meta.lore() != null) lore.addAll(meta.lore());

        // Remove any previous bait lines (old "Bait: ..." or new "... attached")
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        lore.removeIf(c -> {
            String s = plain.serialize(c).toLowerCase(Locale.ENGLISH).trim();
            return s.startsWith("bait: ") || s.endsWith(" attached");
        });

        if (id != null && count > 0) {
            // Use registry’s plural-aware display name
            String display = BaitRegistry.displayName(id, count); // e.g. "Dragonfly" / "Dragonflies"
            lore.add(Component.text(count + "x " + display + " attached", NamedTextColor.GOLD));
        }

        meta.lore(lore);
        rod.setItemMeta(meta);
    }
}