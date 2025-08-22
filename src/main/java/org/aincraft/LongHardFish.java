package org.aincraft;

import org.aincraft.commands.FishDexCommand;
import org.aincraft.config.FishConfig;
import org.aincraft.container.FishDistribution;
import org.aincraft.container.FishEnvironment;
import org.aincraft.container.FishModel;
import org.aincraft.gui.FishDexFishSelector;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public class LongHardFish extends JavaPlugin {
    private PirateChestListener guiListener;
    private Database db;          // NEW
    private StatsService stats;
    private FishCatchListener catchListener;
    private FishDexFishSelector fishDex;

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

        catchListener = new FishCatchListener(this, environmentProvider, rarityProvider, modelProvider, stats);
        getServer().getPluginManager().registerEvents(catchListener, this);

        PluginCommand chestCmd = getCommand("piratechest");
        if (chestCmd != null) chestCmd.setExecutor(guiListener);
        FishStatsCommand statsCmd = new FishStatsCommand(this, stats);
        Objects.requireNonNull(getCommand("fishstats")).setExecutor(statsCmd);
        Objects.requireNonNull(getCommand("fishstats")).setTabCompleter(statsCmd);

        // --- Build lookups for the selector ---
        Map<NamespacedKey, FishEnvironment> envMap = environmentProvider.parseFishEnvironmentObjects();
        Map<NamespacedKey, FishModel>       modelMap = modelProvider.parseFishModelObjects();
        Map<NamespacedKey, FishDistribution>   distMap  = rarityProvider.parseFishDistributorObjects();
        Map<NamespacedKey, Integer> tierMap = environmentProvider.parseFishTierMap();

        // Key used by GuiItemSlot to lock icons in place
        NamespacedKey immovableKey = new NamespacedKey(this, "immovable");

        // Optional: mask the GUI with your existing listener (no-op here for testing)
        BiConsumer<Player, Inventory> mask = (p, inv) -> { /* no-op */ };

        // Create selector
        fishDex = FishDexFishSelector.createWithPaging(
                this,
                immovableKey,
                mask,
                envMap::get,     // EnvLookup
                modelMap::get,   // ModelLookup
                distMap::get,    // DistributionLookup (rarity)
                () -> modelMap.keySet(),
                tierMap::get
        );

        getServer().getPluginManager().registerEvents(new org.aincraft.listener.FishDexGuiListener(fishDex), this);

        var fishDexCmd = new FishDexCommand(this, fishDex, modelMap.keySet());
        Objects.requireNonNull(getCommand("fishdex")).setExecutor(fishDexCmd);
        Objects.requireNonNull(getCommand("fishdex")).setTabCompleter(fishDexCmd);

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


