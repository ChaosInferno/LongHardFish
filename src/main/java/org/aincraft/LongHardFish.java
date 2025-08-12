package org.aincraft;

import org.aincraft.config.FishConfig;
import org.aincraft.container.FishEnvironment;
import org.aincraft.listener.FishCatchListener;
import org.aincraft.listener.PirateChestListener;
import org.aincraft.provider.FishEnvironmentDefaultsProvider;
import org.aincraft.provider.FishEnvironmentProvider;
import org.aincraft.provider.FishModelProvider;
import org.aincraft.provider.FishRarityProvider;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public class LongHardFish extends JavaPlugin {

    @Override
    public void onEnable() {
        // --- Load main fish config ---
        FishConfig config = new FishConfig("fish.yml", this);

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
        // (This class implements Listener and CommandExecutor; it is NOT a JavaPlugin)
        PirateChestListener pirateChest = new PirateChestListener();
        getServer().getPluginManager().registerEvents(pirateChest, this);
        if (getCommand("piratechest") != null) {
            getCommand("piratechest").setExecutor(pirateChest);
            // Optional: tab completion if your class implements TabCompleter
            // getCommand("piratechest").setTabCompleter(piratechest);
        } else {
            getLogger().warning("Command 'piratechest' is not defined in plugin.yml!");
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
    }
}


