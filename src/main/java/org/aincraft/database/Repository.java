package org.aincraft.database;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

public interface Repository<K, V> {

  @Nullable
  V load(K key);

  boolean save(K key, V value);

  void saveAll(Map<K, V> entities);

  void delete(K key);
}
