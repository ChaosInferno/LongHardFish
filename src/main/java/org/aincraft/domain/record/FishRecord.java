package org.aincraft.domain.record;

import java.util.List;
import java.util.Map;

public record FishRecord(String fishKey, String displayName, String description,
                         int identificationNumber, String rarityKey,
                         List<FishEnvironmentRecord> parents,
                         FishEnvironmentRecord environmentRecord, boolean openWaterRequired,
                         boolean rainRequired) {

}
