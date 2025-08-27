package org.aincraft;

import com.google.inject.AbstractModule;
import org.aincraft.math.MathModule;
import org.bukkit.plugin.Plugin;

public final class PluginModule extends AbstractModule {

  private final Plugin plugin;

  public PluginModule(Plugin plugin) {
    this.plugin = plugin;
  }

  @Override
  protected void configure() {
    bind(Plugin.class).toInstance(plugin);
    install(new MathModule());
  }
}
