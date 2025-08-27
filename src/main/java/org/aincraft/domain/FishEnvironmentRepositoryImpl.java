package org.aincraft.domain;

import java.util.List;
import org.aincraft.domain.record.FishEnvironmentRecord;
import org.bukkit.configuration.ConfigurationSection;

public class FishEnvironmentRepositoryImpl implements FishEnvironmentRepository {

  private final ConfigurationSection configuration;

  public FishEnvironmentRepositoryImpl(ConfigurationSection configuration) {
    this.configuration = configuration;
  }

  @Override
  public List<FishEnvironmentRecord> getAllEnvironments() {
    return List.of();
  }
}
