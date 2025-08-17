package org.aincraft.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

final class HikariSourceImpl implements ConnectionSource {

  private final HikariDataSource source;

  private final DatabaseType type;

  HikariSourceImpl(HikariConfig config, DatabaseType type) {
    this.source = new HikariDataSource(config);
    this.type = type;
  }

  @Override
  public void shutdown() throws SQLException {
    if (source.isClosed()) {
      return;
    }
    source.close();
  }

  @Override
  public DatabaseType getType() {
    return type;
  }

  @Override
  public boolean isClosed() {
    return source.isClosed();
  }

  @Override
  public Connection getConnection() throws SQLException {
    return source.getConnection();
  }

  @Override
  public boolean isSetup() {
    return true;
  }
}
