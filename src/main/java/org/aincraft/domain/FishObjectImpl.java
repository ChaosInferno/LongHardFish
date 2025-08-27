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
  private final Map<Biome, Double> biomeWeights;
  private final Map<FishTimeCycle, Double> timeCycleWeights;
  private final Map<MoonPhase, Double> moonWeights;
  private final boolean openWaterRequired;
  private final boolean rainRequired;

  FishObjectImpl(ItemStack itemStack, Key fishkey, Component displayName,
      Component description, int identificationNumber, Rarity rarity,
      Map<Biome, Double> biomeWeights, Map<FishTimeCycle, Double> timeCycleWeights,
      Map<MoonPhase, Double> moonWeights, boolean openWaterRequired, boolean rainRequired) {
    this.itemStack = itemStack;
    this.fishkey = fishkey;
    this.displayName = displayName;
    this.description = description;
    this.identificationNumber = identificationNumber;
    this.rarity = rarity;
    this.biomeWeights = biomeWeights;
    this.timeCycleWeights = timeCycleWeights;
    this.moonWeights = moonWeights;
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
    return biomeWeights.get(biome);
  }

  @Override
  public Double getWeight(TimeCycle cycle) {
    return timeCycleWeights.get(cycle);
  }

  @Override
  public Double getWeight(MoonPhase phase) {
    return moonWeights.get(phase);
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

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    var that = (FishObjectImpl) obj;
    return Objects.equals(this.itemStack, that.itemStack) &&
        Objects.equals(this.fishkey, that.fishkey) &&
        Objects.equals(this.displayName, that.displayName) &&
        Objects.equals(this.description, that.description) &&
        this.identificationNumber == that.identificationNumber &&
        Objects.equals(this.rarity, that.rarity);
  }

  @Override
  public int hashCode() {
    return Objects.hash(itemStack, fishkey, displayName, description, identificationNumber, rarity);
  }

  @Override
  public String toString() {
    return "FishObjectImpl[" +
        "itemStack=" + itemStack + ", " +
        "fishkey=" + fishkey + ", " +
        "displayName=" + displayName + ", " +
        "description=" + description + ", " +
        "identificationNumber=" + identificationNumber + ", " +
        "rarity=" + rarity + ']';
  }

}
