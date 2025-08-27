package org.aincraft.registry;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;
import org.jetbrains.annotations.NotNull;

public interface Registry<T extends Keyed> {

  void register(T object);

  boolean isRegistered(Key key);

  @NotNull
  T getOrThrow(Key key) throws IllegalArgumentException;
}
