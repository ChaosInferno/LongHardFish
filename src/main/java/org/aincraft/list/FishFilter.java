package org.aincraft.list;

import org.aincraft.container.FishEnvironment;
import org.aincraft.container.FishMoonCycle;
import org.aincraft.container.FishRarity;
import org.aincraft.container.FishTimeCycle;
import org.aincraft.provider.FishEnvironmentProvider;
import org.aincraft.provider.MinecraftTimeParser;
import org.aincraft.service.StatsService;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.*;

public class FishFilter {

    public FishFilter() {}

    public Map<NamespacedKey, Double> getValidFish(
            Player player,
            Location hookLocation,
            FishHook hook,
            FishEnvironmentProvider provider,
            Map<NamespacedKey, FishRarity> rarityMap
    ) {
        Map<NamespacedKey, Double> distributedFish = new HashMap<>();

        Biome currentBiome = hookLocation.getBlock().getBiome();
        World world = hookLocation.getWorld();
        if (world == null) return distributedFish;

        int moonPhaseIndex = (int) (world.getFullTime() / 24000) % 8;
        FishMoonCycle currentMoonPhase = FishMoonCycle.values()[moonPhaseIndex];
        FishTimeCycle currentTime = MinecraftTimeParser.getCurrentTimeCycle();

        // NOTE: you may want to cache this map elsewhere to avoid reparsing every time
        Map<NamespacedKey, FishEnvironment> environments = provider.parseFishEnvironmentObjects();

        final boolean isOpenWater = hook.isInOpenWater();
        final boolean isRaining   = world.hasStorm();

        for (Map.Entry<NamespacedKey, FishEnvironment> entry : environments.entrySet()) {
            NamespacedKey fishKey = entry.getKey();
            FishEnvironment env = entry.getValue();

            FishRarity rarity = rarityMap.get(fishKey);
            if (rarity == null) continue;

            double biomeWeight = env.getEnvironmentBiomes().getOrDefault(currentBiome, 0.0);
            double timeWeight  = env.getEnvironmentTimes().getOrDefault(currentTime, 0.0);
            double moonWeight  = env.getEnvironmentMoons().getOrDefault(currentMoonPhase, 0.0);

            // --- Weather gate: only enforce when required
            boolean rainOk = env.getRainRequired() ? isRaining : true;

            // --- Open-water gate: only enforce when required
            boolean openOk = env.getOpenWaterRequired() ? isOpenWater : true;

            if (biomeWeight != 0.0 && timeWeight != 0.0 && moonWeight != 0.0 && rainOk && openOk) {
                double score = FishRarityCalculator.calculateRarityScore(
                        rarity, env, currentBiome, currentTime, currentMoonPhase
                );
                distributedFish.put(fishKey, score);
            }
        }

        return distributedFish;
    }
}
