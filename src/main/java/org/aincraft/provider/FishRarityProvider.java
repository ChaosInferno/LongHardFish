package org.aincraft.provider;

import org.aincraft.config.FishConfig;
import org.aincraft.container.FishDistribution;
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

    /**
     * Result container for parsed rarity data - avoids double iteration.
     */
    public static class ParseResult {
        public final Map<NamespacedKey, FishDistribution> distributions;
        public final Map<NamespacedKey, FishRarity> rarityMap;

        ParseResult(Map<NamespacedKey, FishDistribution> distributions, Map<NamespacedKey, FishRarity> rarityMap) {
            this.distributions = distributions;
            this.rarityMap = rarityMap;
        }
    }

    /**
     * Parses fish rarity data and returns both distributions and rarity map in a single pass.
     * This avoids the need to iterate twice.
     */
    public ParseResult parseAll() {
        FileConfiguration config = holder.getConfig();
        Set<String> keys = config.getKeys(false);
        Map<NamespacedKey, FishDistribution> distributions = new HashMap<>(keys.size());
        Map<NamespacedKey, FishRarity> rarityMap = new HashMap<>(keys.size());

        for (String key : keys) {
            ConfigurationSection configurationSection = config.getConfigurationSection(key);
            if (configurationSection != null) {
                String rarityString = configurationSection.getString("rarity");
                if (rarityString != null) {
                    try {
                        FishRarity rarity = FishRarity.valueOf(rarityString.toUpperCase(Locale.ENGLISH));
                        NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
                        FishDistribution distribution = new FishDistribution(rarity);
                        distributions.put(namespacedKey, distribution);
                        rarityMap.put(namespacedKey, rarity);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid rarity: " + rarityString + " for fish: " + key);
                    }
                }
            }
        }
        return new ParseResult(distributions, rarityMap);
    }

    /**
     * @deprecated Use {@link #parseAll()} instead to avoid double iteration.
     */
    @Deprecated
    public Map<NamespacedKey, FishDistribution> parseFishDistributorObjects() {
        return parseAll().distributions;
    }
}