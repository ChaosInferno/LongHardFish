package org.aincraft.api;

import io.papermc.paper.world.MoonPhase;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.aincraft.container.Rarity;
import org.aincraft.container.TimeCycle;
import org.aincraft.domain.FishEnvironment;
import org.bukkit.block.Biome;
import org.bukkit.inventory.ItemStack;

public interface FishObject {

  ItemStack itemStack();

  Key fishkey();

  Component displayName();

  Component description();

  int identificationNumber();

  Rarity rarity();

  boolean openWaterRequired();

  boolean rainRequired();

  FishEnvironment fishEnvironment();

  Double getWeight(Biome biome);

  Double getWeight(TimeCycle cycle);

  Double getWeight(MoonPhase phase);
}
