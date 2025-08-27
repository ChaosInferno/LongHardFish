package org.aincraft.domain;

import io.papermc.paper.world.MoonPhase;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.aincraft.api.FishObject;
import org.aincraft.container.FishTimeCycle;
import org.aincraft.container.Rarity;
import org.aincraft.container.TimeCycle;
import org.bukkit.block.Biome;
import org.bukkit.inventory.ItemStack;

final class FishObjectImpl implements
    FishObject {

  private final ItemStack itemStack;
  private final Key fishkey;
  private final Component displayName;
  private final Component description;
  private final int identificationNumber;
  private final Rarity rarity;
  private final FishEnvironment fishEnvironment;
  private final boolean openWaterRequired;
  private final boolean rainRequired;

  FishObjectImpl(ItemStack itemStack, Key fishkey, Component displayName,
      Component description, int identificationNumber, Rarity rarity,
      FishEnvironment fishEnvironment, boolean openWaterRequired,
      boolean rainRequired) {
    this.itemStack = itemStack;
    this.fishkey = fishkey;
    this.displayName = displayName;
    this.description = description;
    this.identificationNumber = identificationNumber;
    this.rarity = rarity;
    this.fishEnvironment = fishEnvironment;
    this.openWaterRequired = openWaterRequired;
    this.rainRequired = rainRequired;
  }

  @Override
  public boolean openWaterRequired() {
    return openWaterRequired;
  }

  @Override
  public boolean rainRequired() {
    return rainRequired;
  }

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

  @Override
  public ItemStack itemStack() {
    return itemStack;
  }

  @Override
  public Key fishkey() {
    return fishkey;
  }

  @Override
  public Component displayName() {
    return displayName;
  }

  @Override
  public Component description() {
    return description;
  }

  @Override
  public int identificationNumber() {
    return identificationNumber;
  }

  @Override
  public Rarity rarity() {
    return rarity;
  }
}
