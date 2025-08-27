package org.aincraft.domain;

import java.util.HashMap;
import java.util.Map;
import org.aincraft.domain.record.FishEnvironmentRecord;
import org.bukkit.configuration.ConfigurationSection;

public final class FishEnvironmentLoaderImpl {

  private final ConfigurationSection configuration;

  public FishEnvironmentLoaderImpl(ConfigurationSection configuration) {
    this.configuration = configuration;
  }

  public FishEnvironmentRecord load() {
    Map<String, Double> biomeWeights = new HashMap<>();
    Map<String, Double> timeWeights = new HashMap<>();
    Map<String, Double> moonWeights = new HashMap<>();
    if (configuration.contains("biomes")) {
      ConfigurationSection biomeSection = configuration.getConfigurationSection("biomes");
      if (biomeSection != null) {
        biomeSection.getKeys(false).forEach(key -> {
          biomeWeights.put(key, biomeSection.getDouble(key, 0.0));
        });
      }
    }
    if (configuration.contains("times")) {
      ConfigurationSection timeSection = configuration.getConfigurationSection("times");
      if (timeSection != null) {
        timeSection.getKeys(false).forEach(key -> {
          timeWeights.put(key, timeSection.getDouble(key, 0.0));
        });
      }
    }
    if (configuration.contains("moons")) {
      ConfigurationSection moonSection = configuration.getConfigurationSection("moons");
      if (moonSection != null) {
        moonSection.getKeys(false).forEach(key -> {
          moonWeights.put(key, moonSection.getDouble(key, 0.0));
        });
      }
    }
    return new FishEnvironmentRecord(biomeWeights,timeWeights,moonWeights);
  }
}
