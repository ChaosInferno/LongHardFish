// org/aincraft/rods/RodItemFactory.java
package org.aincraft.rods;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public final class RodItemFactory {
    private final Plugin plugin;
    public RodItemFactory(Plugin plugin) { this.plugin = plugin; }

    public ItemStack create(RodDefinition def) {
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();

        // Line 1: White name, no italics
        String display = niceName(def.id());
        meta.displayName(Component.text(display, NamedTextColor.WHITE).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();

        // Line 2: Rarity based on tier (no italics)
        Component rarityLine = Component.text(tierToRarityName(def.tier()), tierToRarityColor(def.tier()))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        lore.add(rarityLine);

        // Line 3: Stars
        lore.add(Component.text(stars(def.tier(), 4), NamedTextColor.GOLD));

        // Description for Tier 3+ only (remove for Tier 1 & 2)
        String desc = def.description();
        if (def.tier() >= 3 && desc != null && !desc.isBlank()) {
            for (String line : wrapWords(desc, 50)) {
                lore.add(Component.text(line, NamedTextColor.GRAY));
            }
        }

        meta.lore(lore);

        meta.setItemModel(NamespacedKey.fromString(def.itemModelKey()));

        // PDC
        meta.getPersistentDataContainer().set(RodKeys.rodId(plugin), PersistentDataType.STRING, def.id());
        meta.getPersistentDataContainer().set(RodKeys.rodTier(plugin), PersistentDataType.INTEGER, def.tier());
        rod.setItemMeta(meta);
        return rod;
    }

    private static String tierToRarityName(int tier) {
        return switch (tier) {
            case 1 -> "Common";
            case 2 -> "Uncommon";
            case 3 -> "Rare";
            case 4 -> "Legendary";
            default -> "Unknown";
        };
    }

    private static NamedTextColor tierToRarityColor(int tier) {
        return switch (tier) {
            case 1 -> NamedTextColor.GRAY;
            case 2 -> NamedTextColor.GREEN;
            case 3 -> NamedTextColor.AQUA;
            case 4 -> NamedTextColor.GOLD;
            default -> NamedTextColor.GRAY;
        };
    }

    public static String niceName(String id) {
        String[] parts = id.replace('-', ' ').replace('_', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(parts[i].charAt(0)))
                    .append(parts[i].substring(1).toLowerCase());
            if (i + 1 < parts.length) sb.append(' ');
        }
        return sb.toString();
    }

    private static String stars(int tier, int max) {
        if (tier < 0) tier = 0;
        if (tier > max) tier = max;
        StringBuilder sb = new StringBuilder(max);
        for (int i = 1; i <= max; i++) {
            sb.append(i <= tier ? '★' : '☆');
        }
        return sb.toString();
    }

    private static List<String> wrapWords(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;

        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();

        for (String w : words) {
            if (line.length() == 0) {
                // start new line with the word
                line.append(w);
            } else if (line.length() + 1 + w.length() <= width) {
                // fits in current line
                line.append(' ').append(w);
            } else {
                // push current line, start new with the word
                lines.add(line.toString());
                line.setLength(0);
                line.append(w);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}