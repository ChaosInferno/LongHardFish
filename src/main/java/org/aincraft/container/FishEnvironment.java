package org.aincraft.container;

import org.bukkit.block.Biome;

import java.util.Map;

public class FishEnvironment {
    private final Map<Biome, Double> environmentBiomes;
    private final Map<FishTimeCycle, Double> environmentTimes;
    private final Map<FishMoonCycle, Double> environmentMoons;
    private final boolean openWaterRequired;
    private final boolean rainRequired;

    public FishEnvironment(Map<Biome, Double> environmentBiomes, Map<FishTimeCycle, Double> environmentTimes, Map<FishMoonCycle, Double> environmentMoons, boolean openWaterRequired, boolean rainRequired) {
        this.environmentBiomes = environmentBiomes;
        this.environmentTimes = environmentTimes;
        this.environmentMoons = environmentMoons;
        this.openWaterRequired = openWaterRequired;
        this.rainRequired = rainRequired;
    }

    public Map<FishMoonCycle, Double> getEnvironmentMoons() {
        return environmentMoons;
    }

    public  Map<FishTimeCycle, Double> getEnvironmentTimes() {
        return environmentTimes;
    }

    public Map<Biome, Double> getEnvironmentBiomes() {
        return environmentBiomes;
    }

    public boolean getOpenWaterRequired() {
        return openWaterRequired;
    }

    public  boolean getRainRequired() {
        return rainRequired;
    }
}
