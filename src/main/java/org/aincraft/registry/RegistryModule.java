package org.aincraft.registry;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import net.kyori.adventure.key.Keyed;

public final class RegistryModule extends AbstractModule {

  @Provides
  @Singleton
  RegistryFactory registryFactory() {
    return SimpleRegistryImpl::new;
  }
}
