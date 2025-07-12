package org.aincraft.provider;

import org.aincraft.config.FishConfig;
import org.aincraft.container.FishDistrubution;
import org.aincraft.container.FishRarity;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FishRarityProvider {
    private final FishConfig holder;
    private final Plugin plugin;

    public FishRarityProvider(FishConfig holder, Plugin plugin) {
        this.holder = holder;
        this.plugin = plugin;
    }

    public Map<NamespacedKey, FishDistrubution> parseFishDistributorObjects() {
        FileConfiguration config = holder.getConfig();
        Set<String> keys = config.getKeys(false);
        Map<NamespacedKey, FishDistrubution> rarityDistribution = new HashMap<>();

        for (String key : keys) {
            ConfigurationSection configurationSection = config.getConfigurationSection(key);
            if (configurationSection != null) {
                String rarityString = configurationSection.getString("rarity");
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