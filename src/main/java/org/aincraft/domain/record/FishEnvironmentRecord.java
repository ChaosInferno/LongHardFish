package org.aincraft.domain.record;

import java.util.Map;

public record FishEnvironmentRecord(Map<String, Double> biomeWeights, Map<String, Double> timeWeights,
                                    Map<String, Double> moonWeights) {

}
