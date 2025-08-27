package org.aincraft.container;

import net.kyori.adventure.key.Keyed;

public interface TimeCycle extends Keyed {

  String label();

  int startTime();

  int endTime();
}
