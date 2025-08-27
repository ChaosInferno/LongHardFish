package org.aincraft.domain;

import io.papermc.paper.world.MoonPhase;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.aincraft.api.FishObject;
import org.aincraft.container.Rarity;
import org.aincraft.container.TimeCycle;
import org.bukkit.block.Biome;
import org.bukkit.inventory.ItemStack;

record FishObjectImpl(ItemStack itemStack, Key fishkey, Component displayName,
                      Component description, int identificationNumber, Rarity rarity,
                      FishEnvironment fishEnvironment, boolean openWaterRequired,
                      boolean rainRequired) implements
    FishObject {

  @Override
  public Double getWeight(Biome biome) {
    return fishEnvironment.getWeight(biome);
  }

  @Override
  public Double getWeight(TimeCycle cycle) {
    return fishEnvironment.getWeight(cycle);
  }

  @Override
  public Double getWeight(MoonPhase phase) {
    return fishEnvironment.getWeight(phase);
  }
}
