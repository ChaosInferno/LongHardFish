package org.aincraft.container;

import org.bukkit.block.Biome;

import java.util.*;

public class FishEnvironment {
    private final Map<Biome, Double> environmentBiomes;
    private final Map<FishTimeCycle, Double> environmentTimes;
    private final Map<FishMoonCycle, Double> environmentMoons;
    private final Integer model;
    private final boolean openWaterRequired;
    private final boolean rainRequired;
    private final Map<String, Double> environmentBaits;

    public FishEnvironment(
            Map<Biome, Double> environmentBiomes,
            Map<FishTimeCycle, Double> environmentTimes,
            Map<FishMoonCycle, Double> environmentMoons,
            Integer model,
            boolean openWaterRequired,
            boolean rainRequired) {
        this(environmentBiomes, environmentTimes, environmentMoons, model, openWaterRequired, rainRequired, Collections.emptyMap());
    }

    public FishEnvironment(
            Map<Biome, Double> environmentBiomes,
            Map<FishTimeCycle, Double> environmentTimes,
            Map<FishMoonCycle, Double> environmentMoons,
            Integer model,
            boolean openWaterRequired,
            boolean rainRequired,
            Map<String, Double> environmentBaits
    ) {
        this.environmentBiomes = Objects.requireNonNull(environmentBiomes, "environmentBiomes");
        this.environmentTimes = Objects.requireNonNull(environmentTimes, "environmentTimes");
        this.environmentMoons = Objects.requireNonNull(environmentMoons, "environmentMoons");
        this.model = model;
        this.openWaterRequired = openWaterRequired;
        this.rainRequired = rainRequired;
        if (environmentBaits == null || environmentBaits.isEmpty()) {
            this.environmentBaits = Collections.emptyMap();
        } else {
            Map<String, Double> copy = new LinkedHashMap<>();
            environmentBaits.forEach((k,v) -> copy.put(
                    k == null ? null : k.toLowerCase(Locale.ENGLISH),
                    v == null ? 0.0 : v
            ));
            this.environmentBaits = Collections.unmodifiableMap(copy);
        }
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

    public Integer getModel() { return model; }

    public boolean getOpenWaterRequired() {
        return openWaterRequired;
    }

    public  boolean getRainRequired() {
        return rainRequired;
    }

    public Map<String, Double> getEnvironmentBaits() { return environmentBaits; }

    public boolean hasBaitBonuses() { return !environmentBaits.isEmpty(); }

    /** Convenience: is the provided bait id acceptable? (null/empty means “no bait on rod”). */
    public double baitBonusFor(String rodBaitId) {
        if (rodBaitId == null) return 0.0;
        Double v = environmentBaits.get(rodBaitId.toLowerCase(Locale.ENGLISH));
        return v == null ? 0.0 : v;
    }
}
