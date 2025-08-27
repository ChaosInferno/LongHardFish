package org.aincraft.domain;

import com.google.inject.PrivateModule;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import org.aincraft.api.FishObject;
import org.aincraft.domain.record.FishEnvironmentRecord;
import org.aincraft.domain.record.FishRecord;
import org.aincraft.registry.RegistryModule;

public final class DomainModule extends PrivateModule {

  @Override
  protected void configure() {
    install(new FishModule());
    install(new RarityModule());
    bind(new TypeLiteral<DomainMapper<FishObject, FishRecord>>() {
    })
        .to(FishRecordMapperImpl.class)
        .in(Singleton.class);
    bind(new TypeLiteral<DomainMapper<FishEnvironment, FishEnvironmentRecord>>() {
    })
        .to(FishEnvironmentRecordMapperImpl.class)
        .in(Singleton.class);
  }
}
