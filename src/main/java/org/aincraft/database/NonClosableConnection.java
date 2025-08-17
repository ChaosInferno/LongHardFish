package org.aincraft.database;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@Internal
interface NonClosableConnection extends Connection {

  static NonClosableConnection create(@NotNull Connection delegate) {
    return (NonClosableConnection) Proxy.newProxyInstance(
        NonClosableConnection.class.getClassLoader(),
        new Class[]{NonClosableConnection.class}, (proxy, method, args) -> {
          if ("close".equals(method.getName())) {
            return null;
          }
          if ("shutdown".equals(method.getName())) {
            delegate.close();
            return null;
          }
          return method.invoke(delegate, args);
        });
  }

  void shutdown() throws SQLException;
}
