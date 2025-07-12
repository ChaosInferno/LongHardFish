package org.aincraft.provider;

import org.aincraft.config.FishConfig;
import org.aincraft.container.*;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class FishEnvironmentProvider {
    private final FishConfig holder;

    public FishEnvironmentProvider(FishConfig holder, Plugin plugin) {
        this.holder = holder;
        this.plugin = plugin;
    }

    private final Plugin plugin;

    public Map<NamespacedKey, FishEnvironment> parseFishEnvironmentObjects() {
        FileConfiguration config = holder.getConfig();
        Set<String> keys = config.getKeys(false);
        Map<NamespacedKey, FishEnvironment> fishEnvironments = new HashMap<>();

        for (String key : keys) {
            ConfigurationSection configurationSection = config.getConfigurationSection(key);
            if (configurationSection != null) {
                // --- Biomes ---
                ConfigurationSection configurationSectionBiome = configurationSection.getConfigurationSection("biomes");
                Map<Biome, Double> biomeWeights = new HashMap<>();
                if (configurationSectionBiome != null) {
                    for (String biomeKey : configurationSectionBiome.getKeys(false)) {
                        Biome biome = Biome.valueOf(biomeKey.toUpperCase(Locale.ENGLISH));
                        double weight = configurationSectionBiome.getDouble(biomeKey);
                        biomeWeights.put(biome, weight);
                    }
                }
                // --- Times ---
                ConfigurationSection configurationSectionTime = configurationSection.getConfigurationSection("times");
                Map<FishTimeCycle, Double> timeWeights = new HashMap<>();
                if (configurationSectionTime != null) {
                    for (String timeKey : configurationSectionTime.getKeys(false)) {
                        FishTimeCycle time = FishTimeCycle.valueOf(timeKey.toUpperCase(Locale.ENGLISH));
                        double weight = configurationSectionTime.getDouble(timeKey);
                        timeWeights.put(time, weight);
                    }
                }
                // --- Moons ---
                ConfigurationSection configurationSectionMoon = configurationSection.getConfigurationSection("moons");
                Map<FishMoonCycle, Double> moonWeights = new HashMap<>();
                if (configurationSectionMoon != null) {
                    for (String moonKey : configurationSectionMoon.getKeys(false)) {
                        FishMoonCycle moon = FishMoonCycle.valueOf(moonKey.toUpperCase(Locale.ENGLISH));
                        double weight = configurationSectionMoon.getDouble(moonKey);
                        moonWeights.put(moon, weight);
                    }
                }
                boolean openWaterRequired = configurationSection.getBoolean("open-water-required", false);
                boolean rainRequired = configurationSection.getBoolean("rain-required",false);

                fishEnvironments.put(new NamespacedKey(plugin, key), new FishEnvironment(biomeWeights, timeWeights, moonWeights, openWaterRequired, rainRequired));
            }
        }

        return fishEnvironments;
    }
}
