package org.aincraft.domain;

import java.util.List;
import org.aincraft.domain.record.FishRecord;
import org.bukkit.configuration.ConfigurationSection;

final class FishRepositoryImpl implements FishRepository {

  private final ConfigurationSection configurationSection;

  public FishRepositoryImpl(ConfigurationSection configurationSection) {
    this.configurationSection = configurationSection;
  }

  @Override
  public List<FishRecord> getAllFish() {
    configurationSection.
    return List.of();
  }
}
