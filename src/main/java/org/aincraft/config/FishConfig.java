package org.aincraft.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class FishConfig {
    private final String resourcePath;
    private final JavaPlugin plugin;
    private YamlConfiguration config = new YamlConfiguration();
    private File file;

    public FishConfig(String resourcePath, JavaPlugin plugin) {
        this.resourcePath = resourcePath;
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }

        config.options().parseComments(true);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        try {
            file = new File(plugin.getDataFolder(), resourcePath);
            config = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }
}
