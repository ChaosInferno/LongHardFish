package org.aincraft.container;

public enum FishTimeCycle {
    DAWN("Morning",22000,1000),
    DAY("Noon",1000,11700),
    EVENING("Evening",11700,14500),
    NIGHT("Midnight",14500,22000);

    private final String frontTimeName;
    private final double startTime;
    private final double endTime;

    FishTimeCycle(String frontTimeName, double startTime, double endTime) {
        this.frontTimeName = frontTimeName;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getFrontTimeName() {
        return frontTimeName;
    }

    public double getStartTime() {
        return startTime;
    }

    public double getEndTime() {
        return endTime;
    }
}
