package org.aincraft.domain;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.aincraft.domain.record.RarityRecord;
import org.bukkit.configuration.ConfigurationSection;

final class RarityRepositoryImpl implements RarityRepository {

  private static final double DEFAULT_BASE_WEIGHT = 0.0;
  private static final String DEFAULT_COLOR = "#ffffff";

  private final ConfigurationSection configuration;

  @Inject
  public RarityRepositoryImpl(ConfigurationSection configuration) {
    this.configuration = configuration;
  }

  @Override
  public List<RarityRecord> getAllRarities() {
    List<RarityRecord> rarities = new ArrayList<>();
    for (String label : configuration.getKeys(false)) {
      if (!configuration.contains(label)) {
        continue;
      }
      ConfigurationSection raritySection = configuration.getConfigurationSection(label);
      if (raritySection == null) {
        continue;
      }
      double baseWeight = raritySection.getDouble("base-weight", DEFAULT_BASE_WEIGHT);
      String rarityLabel = raritySection.getString("label", label);
      String color = raritySection.getString("color", DEFAULT_COLOR);
      rarities.add(new RarityRecord(baseWeight, rarityLabel, color));
    }
    return rarities;
  }
}
