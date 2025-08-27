package org.aincraft.domain;

import io.papermc.paper.world.MoonPhase;
import org.aincraft.container.TimeCycle;
import org.bukkit.block.Biome;

public interface FishEnvironment {
  Double getWeight(Biome biome);

  Double getWeight(TimeCycle cycle);

  Double getWeight(MoonPhase phase);
}
