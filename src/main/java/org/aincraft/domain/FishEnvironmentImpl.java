package org.aincraft.domain;

import io.papermc.paper.world.MoonPhase;
import java.util.Map;
import net.kyori.adventure.key.Key;
import org.aincraft.container.TimeCycle;
import org.bukkit.block.Biome;

final class FishEnvironmentImpl implements FishEnvironment {

  private final Map<Key, Double> biomeWeights;
  private final Map<Key, Double> timeCycleWeights;
  private final Map<MoonPhase, Double> moonWeights;

  FishEnvironmentImpl(Map<Key, Double> biomeWeights, Map<Key, Double> timeCycleWeights,
      Map<MoonPhase, Double> moonWeights) {
    this.biomeWeights = biomeWeights;
    this.timeCycleWeights = timeCycleWeights;
    this.moonWeights = moonWeights;
  }

  @Override
  public Double getWeight(Biome biome) {
    return biomeWeights.get(biome.key());
  }

  @Override
  public Double getWeight(TimeCycle cycle) {
    return timeCycleWeights.get(cycle.key());
  }

  @Override
  public Double getWeight(MoonPhase phase) {
    return moonWeights.get(phase);
  }
}
