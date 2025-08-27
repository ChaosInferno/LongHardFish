package org.aincraft.registry;

import net.kyori.adventure.key.Keyed;

public interface RegistryFactory {
  <T extends Keyed> Registry<T> create();
}
