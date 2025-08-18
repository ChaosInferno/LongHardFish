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
        // --- Load main fish config ---
        FishConfig config = new FishConfig("fish.yml", this);
        guiListener = new PirateChestListener(this);
        getServer().getPluginManager().registerEvents(guiListener, this);

        // --- Load defaults config (fish_defaults.yml) ---
        saveResource("fish_defaults.yml", false); // Copy from JAR if missing
        File defaultsFile = new File(getDataFolder(), "fish_defaults.yml");
        FileConfiguration defaultsConfig = YamlConfiguration.loadConfiguration(defaultsFile);

        // --- Create Defaults Provider ---
        FishEnvironmentDefaultsProvider defaultsProvider = new FishEnvironmentDefaultsProvider(defaultsConfig, this);

        // --- Create Main Providers ---
        FishEnvironmentProvider environmentProvider = new FishEnvironmentProvider(config, defaultsProvider, this);
        FishRarityProvider rarityProvider = new FishRarityProvider(config, this);
        FishModelProvider modelProvider = new FishModelProvider(config, this);

        // --- Register Fish listener ---
        getServer().getPluginManager().registerEvents(
                new FishCatchListener(environmentProvider, rarityProvider, modelProvider), this
        );

        // --- Register Pirate Chest listener + command ---
        PirateChestListener pirateChest = new PirateChestListener(this); // <-- pass plugin
        getServer().getPluginManager().registerEvents(pirateChest, this);

        PluginCommand cmd = getCommand("piratechest");
        if (cmd != null) {
            cmd.setExecutor(pirateChest);       // reuse the SAME instance
            // If your listener also implements TabCompleter, you can do:
            // cmd.setTabCompleter(pirateChest);
        } else {
            getLogger().severe("Command 'piratechest' is not defined in plugin.yml!");
        }

        // --- Optional Debug Logging ---
        Map<NamespacedKey, FishEnvironment> fishEnvironments = environmentProvider.parseFishEnvironmentObjects();
        Bukkit.getLogger().info("Parsed fish environments:");
        for (Map.Entry<NamespacedKey, FishEnvironment> entry : fishEnvironments.entrySet()) {
            Bukkit.getLogger().info("Fish: " + entry.getKey().getKey());
            Bukkit.getLogger().info("  Biomes: " + entry.getValue().getEnvironmentBiomes());
            Bukkit.getLogger().info("  Times: " + entry.getValue().getEnvironmentTimes());
            Bukkit.getLogger().info("  Moons: " + entry.getValue().getEnvironmentMoons());
        }

        saveDefaultConfig();
        try {
            Path dbFile = getDataFolder().toPath().resolve("data.db");
            db = new SQLiteDatabase(dbFile); // your class from earlier
            db.init();                       // create tables
            stats = new StatsService(this, db);
        } catch (Exception e) {
            getLogger().severe("Failed to init database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        FishStatsCommand statsCmd = new FishStatsCommand(this, stats);
        Objects.requireNonNull(getCommand("fishstats")).setExecutor(statsCmd);
        Objects.requireNonNull(getCommand("fishstats")).setTabCompleter(statsCmd);
    }

    public void onDisable() {
        if (guiListener != null) {
            guiListener.restoreAllMasks();
        }
        try { if (db != null) db.close(); } catch (Exception ignored) {}
    }

    public StatsService stats() { return stats; }
    }


