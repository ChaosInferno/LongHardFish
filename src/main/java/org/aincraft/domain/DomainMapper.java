package org.aincraft.domain;

import org.jetbrains.annotations.NotNull;

public interface DomainMapper<D, R> {

  @NotNull
  D toDomain(@NotNull R record) throws IllegalArgumentException;

  @NotNull
  R toRecord(@NotNull D domain) throws IllegalArgumentException;
}
