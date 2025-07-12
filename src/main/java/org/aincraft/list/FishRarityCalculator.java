package org.aincraft.list;

import org.aincraft.container.FishEnvironment;
import org.aincraft.container.FishMoonCycle;
import org.aincraft.container.FishRarity;
import org.aincraft.container.FishTimeCycle;
import org.bukkit.block.Biome;

public class FishRarityCalculator {

    /**
     * Calculates the rarity score of a fish based on biome, time, moon cycle, and base rarity.
     *
     * @param rarity The base rarity (e.g., UNCOMMON)
     * @param environment The environment modifiers from config
     * @param currentBiome The biome the player is fishing in
     * @param timeCycle The current time cycle (dawn, day, etc.)
     * @param moonCycle The current moon phase
     * @return A final calculated rarity score (the higher, the more likely the fish appears)
     */
    public static double calculateRarityScore(
            FishRarity rarity,
            FishEnvironment environment,
            Biome currentBiome,
            FishTimeCycle timeCycle,
            FishMoonCycle moonCycle
    ) {
        double base = rarity.getBackRarityValue(); // e.g., 0.3 for UNCOMMON

        double biomeWeight = environment.getEnvironmentBiomes().getOrDefault(currentBiome, 0.0);
        double timeWeight = environment.getEnvironmentTimes().getOrDefault(timeCycle, 0.0);
        double moonWeight = environment.getEnvironmentMoons().getOrDefault(moonCycle, 0.0);

        if (!(biomeWeight == 0 && timeWeight == 0 && moonWeight == 0)) {
            return base * (1.0 + biomeWeight + timeWeight + moonWeight);
        }
        else {
            return 0;
        }
    }
}
