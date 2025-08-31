package org.aincraft;

import org.aincraft.commands.FishDexCommand;
import org.aincraft.commands.GiveFishItemsCommand;
import org.aincraft.commands.TackleBoxCommand;
import org.aincraft.config.FishConfig;
import org.aincraft.container.FishDistribution;
import org.aincraft.container.FishEnvironment;
import org.aincraft.container.FishModel;
import org.aincraft.gui.FishDexFishSelector;
import org.aincraft.ingame_items.*;
import org.aincraft.items.CustomFishItems;
import org.aincraft.listener.*;
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
    private Database db;          // NEW
    private StatsService stats;
    private FishCatchListener catchListener;
    private FishDexFishSelector fishDex;
    private InventoryBackupService invBackup;
    private FishDexItem fishDexItem;
    private SextantItem sextantItem;
    private WeatherRadioItem weatherRadioItem;
    private WatchItem watchItem;
    private FishFinderItem fishFinderItem;
    private TackleBoxItem tackleBoxItem;
    private BiomeRadarItem biomeRadarItem;

    @Override
    public void onEnable() {
        FishConfig config = new FishConfig("fish.yml", this);
        if (getResource("fish_defaults.yml") != null) {
            saveResource("fish_defaults.yml", false);
        } else {
            getLogger().warning("fish_defaults.yml not embedded; skipping saveResource()");
        }
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

        // Catch listener
        FishCatchListener catchListener = new FishCatchListener(this, environmentProvider, rarityProvider, modelProvider, stats);
        getServer().getPluginManager().registerEvents(catchListener, this);

        // Commands
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
            String keyOnly    = fishKey.getKey();      // "clownfish"
            String namespaced = fishKey.toString();    // "longhardfish:clownfish"
            try {
                // If your Database interface exposes these, use db directly:
                boolean caught =
                        db.hasCaught(playerId, namespaced) || db.hasCaught(playerId, keyOnly);
                if (caught) return FishDexFishSelector.Progress.CAUGHT;

                boolean seen =
                        db.hasDropSeen(playerId, namespaced) || db.hasDropSeen(playerId, keyOnly);
                return seen ? FishDexFishSelector.Progress.SEEN : FishDexFishSelector.Progress.UNSEEN;

            } catch (Exception e) {
                getLogger().warning("Progress lookup failed for " + namespaced + ": " + e.getMessage());
                return FishDexFishSelector.Progress.UNSEEN;
            }
        };

        FishDexFishSelector.CaughtCountLookup countLookup = (playerId, fishKey) -> {
            String keyOnly    = fishKey.getKey();
            String namespaced = fishKey.toString();
            try {
                int n = db.caughtCount(playerId, namespaced);
                if (n == 0) n = db.caughtCount(playerId, keyOnly);
                return n;
            } catch (Exception e) {
                getLogger().warning("Count lookup failed for " + namespaced + ": " + e.getMessage());
                return 0;
            }
        };

        // Backup+clear mask for Dex open
        BiConsumer<Player, Inventory> mask = (p, inv) -> {
            try {
                invBackup.backupIfNeeded(p);
                p.getInventory().clear();
                p.getInventory().setExtraContents(null);
                p.getInventory().setItemInOffHand(null);
                p.setItemOnCursor(null);
            } catch (Exception ex) {
                getLogger().warning("Failed to backup+clear inventory for " + p.getName() + ": " + ex.getMessage());
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
                progressLookup,
                countLookup
        );

        // Register GUI listeners AFTER fishDex exists. Only register ONCE (with invBackup).
        getServer().getPluginManager().registerEvents(new org.aincraft.listener.FishDexGuiListener(fishDex, invBackup), this);

        // FishDex command
        var fishDexCmd = new FishDexCommand(this, fishDex, modelMap.keySet(),modelMap::get, invBackup);
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

        this.fishDexItem = new FishDexItem(this);
        this.sextantItem = new SextantItem(this);
        this.weatherRadioItem = new WeatherRadioItem(this);
        this.watchItem = new WatchItem(this);
        this.fishFinderItem = new FishFinderItem(this);
        this.tackleBoxItem = new TackleBoxItem(this);
        this.biomeRadarItem = new BiomeRadarItem(this);

        // Register current and future items here:
        CustomFishItems.register("fishdex", fishDexItem::create);
        CustomFishItems.register("sextant", sextantItem::create);
        CustomFishItems.register("weather_radio", weatherRadioItem::create);
        CustomFishItems.register("watch", watchItem::create);
        CustomFishItems.register("fish_finder", fishFinderItem::create);
        CustomFishItems.register("tacklebox", tackleBoxItem::create);
        CustomFishItems.register("biome_radar", biomeRadarItem::create);

        getServer().getPluginManager().registerEvents(new FishDexListener(this, fishDexItem), this);
        getServer().getPluginManager().registerEvents(new SextantListener(sextantItem), this);
        getServer().getPluginManager().registerEvents(new WeatherRadioListener(weatherRadioItem), this);
        getServer().getPluginManager().registerEvents(new WatchListener(watchItem), this);
        getServer().getPluginManager().registerEvents(new FishFinderListener(fishFinderItem), this);
        getServer().getPluginManager().registerEvents(new BiomeRadarListener(biomeRadarItem), this);

        // --- TackleBox: persistence service + command + open-on-right-click
        final int tackleBoxSize = 54; // or 27 if you prefer single chest
        TackleBoxService tackleBoxService = new TackleBoxService(this, tackleBoxItem, tackleBoxSize);

        // The service itself listens for clicks & close (saving contents)
        getServer().getPluginManager().registerEvents(tackleBoxService, this);

        // Right-click listener should call service.openFromHand(...)
        // (Make sure your TackleBoxListener constructor accepts the service)
        getServer().getPluginManager().registerEvents(new TackleBoxListener(tackleBoxItem, tackleBoxService), this);

        // /tacklebox opens the GUI for the box in hand
        Objects.requireNonNull(getCommand("tacklebox")).setExecutor(new TackleBoxCommand(this, tackleBoxService));

        getCommand("lhfgive").setExecutor(new GiveFishItemsCommand(this));
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
    }
}


