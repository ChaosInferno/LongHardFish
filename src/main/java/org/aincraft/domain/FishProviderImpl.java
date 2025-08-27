package org.aincraft.domain;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.aincraft.domain.record.FishEnvironmentRecord;
import org.aincraft.domain.record.FishRecord;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

final class FishProviderImpl implements FishProvider {

  private final Map<String, FishEnvironmentRecord> parentEnvironments;
  private final ConfigurationSection configurationSection;

  @Inject
  public FishProviderImpl(Map<String, FishEnvironmentRecord> parentEnvironments,
      ConfigurationSection configurationSection) {
    this.parentEnvironments = parentEnvironments;
    this.configurationSection = configurationSection;
  }

  @Override
  public List<FishRecord> getAllFish() {
    List<FishRecord> records = new ArrayList<>();
    for (String fishKey : configurationSection.getKeys(false)) {
      if (!configurationSection.contains(fishKey)) {
        continue;
      }
      ConfigurationSection fishSection = configurationSection.getConfigurationSection(
          fishKey);
      if (fishSection == null) {
        continue;
      }
      String displayName = fishSection.getString("display-name");
      String description = fishSection.getString("description");
      int identificationNumber = fishSection.getInt("identification-number");
      String rarityKey = fishSection.getString("rarity");
      List<FishEnvironmentRecord> parents = fishSection.getStringList("parents").stream()
          .map(parentEnvironments::get).toList();
      FishEnvironmentRecord environment = new FishEnvironmentLoaderImpl(fishSection).load();
      boolean openWaterRequired = fishSection.getBoolean("open-water-required");
      boolean rainRequired = fishSection.getBoolean("rain-required");
      FishRecord record = new FishRecord(fishKey, displayName, description,
          identificationNumber, rarityKey, parents,
          environment, openWaterRequired, rainRequired);
      records.add(record);
    }
    return records;
  }
}
