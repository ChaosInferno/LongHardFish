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

        String display = niceName(def.id());
        meta.displayName(Component.text(display, NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        if (def.description() != null && !def.description().isBlank()) {
            lore.add(Component.text(def.description(), NamedTextColor.GRAY));
        }
        lore.add(Component.text("Tier " + def.tier(), NamedTextColor.DARK_AQUA));
        meta.lore(lore);

        // Optional: resource-pack model per rod id → longhardfish:rods/<id>
        meta.setItemModel(NamespacedKey.fromString("longhardfish:rods/" + def.id()));

        // PDC
        meta.getPersistentDataContainer().set(RodKeys.rodId(plugin), PersistentDataType.STRING, def.id());
        meta.getPersistentDataContainer().set(RodKeys.rodTier(plugin), PersistentDataType.INTEGER, def.tier());
        rod.setItemMeta(meta);
        return rod;
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
}