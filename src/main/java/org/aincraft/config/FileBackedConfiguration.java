package org.aincraft.config;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a physical file and its operations for a configuration
 */
public interface FileBackedConfiguration {

  @NotNull
  Plugin getPlugin();

  void reload();

  void save();
}
