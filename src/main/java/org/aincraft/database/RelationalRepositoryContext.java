package org.aincraft.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface RelationalRepositoryContext<K, V> {

  String getSelectQuery();

  String getSaveQuery();

  String getDeleteQuery();

  void setKey(PreparedStatement ps, K key) throws SQLException;

  void setSaveValues(PreparedStatement ps, K key, V value) throws SQLException;

  V mapResult(ResultSet rs, K key) throws SQLException;
}
