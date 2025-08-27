package org.aincraft.domain;

import com.google.inject.Inject;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.world.MoonPhase;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.kyori.adventure.key.Key;
import org.aincraft.container.TimeCycle;
import org.aincraft.domain.record.FishEnvironmentRecord;
import org.aincraft.registry.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

final class FishEnvironmentRecordMapperImpl implements
    DomainMapper<FishEnvironment, FishEnvironmentRecord> {

  private static final org.bukkit.Registry<Biome> BIOMES = RegistryAccess.registryAccess()
      .getRegistry(RegistryKey.BIOME);

  private final Registry<TimeCycle> timeCycleRegistry;
  private final Plugin plugin;

  @Inject
  FishEnvironmentRecordMapperImpl(Registry<TimeCycle> timeCycleRegistry, Plugin plugin) {
    this.timeCycleRegistry = timeCycleRegistry;
    this.plugin = plugin;
  }

  @Override
  public @NotNull FishEnvironment toDomain(@NotNull FishEnvironmentRecord record)
      throws IllegalArgumentException {
    Map<Key, Double> biomeWeights = new HashMap<>();
    for (Entry<String, Double> entry : record.biomeWeights().entrySet()) {
      Biome biome = BIOMES.getOrThrow(NamespacedKey.minecraft(entry.getKey()));
      biomeWeights.put(biome.key(), entry.getValue());
    }
    Map<Key, Double> timeWeights = new HashMap<>();
    for (Entry<String, Double> entry : record.timeWeights().entrySet()) {
      Key key = NamespacedKey.fromString(entry.getKey());
      if (!timeCycleRegistry.isRegistered(key)) {
        throw new IllegalArgumentException("failed to find a time cycle");
      }
      timeWeights.put(key, entry.getValue());
    }
    Map<MoonPhase, Double> moonWeights = new HashMap<>();
    for (Entry<String, Double> entry : record.moonWeights().entrySet()) {
      MoonPhase phase = MoonPhase.valueOf(entry.getKey());
      moonWeights.put(phase, entry.getValue());
    }
    return new FishEnvironmentImpl(biomeWeights, timeWeights, moonWeights);
  }
}
