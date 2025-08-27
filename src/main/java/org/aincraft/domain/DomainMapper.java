package org.aincraft.domain;

public interface DomainMapper<D, R> {

  D toDomain(R record) throws IllegalArgumentException;

  R toRecord(D domain) throws IllegalArgumentException;
}
