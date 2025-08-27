package org.aincraft.domain;

import java.util.List;
import org.aincraft.domain.record.FishRecord;

public interface FishProvider {

  List<FishRecord> getAllFish();
}
