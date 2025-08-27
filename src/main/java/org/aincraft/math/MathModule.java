package org.aincraft.math;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

public final class MathModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(new TypeLiteral<WeightedRandom<Integer>>() {
    }).toInstance(new WeightedRandomImpl<>());
  }
}
