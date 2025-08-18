package org.aincraft;

import org.aincraft.config.FishConfig;
import org.aincraft.container.FishEnvironment;
import org.aincraft.listener.FishCatchListener;
import org.aincraft.listener.PirateChestListener;
import org.aincraft.provider.FishEnvironmentDefaultsProvider;
import org.aincraft.provider.FishEnvironmentProvider;
import org.aincraft.provider.FishModelProvider;
import org.aincraft.provider.FishRarityProvider;
import org.aincraft.commands.FishStatsCommand;
import org.aincraft.service.StatsService;
import org.aincraft.storage.Database;
import org.aincraft.storage.SQLiteDatabase;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public class LongHardFish extends JavaPlugin {
    private PirateChestListener guiListener;
    private Database db;          // NEW
    private StatsService stats;

    @Override
    public void onEnable() {
        FishConfig config = new FishConfig("fish.yml", this);
        saveResource("fish_defaults.yml", false);
        File defaultsFile = new File(getDataFolder(), "fish_defaults.yml");
        FileConfiguration defaultsConfig = YamlConfiguration.loadConfiguration(defaultsFile);

        FishEnvironmentDefaultsProvider defaultsProvider = new FishEnvironmentDefaultsProvider(defaultsConfig, this);
        FishEnvironmentProvider environmentProvider = new FishEnvironmentProvider(config, defaultsProvider, this);
        FishRarityProvider rarityProvider = new FishRarityProvider(config, this);
        FishModelProvider modelProvider = new FishModelProvider(config, this);

        // --- DB + Stats (MUST be before registering FishCatchListener) ---
        saveDefaultConfig();
        try {
            Path dbFile = getDataFolder().toPath().resolve("data.db");
            db = new SQLiteDatabase(dbFile);
            db.init();
            stats = new StatsService(this, db);
            getLogger().info("Stats service initialized.");
        } catch (Exception e) {
            getLogger().severe("Failed to init database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        stats.refreshFishNamesAsync(modelProvider.parseFishModelObjects());

        guiListener = new PirateChestListener(this);
        getServer().getPluginManager().registerEvents(guiListener, this);
        getServer().getPluginManager().registerEvents(
                new FishCatchListener(this, environmentProvider, rarityProvider, modelProvider, stats),
                this);

        PluginCommand chestCmd = getCommand("piratechest");
        if (chestCmd != null) chestCmd.setExecutor(guiListener);
        FishStatsCommand statsCmd = new FishStatsCommand(this, stats);
        Objects.requireNonNull(getCommand("fishstats")).setExecutor(statsCmd);
        Objects.requireNonNull(getCommand("fishstats")).setTabCompleter(statsCmd);

        // --- Fish catch listener (AFTER stats exists) ---
        getServer().getPluginManager().registerEvents(
                new FishCatchListener(this, environmentProvider, rarityProvider, modelProvider, stats),
                this
        );

        // --- Optional debug logging ---
        Map<NamespacedKey, FishEnvironment> fishEnvironments = environmentProvider.parseFishEnvironmentObjects();
        Bukkit.getLogger().info("Parsed fish environments:");
        for (Map.Entry<NamespacedKey, FishEnvironment> entry : fishEnvironments.entrySet()) {
            Bukkit.getLogger().info("Fish: " + entry.getKey().getKey());
            Bukkit.getLogger().info("  Biomes: " + entry.getValue().getEnvironmentBiomes());
            Bukkit.getLogger().info("  Times: " + entry.getValue().getEnvironmentTimes());
            Bukkit.getLogger().info("  Moons: " + entry.getValue().getEnvironmentMoons());
        }
    }


    public void onDisable() {
        if (guiListener != null) {
            guiListener.restoreAllMasks();
        }
        try { if (db != null) db.close(); } catch (Exception ignored) {}
    }

    public StatsService stats() { return stats; }
    }


