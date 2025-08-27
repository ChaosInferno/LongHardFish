package org.aincraft.list;

import org.aincraft.container.FishTimeCycle;
import org.aincraft.provider.FishEnvironmentProvider;
import org.aincraft.provider.MinecraftTimeParser;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;

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

        Map<NamespacedKey, FishEnvironment> environments = provider.parseFishEnvironmentObjects();

        for (Map.Entry<NamespacedKey, FishEnvironment> entry : environments.entrySet()) {
            NamespacedKey fishKey = entry.getKey();
            FishEnvironment env = entry.getValue();

            FishRarity rarity = rarityMap.get(fishKey);
            if (rarity == null) continue;

            if (!rarityMap.containsKey(fishKey)) {
                player.sendMessage(" - No rarity found for " + fishKey);
            }

            double biomeWeight = env.getEnvironmentBiomes().getOrDefault(currentBiome, 0.0);
            double timeWeight = env.getEnvironmentTimes().getOrDefault(currentTime, 0.0);
            double moonWeight = env.getEnvironmentMoons().getOrDefault(currentMoonPhase, 0.0);
            boolean rainMatch = env.getRainRequired();
            boolean rainCheck = true;
            boolean openWaterMatch = env.getOpenWaterRequired();
            boolean openWaterCheck = hook.isInOpenWater();

            if (rainMatch && !world.hasStorm()) {
                rainCheck = false;
            }

            if (!openWaterMatch && hook.isInOpenWater()) {
                openWaterCheck = false;
            }

            if (!openWaterMatch && !hook.isInOpenWater()) {
                openWaterCheck = true;
            }

            if (biomeWeight != 0 && timeWeight != 0 && moonWeight != 0 && rainCheck && openWaterCheck) {
                double score = FishRarityCalculator.calculateRarityScore(
                        rarity, env, currentBiome, currentTime, currentMoonPhase
                );
                distributedFish.put(fishKey, score);
            }
        }

        return distributedFish;
    }
}
