package org.aincraft.math;

public interface WeightedRandom<T> {

  T getItem() throws IllegalStateException;

  void addItem(T item, double weight);
}
