package org.aincraft.database;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

final class SQLiteSourceImpl implements ConnectionSource {

  private final Plugin plugin;
  private NonClosableConnection connection;

  private SQLiteSourceImpl(Plugin plugin, NonClosableConnection connection) {
    this.plugin = plugin;
    this.connection = connection;
  }

  static SQLiteSourceImpl create(@NotNull Plugin plugin) {
    Path databaseFilePath = getDatabaseFilePath(plugin);
    try {
      Class.forName(DatabaseType.SQLITE.getClassName());
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    File databaseFile = new File(databaseFilePath.toString());
    File parentFile = databaseFile.getParentFile();
    if (!parentFile.exists()) {
      parentFile.mkdirs();
    }
    if (!databaseFile.exists()) {
      try {
        if (!databaseFile.createNewFile()) {
          throw new IOException("failed to create database flat file");
        }
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }
    try {
      return new SQLiteSourceImpl(plugin,
          NonClosableConnection.create(DriverManager.getConnection(getUrl(databaseFilePath))));
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private static String getUrl(@NotNull Path databaseFilePath) {
    return String.format("jdbc:%s:%s", DatabaseType.SQLITE.getIdentifier(),
        databaseFilePath.toAbsolutePath());
  }

  @NotNull
  private static Path getDatabaseFilePath(Plugin plugin) {
    Path parentPath = plugin.getDataFolder().toPath();
    return parentPath.resolve(String.format("%s.db", plugin.getName().toLowerCase(Locale.ENGLISH)));
  }

  @Override
  public void shutdown() throws SQLException {
    connection.shutdown();
  }

  @Override
  public DatabaseType getType() {
    return DatabaseType.SQLITE;
  }

  @Override
  public boolean isClosed() {
    try {
      return connection.isClosed();
    } catch (SQLException ex) {
      throw new RuntimeException();
    }
  }

  @Override
  public Connection getConnection() {
    try {
      if (connection == null || connection.isClosed()) {
        connection = NonClosableConnection.create(
            DriverManager.getConnection(getUrl(getDatabaseFilePath(plugin))));
        try (Statement stmt = connection.createStatement()) {
          stmt.execute("PRAGMA foreign_keys = ON;");
          stmt.execute("PRAGMA journal_mode = WAL;");
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    return connection;
  }

  @Override
  public boolean isSetup() {
    try (Connection connection = getConnection()) {
      PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' LIMIT 1");
      ResultSet rs = ps.executeQuery();
      return rs.next();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
