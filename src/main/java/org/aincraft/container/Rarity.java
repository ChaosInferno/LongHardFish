package org.aincraft.container;

import net.kyori.adventure.key.Keyed;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;

public interface Rarity extends ComponentLike, Keyed {

  double baseWeight();

  Component label();

  TextColor color();

  @Override
  default @NotNull Component asComponent() {
    return label();
  }
}
