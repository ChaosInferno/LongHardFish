package org.aincraft.domain.record;

import java.util.Map;

public record FishEnvironmentRecord(Map<String, Double> biomeWeights, Map<String, Double> timeCycleWeights,
                                    Map<String, Double> moonWeights) {

}
