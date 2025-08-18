package org.aincraft.config;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

final class YamlFileBackedConfigurationImpl implements FileBackedConfiguration {

  private final Plugin plugin;
  private final String path;
  private YamlConfiguration config;
  private File configFile;

  YamlFileBackedConfigurationImpl(Plugin plugin, String path) {
    this.plugin = plugin;
    this.path = path;
    this.configFile = new File(plugin.getDataFolder(), path);
    if (!configFile.exists()) {
      plugin.saveResource(path, false);
    }
    assert (configFile != null);
    config = YamlConfiguration.loadConfiguration(configFile);
  }

  static org.aincraft.config.YamlConfiguration create(Plugin plugin, String path) {
    String[] split = path.split("\\.");
    Preconditions.checkArgument(split.length >= 2);
    Preconditions.checkArgument(split[1].equals("yml") || split[1].equals("yaml"));
    YamlFileBackedConfigurationImpl impl = new YamlFileBackedConfigurationImpl(plugin, path);
    YamlConfiguration config = impl.config;
    return (org.aincraft.config.YamlConfiguration) Proxy.newProxyInstance(
        org.aincraft.config.YamlConfiguration.class.getClassLoader(),
        new Class[]{
            org.aincraft.config.YamlConfiguration.class}, (proxy, method, args) -> {
          if ("getPlugin".equals(method.getName())) {
            return impl.getPlugin();
          }
          if ("reload".equals(method.getName())) {
            impl.reload();
            return null;
          }
          if ("save".equals(method.getName())) {
            impl.save();
            return null;
          }
          return method.invoke(config, args);
        });
  }

  @Override
  public @NotNull Plugin getPlugin() {
    return plugin;
  }

  @Override
  public void reload() {
    try {
      configFile = new File(plugin.getDataFolder(), path);
      config = YamlConfiguration.loadConfiguration(configFile);
    } catch (NullPointerException | IllegalArgumentException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void save() {
    try {
      config.save(configFile);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
