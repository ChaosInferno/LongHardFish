package org.aincraft.provider;

import org.aincraft.container.FishTimeCycle;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class MinecraftTimeParser {

    public static int getCurrentMinecraftDay() {
        // Get the default world (usually "world")
        World world = Bukkit.getWorlds().get(0); // or Bukkit.getWorld("world")

        if (world == null) {
            throw new IllegalStateException("World not found.");
        }

        // getFullTime returns the total number of ticks since world start
        long fullDay = world.getFullTime();

        return (int)(fullDay / 24000);
    }

    public static int getCurrentMinecraftTime() {
        World world = Bukkit.getWorlds().get(0);

        if (world == null) {
            throw new IllegalStateException("World not found.");
        }

        long fullTicks = world.getFullTime();
        return (int) (fullTicks % 24000);
    }

    public static FishTimeCycle getCurrentTimeCycle() {
        int currentTime = getCurrentMinecraftTime();

        for (FishTimeCycle cycle : FishTimeCycle.values()) {
            double start = cycle.getStartTime();
            double end = cycle.getEndTime();

            if (start < end) {
                if (currentTime >= start && currentTime < end) {
                    return cycle;
                }
            } else {
                // Handle wrap-around times like DAWN (22000–1000)
                if (currentTime >= start || currentTime < end) {
                    return cycle;
                }
            }
        }

        return FishTimeCycle.DAY; // Default fallback
    }
}
