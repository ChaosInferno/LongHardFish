package org.aincraft.database;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum DatabaseType {
  SQLITE("sqlite", "org.sqlite.JDBC"),
  MYSQL("mysql", "com.mysql.jdbc.Driver");

  private final String identifier;
  private final String className;

  DatabaseType(String identifier, String className) {
    this.identifier = identifier;
    this.className = className;
  }

  public String getIdentifier() {
    return identifier;
  }

  public String getClassName() {
    return className;
  }

  public String[] getSQLTables() {
    try (InputStream resourceStream = ResourceExtractor.getResourceStream(
        String.format("sql/%s.sql", identifier));
        BufferedReader reader = new BufferedReader(new InputStreamReader(resourceStream, StandardCharsets.UTF_8))) {
      Stream<String> lines = reader.lines();
      String tables = lines.collect(Collectors.joining("\n"));
      return Arrays.stream(tables.split(";"))
          .map(s -> s.trim() + ";")
          .filter(s -> !s.equals(";"))
          .toArray(String[]::new);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static DatabaseType getDefault() {
    return SQLITE;
  }
}
