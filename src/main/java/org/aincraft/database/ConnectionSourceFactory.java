package org.aincraft.database;

import com.google.common.base.Preconditions;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class ConnectionSourceFactory {

  @NotNull
  private final Plugin plugin;

  @NotNull
  private final ConfigurationSection configuration;

  public ConnectionSourceFactory(@NotNull Plugin plugin, @NotNull ConfigurationSection configuration) {
    this.plugin = plugin;
    this.configuration = configuration;
  }

  @NotNull
  public ConnectionSource create()
      throws NullPointerException, IllegalArgumentException, SQLException {
    Preconditions.checkArgument(configuration.contains("database"));
    ConfigurationSection databaseSection = configuration.getConfigurationSection("database");
    Preconditions.checkNotNull(databaseSection);
    String typeString = databaseSection.getString("type");
    DatabaseType databaseType =
        typeString != null ? DatabaseType.valueOf(typeString.toUpperCase(Locale.ENGLISH))
            : DatabaseType.getDefault();
    plugin.getLogger().info("creating database connection source, database type: " + databaseType.toString());
    ConnectionSource connectionSource = switch (databaseType) {
      case SQLITE -> SQLiteSourceImpl.create(plugin);
      case MYSQL -> {
        HikariConfigProvider hikariConfigProvider = new HikariConfigProvider(databaseSection);
        yield new HikariSourceImpl(hikariConfigProvider.create(databaseType), databaseType);
      }
    };
    if (!connectionSource.isSetup()) {
      String[] tables = databaseType.getSQLTables();
      try (Connection connection = connectionSource.getConnection()) {
        connection.setAutoCommit(false);
        Savepoint savepoint = connection.setSavepoint();

        try (Statement stmt = connection.createStatement()) {
          for (String query : tables) {
            stmt.addBatch(query);
          }
          stmt.executeBatch();
          connection.commit();
        } catch (SQLException e) {
          connection.rollback(savepoint);
          throw new SQLException("Error executing bulk SQL", e);
        }
      }
    }
    return connectionSource;
  }
}
