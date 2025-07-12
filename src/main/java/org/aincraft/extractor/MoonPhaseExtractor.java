package org.aincraft.extractor;

import org.aincraft.container.FishEnvironment;
import org.aincraft.container.FishMoonCycle;
import org.aincraft.provider.FishEnvironmentProvider;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;

public class MoonPhaseExtractor {
    public static Map<NamespacedKey, Map<FishMoonCycle, Double>> getMoonPhaseData(FishEnvironmentProvider provider) {
        Map<NamespacedKey, FishEnvironment> fullMap = provider.parseFishEnvironmentObjects();
        Map<NamespacedKey, Map<FishMoonCycle, Double>> moonPhaseMap = new HashMap<>();

        for (Map.Entry<NamespacedKey, FishEnvironment> entry : fullMap.entrySet()) {
            NamespacedKey key = entry.getKey();
            FishEnvironment env = entry.getValue();
            Map<FishMoonCycle, Double> moonData = env.getEnvironmentMoons();
            moonPhaseMap.put(key, moonData);
        }

        return moonPhaseMap;
    }
}
