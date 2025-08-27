package org.aincraft.domain.record;

import java.util.Map;

public record FishRecord(String fishKey, String displayName, String description,
                         int identificationNumber, String rarityKey,
                         Map<String, Double> biomeWeights, Map<String, Double> timeCycleWeights,
                         Map<String, Double> moonWeights, boolean openWaterRequired,
                         boolean rainRequired) {

}
