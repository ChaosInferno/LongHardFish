package org.aincraft.domain;

import java.util.List;
import org.aincraft.domain.record.RarityRecord;

public interface RarityRepository {
  List<RarityRecord> getAllRarities();
}
