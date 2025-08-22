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

            Integer defaultsModel = null;

            // 1. Load defaults from `defaults:` section
            for (String group : section.getStringList("defaults")) {
                FishEnvironment def = defaultsProvider.getDefaults(group);
                if (def == null) continue;

                def.getEnvironmentBiomes().forEach(biomeWeights::putIfAbsent);
                def.getEnvironmentTimes().forEach(timeWeights::putIfAbsent);
                def.getEnvironmentMoons().forEach(moonWeights::putIfAbsent);
                if (defaultsModel == null && def.getModel() != null) {
                    defaultsModel = def.getModel();
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

            Integer model = defaultsModel;
            if (section.isInt("model")) {
                int raw = section.getInt("model", -1);
                if (raw > 0) {
                    model = raw;
                } else {
                    plugin.getLogger().warning("Invalid model for fish '" + key + "': " + raw);
                }
            }

            fishEnvironments.put(
                    new NamespacedKey(plugin, key),
                    new FishEnvironment(biomeWeights, timeWeights, moonWeights, model, openWaterRequired, rainRequired)
            );
        }

        return fishEnvironments;
    }

    public Map<NamespacedKey, Integer> parseFishTierMap() {
        FileConfiguration config = holder.getConfig();
        Set<String> keys = config.getKeys(false);
        Map<NamespacedKey, Integer> tierMap = new HashMap<>();

        for (String key : keys) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;

            if (section.contains("tier")) {
                int raw = section.getInt("tier", 1);
                int clamped = raw;
                if (raw < 1 || raw > 4) {
                    clamped = Math.max(1, Math.min(4, raw));
                    plugin.getLogger().warning("Tier out of range for fish '" + key + "': " + raw + " (clamped to " + clamped + ")");
                }
                tierMap.put(new NamespacedKey(plugin, key), clamped);
            }
        }

        return tierMap;
    }
}
