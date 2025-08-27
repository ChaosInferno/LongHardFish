package org.aincraft.provider;

import org.aincraft.container.FishTimeCycle;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FishEnvironmentDefaultsProvider {

    private final FileConfiguration config;
    private final Plugin plugin;

    public FishEnvironmentDefaultsProvider(FileConfiguration config, Plugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }

    public FishEnvironment getDefaults(String group) {
        ConfigurationSection section = config.getConfigurationSection(group);
        if (section == null) {
            plugin.getLogger().warning("Defaults group not found: " + group);
            return null;
        }

        Map<Biome, Double> biomes = new HashMap<>();
        ConfigurationSection biomeSection = section.getConfigurationSection("biomes");
        if (biomeSection != null) {
            for (String biomeKey : biomeSection.getKeys(false)) {
                try {
                    Biome biome = Biome.valueOf(biomeKey.toUpperCase(Locale.ENGLISH));
                    biomes.put(biome, biomeSection.getDouble(biomeKey));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid biome in defaults '" + group + "': " + biomeKey);
                }
            }
        }

        Map<FishTimeCycle, Double> times = new HashMap<>();
        ConfigurationSection timeSection = section.getConfigurationSection("times");
        if (timeSection != null) {
            for (String timeKey : timeSection.getKeys(false)) {
                try {
                    FishTimeCycle time = FishTimeCycle.valueOf(timeKey.toUpperCase(Locale.ENGLISH));
                    times.put(time, timeSection.getDouble(timeKey));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid time in defaults '" + group + "': " + timeKey);
                }
            }
        }

        Map<FishMoonCycle, Double> moons = new HashMap<>();
        ConfigurationSection moonSection = section.getConfigurationSection("moons");
        if (moonSection != null) {
            for (String moonKey : moonSection.getKeys(false)) {
                try {
                    FishMoonCycle moon = FishMoonCycle.valueOf(moonKey.toUpperCase(Locale.ENGLISH));
                    moons.put(moon, moonSection.getDouble(moonKey));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid moon in defaults '" + group + "': " + moonKey);
                }
            }
        }

        Integer model = null;
        if (section.isInt("model")) {
            int raw = section.getInt("model", -1);
            if (raw > 0) {
                model = raw;
            } else {
                plugin.getLogger().warning("Invalid 'model' in defaults '" + group + "': " + raw);
            }
        }

        // Defaults for openWaterRequired and rainRequired could be false or read from config if you add those fields to defaults
        return new FishEnvironment(biomes, times, moons, model,false, false);
    }
}
