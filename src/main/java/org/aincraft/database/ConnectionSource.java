package org.aincraft.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionSource {

  void shutdown() throws SQLException;

  DatabaseType getType();

  boolean isClosed();

  Connection getConnection() throws SQLException;

  boolean isSetup();
}
