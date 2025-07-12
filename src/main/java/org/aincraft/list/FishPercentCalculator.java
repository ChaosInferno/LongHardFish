package org.aincraft.list;

import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;

public class FishPercentCalculator {

    /**
     * Calculates percentage chances of catching each fish based on score.
     *
     * @param distributedFish A map of fish and their calculated rarity scores.
     * @return A map of fish and their percentage chances (0.0 - 100.0).
     */

    public static Map<NamespacedKey, Double> calculatePercentages(Map<NamespacedKey, Double> distributedFish) {
        Map<NamespacedKey, Double> percentageMap = new HashMap<>();

        // Calculate the total score of all fish
        double totalScore = 0.0;
        for (double score : distributedFish.values()) {
            totalScore += score;
        }

        // Prevent division by zero
        if (totalScore == 0.0) {
            return percentageMap;
        }

        // Convert each fish score into a percentage
        for (Map.Entry<NamespacedKey, Double> entry : distributedFish.entrySet()) {
            double percentage = (entry.getValue() / totalScore) * 100.0;
            percentageMap.put(entry.getKey(), percentage);
        }

        return percentageMap;
    }
}
