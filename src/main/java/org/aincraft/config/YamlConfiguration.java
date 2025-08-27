package org.aincraft.config;

import java.util.Map;
import java.util.function.Function;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Union interface for {@link FileBackedConfiguration} and {@link ConfigurationSection}
 */
public interface YamlConfiguration extends FileBackedConfiguration, ConfigurationSection {

  @NotNull
  static YamlConfiguration create(Plugin plugin, String path) throws IllegalArgumentException {
    return YamlFileBackedConfigurationImpl.create(plugin, path);
  }
}
