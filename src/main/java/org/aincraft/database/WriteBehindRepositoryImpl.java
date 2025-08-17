package org.aincraft.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public final class WriteBehindRepositoryImpl<K, V> implements Repository<K, V> {

  private final Repository<K, V> delegate;

  private final Cache<K, V> readCache = Caffeine.newBuilder()
      .expireAfterAccess(Duration.ofMinutes(1)).maximumSize(100).build();
  private final Map<K, V> pendingUpserts = new ConcurrentHashMap<>();
  private final Set<K> pendingDeletes = ConcurrentHashMap.newKeySet();
  private final int maxBatch = 50;

  private final AtomicBoolean flushing = new AtomicBoolean(false);

  private WriteBehindRepositoryImpl(Repository<K, V> delegate) {
    this.delegate = delegate;
  }

  public static <K, V> Repository<K, V> create(Plugin plugin, Repository<K, V> repository,
      long period) {
    WriteBehindRepositoryImpl<K, V> writeBehindRepository = new WriteBehindRepositoryImpl<>(
        repository);
    Bukkit.getScheduler()
        .runTaskTimerAsynchronously(plugin, writeBehindRepository::flush, 0L, period);
    return writeBehindRepository;
  }

  @Override
  public @Nullable V load(K key) {
    if (pendingDeletes.contains(key)) {
      return null;
    }
    V staged = pendingUpserts.get(key);
    if (staged != null) {
      return staged;
    }
    return readCache.get(key, delegate::load);
  }

  @Override
  public boolean save(K key, V value) {
    pendingDeletes.remove(key);
    pendingUpserts.put(key, value);
    readCache.put(key, value);
    return true;
  }

  @Override
  public void saveAll(Map<K, V> entities) {
    if (entities == null || entities.isEmpty()) {
      return;
    }
    entities.forEach((key, value) -> {
      pendingDeletes.remove(key);
      pendingUpserts.put(key, value);
    });
    readCache.putAll(entities);
  }

  @Override
  public void delete(K key) {
    pendingUpserts.remove(key);
    pendingDeletes.add(key);
    readCache.invalidate(key);
  }

  private void flush() {
    if (!flushing.compareAndSet(false, true)) {
      return;
    }
    try {
      flushOnce();
    } finally {
      flushing.set(false);
    }
  }

  private void flushAll() {
    while (flushOnce()) {
    }
  }

  private boolean flushOnce() {
    Map<K, V> upserts = new LinkedHashMap<>();
    Iterator<Entry<K, V>> iterator = pendingUpserts.entrySet().iterator();
    while (iterator.hasNext() && upserts.size() < maxBatch) {
      Entry<K, V> element = iterator.next();
      K key = element.getKey();
      V value = element.getValue();
      if (pendingUpserts.remove(key, value)) {
        upserts.put(key, value);
      }
    }

    List<K> deletes = new ArrayList<>();
    Iterator<K> keyIterator = this.pendingDeletes.iterator();
    while (keyIterator.hasNext() && deletes.size() < maxBatch) {
      K deletedKey = keyIterator.next();
      if (deletes.remove(deletedKey)) {
        deletes.add(deletedKey);
      }
    }

    if (deletes.isEmpty() && upserts.isEmpty()) {
      return false;
    }

    try {
      if (!upserts.isEmpty()) {
        delegate.saveAll(upserts);
      }
      for (K deletedKey : deletes) {
        delegate.delete(deletedKey);
      }
      return true;
    } catch (Throwable t) {
      upserts.forEach(this.pendingUpserts::putIfAbsent);
      this.pendingDeletes.addAll(deletes);
      throw t;
    }
  }
}
