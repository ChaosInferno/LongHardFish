package org.aincraft.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.aincraft.exception.RepositoryException;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;

public final class RelationalRepositoryImpl<K, V> implements Repository<K, V> {

  private final ConnectionSource connectionSource;

  private final RelationalRepositoryContext<K, V> context;

  private final Cache<K, V> readCache = Caffeine.newBuilder()
      .expireAfterAccess(Duration.ofMinutes(5)).maximumSize(1_000).build();

  public RelationalRepositoryImpl(ConnectionSource connectionSource,
      RelationalRepositoryContext<K, V> context) {
    this.connectionSource = connectionSource;
    this.context = context;
  }

  @Override
  @Nullable
  public V load(K key) {
    return readCache.get(key, ignored -> {
      try (Connection connection = connectionSource.getConnection();
          PreparedStatement ps = connection.prepareStatement(context.getSelectQuery())) {
        context.setKey(ps, key);
        try (ResultSet rs = ps.executeQuery()) {
          return rs.next() ? context.mapResult(rs, key) : null;
        }
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public boolean save(K key, V value) {
    try (Connection connection = connectionSource.getConnection();
        PreparedStatement ps = connection.prepareStatement(context.getSaveQuery())) {
      context.setSaveValues(ps, key, value);
      readCache.put(key, value);
      return ps.executeUpdate() > 0;
    } catch (SQLException e) {
      throw new RuntimeException("failed to save entity for key: " + key, e);
    }
  }

  @Override
  public void saveAll(Map<K, V> entities) {
    if (entities.isEmpty()) {
      return;
    }

    try (Connection connection = connectionSource.getConnection()) {
      connection.setAutoCommit(false);

      try (PreparedStatement ps = connection.prepareStatement(context.getSaveQuery())) {
        for (Map.Entry<K, V> entry : entities.entrySet()) {
          context.setSaveValues(ps, entry.getKey(), entry.getValue());
          ps.addBatch();
        }

        int[] batch = ps.executeBatch();
        connection.commit();

        for (int i = 0; i < batch.length; ++i) {
          int count = batch[i];
          if (!(count >= 0 || count == PreparedStatement.SUCCESS_NO_INFO)) {
            throw new SQLException("Unexpected batch result at index " + i + ": " + count);
          }
        }

        readCache.putAll(entities);

      } catch (SQLException e) {
        try {
          connection.rollback();
        } catch (SQLException rollbackEx) {
          e.addSuppressed(rollbackEx);
        }
        throw new RepositoryException("failed to batch save entities", e);
      } finally {
        try {
          connection.setAutoCommit(true);
        } catch (SQLException ignored) {
        }
      }
    } catch (SQLException e) {
      throw new RepositoryException("failed to establish database connection", e);
    }
  }


  @Override
  public void delete(K key) {
    try (Connection connection = connectionSource.getConnection();
        PreparedStatement ps = connection.prepareStatement(context.getDeleteQuery())) {
      context.setKey(ps, key);
      if (ps.executeUpdate() > 0) {
        readCache.invalidate(key);
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete entity for key: " + key, e);
    }
  }
}
