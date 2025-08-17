package org.aincraft.database;

import com.google.common.base.Preconditions;
import com.zaxxer.hikari.HikariConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

final class HikariConfigProvider {

  @NotNull
  private final ConfigurationSection databaseSection;

  HikariConfigProvider(@NotNull ConfigurationSection databaseSection) {
    this.databaseSection = databaseSection;
  }

  @NotNull
  public HikariConfig create(DatabaseType databaseType)
      throws NullPointerException {
    Preconditions.checkNotNull(databaseSection);
    HikariConfig hikariConfig = new HikariConfig();

    String jdbcUrl = databaseSection.getString("jdbc-url");
    String username = databaseSection.getString("username");
    String password = databaseSection.getString("password");

    Preconditions.checkNotNull(jdbcUrl, "missing required field: database.jdbc-url");
    Preconditions.checkNotNull(username, "missing required field: database.username");
    Preconditions.checkNotNull(password, "missing required field: database.password");

    hikariConfig.setJdbcUrl(jdbcUrl);
    hikariConfig.setUsername(username);
    hikariConfig.setPassword(password);
    hikariConfig.setDriverClassName(databaseType.getClassName());

    int maxPoolSize = databaseSection.getInt("maximum-pool-size", -1);
    if (maxPoolSize > 0) {
      hikariConfig.setMaximumPoolSize(maxPoolSize);
    }

    int minIdle = databaseSection.getInt("minimum-idle", -1);
    if (minIdle >= 0) {
      hikariConfig.setMinimumIdle(minIdle);
    }

    long connectionTimeout = databaseSection.getLong("connection-timeout", -1);
    if (connectionTimeout > 0) {
      hikariConfig.setConnectionTimeout(connectionTimeout);
    }

    long idleTimeout = databaseSection.getLong("idle-timeout", -1);
    if (idleTimeout > 0) {
      hikariConfig.setIdleTimeout(idleTimeout);
    }

    long maxLifetime = databaseSection.getLong("max-lifetime", -1);
    if (maxLifetime > 0) {
      hikariConfig.setMaxLifetime(maxLifetime);
    }
    return hikariConfig;
  }
}
