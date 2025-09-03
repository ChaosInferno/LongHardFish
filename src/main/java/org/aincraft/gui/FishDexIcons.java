package org.aincraft.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.container.FishEnvironment;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.text.DecimalFormat;
import java.util.*;

public final class FishDexIcons {
    private FishDexIcons() {}

    private static final DecimalFormat PCT = new DecimalFormat("+0.##%;-0.##%");

    /**
     * Build the non-editable bait icon for Dex slot 3.
     * @param known whether the fish has been seen/caught (i.e., reveal bait data)
     */
    public static ItemStack baitIcon(Plugin plugin,
                                     NamespacedKey immovableKey,
                                     FishEnvironment env,
                                     boolean known) {
        ItemStack it = new ItemStack(Material.PAPER); // base hidden by RP model
        ItemMeta meta = it.getItemMeta();

        meta.displayName(Component.text("Bait", NamedTextColor.GOLD));
        meta.setHideTooltip(false); // we want the tooltip

        if (!known) {
            // Unknown state
            meta.lore(List.of(
                    Component.text("Bait requirements unknown", NamedTextColor.GRAY)
            ));
            meta.setItemModel(NamespacedKey.fromString("longhardfish:icons/bait-icon_empty"));
        } else {
            // Known: render the fish's bait table
            List<Component> lore = new ArrayList<>();
            Map<String, Double> baits = (env == null ? null : env.getEnvironmentBaits());

            if (baits == null || baits.isEmpty()) {
                lore.add(Component.text("No bait effects", NamedTextColor.GRAY));
            } else {
                // 'none' first, then by descending bonus, then alpha
                List<Map.Entry<String,Double>> rows = new ArrayList<>(baits.entrySet());
                rows.sort((a,b) -> {
                    boolean an = "none".equalsIgnoreCase(a.getKey());
                    boolean bn = "none".equalsIgnoreCase(b.getKey());
                    if (an != bn) return an ? -1 : 1;
                    int cmp = Double.compare(b.getValue(), a.getValue());
                    if (cmp != 0) return cmp;
                    return a.getKey().compareToIgnoreCase(b.getKey());
                });

                lore.add(Component.text("Accepted bait:", NamedTextColor.GRAY));
                for (var e : rows) {
                    String id = e.getKey();
                    double bonus = e.getValue();
                    String label = "none".equalsIgnoreCase(id) ? "No bait" : id.replace('_', ' ');
                    lore.add(Component.text("• " + label + " " + PCT.format(bonus), NamedTextColor.DARK_GRAY));
                }
            }
            meta.lore(lore);
            meta.setItemModel(NamespacedKey.fromString("longhardfish:icons/bait-icon"));
        }

        // Lock it
        meta.getPersistentDataContainer().set(
                immovableKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);

        it.setItemMeta(meta);
        return it;
    }
}
