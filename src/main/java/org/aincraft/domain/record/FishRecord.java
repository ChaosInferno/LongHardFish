package org.aincraft.domain.record;

import java.util.Map;

public record FishRecord(String fishKey, String displayName, String description,
                         int identificationNumber, String rarityKey,
                         FishEnvironmentRecord environmentRecord, boolean openWaterRequired,
                         boolean rainRequired) {

}
