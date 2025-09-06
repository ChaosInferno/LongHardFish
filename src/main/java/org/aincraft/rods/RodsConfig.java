package org.aincraft.rods;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Objects;

public final class RodsConfig {
    private final FileConfiguration config;
    public RodsConfig(FileConfiguration config) { this.config = Objects.requireNonNull(config); }
    public FileConfiguration getConfig() { return config; }
}
