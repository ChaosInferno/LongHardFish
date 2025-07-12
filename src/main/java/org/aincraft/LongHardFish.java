package org.aincraft;

import org.aincraft.config.FishConfig;
import org.aincraft.container.FishEnvironment;
import org.aincraft.listener.FishCatchListener;
import org.aincraft.provider.FishEnvironmentProvider;
import org.aincraft.provider.FishRarityProvider;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class LongHardFish extends JavaPlugin {

    @Override
    public void onEnable() {
        // Load config file
        FishConfig config = new FishConfig("fish.yml", this);

        // Create providers
        FishEnvironmentProvider environmentProvider = new FishEnvironmentProvider(config, this);
        FishRarityProvider rarityProvider = new FishRarityProvider(config, this);

        // Register event listener with both providers
        getServer().getPluginManager().registerEvents(
                new FishCatchListener(environmentProvider, rarityProvider), this
        );

        // Log fish environments for debugging
        Map<NamespacedKey, FishEnvironment> fishEnvironments = environmentProvider.parseFishEnvironmentObjects();
        Bukkit.getLogger().info("Parsed fish environments:");
        for (Map.Entry<NamespacedKey, FishEnvironment> entry : fishEnvironments.entrySet()) {
            Bukkit.getLogger().info("Fish: " + entry.getKey().getKey());
            Bukkit.getLogger().info("Biomes: " + entry.getValue().getEnvironmentBiomes());
            Bukkit.getLogger().info("Times: " + entry.getValue().getEnvironmentTimes());
            Bukkit.getLogger().info("Moons: " + entry.getValue().getEnvironmentMoons());
        }
    }
}