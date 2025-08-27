package org.aincraft.domain;

import java.util.List;
import org.aincraft.domain.record.FishEnvironmentRecord;

public interface FishEnvironmentRepository {

  List<FishEnvironmentRecord> getAllEnvironments();

}
