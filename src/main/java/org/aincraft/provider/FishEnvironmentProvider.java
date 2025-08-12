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
    private final Plugin plugin;
    private final FishEnvironmentDefaultsProvider defaultsProvider;

    public FishEnvironmentProvider(FishConfig holder, FishEnvironmentDefaultsProvider defaultsProvider, Plugin plugin) {
        this.holder = holder;
        this.plugin = plugin;
        this.defaultsProvider = defaultsProvider;
    }

    public Map<NamespacedKey, FishEnvironment> parseFishEnvironmentObjects() {
        FileConfiguration config = holder.getConfig();
        Set<String> keys = config.getKeys(false);
        Map<NamespacedKey, FishEnvironment> fishEnvironments = new HashMap<>();

        for (String key : keys) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;

            Map<Biome, Double> biomeWeights = new HashMap<>();
            Map<FishTimeCycle, Double> timeWeights = new HashMap<>();
            Map<FishMoonCycle, Double> moonWeights = new HashMap<>();

            // 1. Load defaults from `defaults:` section
            List<String> defaultGroups = section.getStringList("defaults");
            for (String group : defaultGroups) {
                FishEnvironment defaults = defaultsProvider.getDefaults(group);
                if (defaults != null) {
                    defaults.getEnvironmentBiomes().forEach((biome, weight) -> biomeWeights.putIfAbsent(biome, weight));
                    defaults.getEnvironmentTimes().forEach((time, weight) -> timeWeights.putIfAbsent(time, weight));
                    defaults.getEnvironmentMoons().forEach((moon, weight) -> moonWeights.putIfAbsent(moon, weight));
                }
            }

            // 2. Override with fish-specific biomes
            ConfigurationSection biomesSection = section.getConfigurationSection("biomes");
            if (biomesSection != null) {
                for (String biomeKey : biomesSection.getKeys(false)) {
                    try {
                        Biome biome = Biome.valueOf(biomeKey.toUpperCase(Locale.ENGLISH));
                        double weight = biomesSection.getDouble(biomeKey);
                        biomeWeights.put(biome, weight);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Unknown biome: " + biomeKey + " in fish: " + key);
                    }
                }
            }

            // 3. Override with fish-specific times
            ConfigurationSection timesSection = section.getConfigurationSection("times");
            if (timesSection != null) {
                for (String timeKey : timesSection.getKeys(false)) {
                    try {
                        FishTimeCycle time = FishTimeCycle.valueOf(timeKey.toUpperCase(Locale.ENGLISH));
                        double weight = timesSection.getDouble(timeKey);
                        timeWeights.put(time, weight);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Unknown time cycle: " + timeKey + " in fish: " + key);
                    }
                }
            }

            // 4. Override with fish-specific moons
            ConfigurationSection moonsSection = section.getConfigurationSection("moons");
            if (moonsSection != null) {
                for (String moonKey : moonsSection.getKeys(false)) {
                    try {
                        FishMoonCycle moon = FishMoonCycle.valueOf(moonKey.toUpperCase(Locale.ENGLISH));
                        double weight = moonsSection.getDouble(moonKey);
                        moonWeights.put(moon, weight);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Unknown moon cycle: " + moonKey + " in fish: " + key);
                    }
                }
            }

            boolean openWaterRequired = section.getBoolean("open-water-required", false);
            boolean rainRequired = section.getBoolean("rain-required", false);

            fishEnvironments.put(
                    new NamespacedKey(plugin, key),
                    new FishEnvironment(biomeWeights, timeWeights, moonWeights, openWaterRequired, rainRequired)
            );
        }

        return fishEnvironments;
    }
}
