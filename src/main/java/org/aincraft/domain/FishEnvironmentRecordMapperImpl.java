package org.aincraft.domain;

import org.aincraft.domain.record.FishEnvironmentRecord;
import org.jetbrains.annotations.NotNull;

public class FishEnvironmentRecordMapperImpl implements
    DomainMapper<FishEnvironment, FishEnvironmentRecord> {

  @Override
  public @NotNull FishEnvironment toDomain(@NotNull FishEnvironmentRecord record) throws IllegalArgumentException {
    return null;
  }

  @Override
  public @NotNull FishEnvironmentRecord toRecord(@NotNull FishEnvironment domain) throws IllegalArgumentException {
    return null;
  }

}
