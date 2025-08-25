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
import org.aincraft.service.InventoryBackupService;
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
    private InventoryBackupService invBackup;

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

        // --- DB + Stats ---
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

        // --- Inventory backup service (init its schema) ---
        invBackup = new InventoryBackupService((SQLiteDatabase) db);
        try {
            invBackup.init();
        } catch (Exception ex) {
            getLogger().severe("Failed to init InventoryBackupService: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        stats.refreshFishNamesAsync(modelProvider.parseFishModelObjects());

        // Usual listeners
        guiListener = new PirateChestListener(this);
        getServer().getPluginManager().registerEvents(guiListener, this);

        // Catch listener
        FishCatchListener catchListener = new FishCatchListener(this, environmentProvider, rarityProvider, modelProvider, stats);
        getServer().getPluginManager().registerEvents(catchListener, this);

        // Commands
        PluginCommand chestCmd = getCommand("piratechest");
        if (chestCmd != null) chestCmd.setExecutor(guiListener);
        FishStatsCommand statsCmd = new FishStatsCommand(this, stats);
        Objects.requireNonNull(getCommand("fishstats")).setExecutor(statsCmd);
        Objects.requireNonNull(getCommand("fishstats")).setTabCompleter(statsCmd);

        // --- Build lookups for the selector ---
        Map<NamespacedKey, FishEnvironment> envMap = environmentProvider.parseFishEnvironmentObjects();
        Map<NamespacedKey, FishModel> modelMap = modelProvider.parseFishModelObjects();
        Map<NamespacedKey, FishDistribution> distMap = rarityProvider.parseFishDistributorObjects();
        Map<NamespacedKey, Integer> tierMap = environmentProvider.parseFishTierMap();

        // Key used by GuiItemSlot to lock icons in place
        NamespacedKey immovableKey = new NamespacedKey(this, "immovable");

        FishDexFishSelector.ProgressLookup progressLookup = (playerId, fishKey) -> {
            try {
                if (db.hasCaught(playerId, fishKey.getKey())) {
                    return FishDexFishSelector.Progress.CAUGHT;
                }
                if (db.hasDropSeen(playerId, fishKey.getKey())) {
                    return FishDexFishSelector.Progress.SEEN;
                }
                return FishDexFishSelector.Progress.UNSEEN;
            } catch (Exception e) {
                getLogger().warning("Progress lookup failed for " + fishKey + ": " + e.getMessage());
                return FishDexFishSelector.Progress.UNSEEN;
            }
        };

        // IMPORTANT: make the mask back up the player's inventory *before* we start writing icons to it
        BiConsumer<Player, Inventory> mask = (p, inv) -> {
            try {
                invBackup.backupIfNeeded(p);  // persists current player inv + marks as "masked"
            } catch (Exception ex) {
                getLogger().warning("Failed to backup inventory for " + p.getName() + ": " + ex.getMessage());
            }
        };

        // Create selector (now we have all lookups & the backup mask)
        fishDex = FishDexFishSelector.createWithPaging(
                this,
                immovableKey,
                mask,
                envMap::get,     // EnvLookup
                modelMap::get,   // ModelLookup
                distMap::get,    // DistributionLookup (rarity)
                () -> modelMap.keySet(), // all fish ids for paging
                tierMap::get,     // tier lookup
                progressLookup
        );

        // Register GUI listeners AFTER fishDex exists. Only register ONCE (with invBackup).
        getServer().getPluginManager().registerEvents(new org.aincraft.listener.FishDexGuiListener(fishDex, invBackup), this);

        // FishDex command
        var fishDexCmd = new FishDexCommand(this, fishDex, modelMap.keySet(), invBackup);
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

    @Override
    public void onDisable() {
        // Try to restore any online players who still have a masked inventory
        if (invBackup != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    invBackup.restoreIfPresent(p);
                } catch (Exception ex) {
                    getLogger().warning("Failed to restore inventory for " + p.getName() + ": " + ex.getMessage());
                }
            }
        }

        if (guiListener != null) {
            guiListener.restoreAllMasks();
        }
        try {
            if (db != null) db.close();
        } catch (Exception ignored) {
        }
    }
}


