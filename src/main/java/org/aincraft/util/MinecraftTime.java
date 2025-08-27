package org.aincraft.util;

import org.bukkit.Bukkit;
import org.bukkit.World;

public class MinecraftTime {

  public static int getCurrent(World world) {
    long fullTicks = world.getFullTime();
    return (int) (fullTicks % 24000);
  }
}
