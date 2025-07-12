package org.aincraft.container;

public enum FishMoonCycle {
    FULL_MOON(0),
    WANING_GIBBOUS(1),
    LAST_QUARTER(2),
    WANING_CRESCENT(3),
    NEW_MOON(4),
    WAXING_CRESCENT(5),
    FIRST_QUARTER(6),
    WAXING_GIBBOUS(7);

    private final int moonDay;

    FishMoonCycle(int moonDay) {
        this.moonDay = moonDay;
    }

    public int getMoonTime() {
        return moonDay;
    }
}
