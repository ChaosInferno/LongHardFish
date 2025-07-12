package org.aincraft.provider;

import org.aincraft.config.FishConfig;
import org.aincraft.container.FishDistrubution;
import org.aincraft.container.FishRarity;
import org.aincraft.container.FishModel;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FishModelProvider {
    private final FishConfig holder;
    private final Plugin plugin;

    public FishModelProvider(FishConfig holder, Plugin plugin) {
        this.holder = holder;
        this.plugin = plugin;
    }

    public Map<NamespacedKey, FishModel> parseFishModelObjects() {
        FileConfiguration config = holder.getConfig();
        Set<String> keys = config.getKeys(false);
        Map<NamespacedKey, FishModel> modelMap = new HashMap<>();

        for (String key : keys) {
            ConfigurationSection configurationSection = config.getConfigurationSection(key);
            if (configurationSection != null) {
                // --Model--
                String modelString = configurationSection.getString("model");
                if (rarityString != null) {
                    try {
                        FishRarity rarity = FishRarity.valueOf(rarityString.toUpperCase(Locale.ENGLISH));
                        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
                        rarityDistribution.put(namespacedKey, new FishDistrubution(rarity));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid rarity: " + rarityString + " for fish: " + key);
                    }
                }
            }
        }
        return rarityDistribution;
    }
}
