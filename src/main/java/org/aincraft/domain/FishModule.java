package org.aincraft.domain;

import com.google.gson.annotations.Expose;
import com.google.inject.Exposed;
import com.google.inject.Inject;
import com.google.inject.PrivateModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aincraft.api.FishObject;
import org.aincraft.config.YamlConfiguration;
import org.aincraft.domain.record.FishEnvironmentRecord;
import org.aincraft.domain.record.FishRecord;
import org.aincraft.registry.Registry;
import org.aincraft.registry.RegistryFactory;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

final class FishModule extends PrivateModule {

  //TODO: relocate these to the config module
  private static final String FISH_PARENTS = "fish-parents";
  private static final String FISH = "fish";

  @Override
  protected void configure() {
    TypeLiteral<Registry<FishObject>> fishRegistryType = new TypeLiteral<>() {
    };
    bind(fishRegistryType)
        .toProvider(FishRegistryProvider.class)
        .in(Singleton.class);
    expose(fishRegistryType);
  }

  @Provides
  @Singleton
  @Named(FISH_PARENTS)
  Map<String, FishEnvironmentRecord> environmentParents(
      @Named(FISH_PARENTS) YamlConfiguration configuration) {
    Map<String, FishEnvironmentRecord> records = new HashMap<>();
    for (String environmentKey : configuration.getKeys(false)) {
      if (!configuration.contains(environmentKey)) {
        continue;
      }
      ConfigurationSection environmentSection = configuration.getConfigurationSection(
          environmentKey);
      if (environmentSection != null) {
        FishEnvironmentRecord environment = new FishEnvironmentLoader(configuration).load();
        records.put(environmentKey, environment);
      }
    }
    return records;
  }

  private record FishRegistryProvider(Map<String, FishEnvironmentRecord> parents,
                                      YamlConfiguration configuration,
                                      DomainMapper<FishObject, FishRecord> mapper,
                                      RegistryFactory registryFactory) implements
      Provider<Registry<FishObject>> {

    @Inject
    private FishRegistryProvider(@Named(FISH_PARENTS) Map<String, FishEnvironmentRecord> parents,
        @Named(FISH) YamlConfiguration configuration, DomainMapper<FishObject, FishRecord> mapper,
        RegistryFactory registryFactory) {
      this.parents = parents;
      this.configuration = configuration;
      this.mapper = mapper;
      this.registryFactory = registryFactory;
    }

    @Override
    public Registry<FishObject> get() {
      Registry<FishObject> registry = registryFactory.create();
      loadFish(parents, configuration).stream().map(mapper::toDomain).forEach(registry::register);
      return registry;
    }

    @NotNull
    private static List<FishRecord> loadFish(Map<String, FishEnvironmentRecord> parentEnvironments,
        ConfigurationSection configuration) {
      List<FishRecord> records = new ArrayList<>();
      for (String fishKey : configuration.getKeys(false)) {
        if (!configuration.contains(fishKey)) {
          continue;
        }
        ConfigurationSection fishSection = configuration.getConfigurationSection(
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
        FishEnvironmentRecord environment = new FishEnvironmentLoader(fishSection).load();
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
}
