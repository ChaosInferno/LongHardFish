package org.aincraft.domain;

import com.google.inject.Exposed;
import com.google.inject.Inject;
import com.google.inject.PrivateModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import org.aincraft.container.Rarity;
import org.aincraft.domain.record.RarityRecord;
import org.aincraft.registry.Registry;
import org.aincraft.registry.RegistryFactory;

final class RarityModule extends PrivateModule {

  @Override
  protected void configure() {
    bind(RarityRepository.class).to(RarityRepositoryImpl.class).in(Singleton.class);
    bind(new TypeLiteral<DomainMapper<Rarity, RarityRecord>>() {
    }).to(RarityRecordMapperImpl.class).in(Singleton.class);
  }

  @Provides
  @Singleton
  @Exposed
  Registry<Rarity> rarityRegistry(RarityRepository repository,
      DomainMapper<Rarity, RarityRecord> rarityRecordDomainMapper,
      RegistryFactory registryFactory) {
    Registry<Rarity> registry = registryFactory.create();
    repository.getAllRarities().stream().map(rarityRecordDomainMapper::toDomain)
        .forEach(registry::register);
    return registry;
  }
}
