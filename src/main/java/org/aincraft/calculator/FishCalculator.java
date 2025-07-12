package org.aincraft.calculator;

import org.bukkit.NamespacedKey;

import java.util.Map;
import java.util.Random;

public class FishCalculator {

    private final Map<NamespacedKey, Double> percentageMap;
    private final Random random = new Random();

    public FishCalculator(Map<NamespacedKey, Double> percentageMap) {
        this.percentageMap = percentageMap;
    }

    /**
     * Selects a random fish based on weighted percentage chances.
     *
     * @return The randomly selected fish's NamespacedKey, or null if the map is empty.
     */

    public NamespacedKey getRandomFish() {
        if (percentageMap.isEmpty()) {
            return null;
        }

        // Calculate total percentage
        double totalWeight = 0;
        for (double weight : percentageMap.values()) {
            totalWeight += weight;
        }

        // Roll a random number within that total
        double roll = random.nextDouble() * totalWeight;

        // Select fish based on weighted range
        double cumulative = 0;
        for (Map.Entry<NamespacedKey, Double> entry : percentageMap.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }
        // Should not be reached if weights sum correctly
        return null;
    }
}
