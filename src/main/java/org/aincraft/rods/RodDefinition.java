// org/aincraft/rods/RodDefinition.java
package org.aincraft.rods;

import org.aincraft.container.FishMoonCycle;
import org.aincraft.container.FishTimeCycle;
import org.aincraft.container.FishRarity;
import org.bukkit.block.Biome;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class RodDefinition {
    public enum Weather { CLEAR, RAIN, STORM }

    public static final class DimBonus {
        private final Map<FishRarity, Double> rarityBonus = new EnumMap<>(FishRarity.class);
        private double speed;        // +0.20 => 20% faster, -0.20 => 20% slower
        private double doubleChance; // 0.15 => 15% double

        public Map<FishRarity, Double> rarityBonus() { return rarityBonus; }
        public double speed() { return speed; }
        public double doubleChance() { return doubleChance; }

        public DimBonus speed(double v) { this.speed = v; return this; }
        public DimBonus doubleChance(double v) { this.doubleChance = v; return this; }
    }

    private final String id;
    private final String description;
    private final int tier;

    private final Map<Biome, Double> biomeBonusFlat = new java.util.HashMap<>();
    private final Map<FishMoonCycle, Double> moonBonusFlat = new EnumMap<>(FishMoonCycle.class);
    private final Map<FishTimeCycle, Double> timeBonusFlat = new EnumMap<>(FishTimeCycle.class);
    private final Map<FishRarity, Double> rarityBonusFlat = new EnumMap<>(FishRarity.class);
    private final Map<Weather, Double> weatherBonus = new EnumMap<>(Weather.class);

    private final Map<FishTimeCycle, DimBonus>  timeBonus  = new EnumMap<>(FishTimeCycle.class);
    private final Map<FishMoonCycle, DimBonus>  moonBonus  = new EnumMap<>(FishMoonCycle.class);
    private final Map<Biome, DimBonus> biomeBonus   = new java.util.HashMap<>();

    public RodDefinition(String id, String description, int tier) {
        this.id = id; this.description = description; this.tier = tier;
    }

    public String id() { return id; }
    public String description() { return description; }
    public int tier() { return tier; }

    public Map<Biome, Double> biomeBonusFlat() { return biomeBonusFlat; }
    public Map<FishMoonCycle, Double> moonBonusFlat() { return moonBonusFlat; }
    public Map<FishTimeCycle, Double> timeBonusFlat() { return timeBonusFlat; }
    public Map<FishRarity, Double> rarityBonus() { return rarityBonusFlat; }
    public Map<Weather, Double> weatherBonus() { return weatherBonus; }

    public Map<FishTimeCycle, DimBonus> timeBonus() { return timeBonus; }
    public Map<FishMoonCycle, DimBonus> moonBonus() { return moonBonus; }
    public Map<Biome, DimBonus> biomeBonus() { return biomeBonus; }

    public DimBonus ensureTime(FishTimeCycle t)   { return timeBonus.computeIfAbsent(t, k -> new DimBonus()); }
    public DimBonus ensureMoon(FishMoonCycle m)   { return moonBonus.computeIfAbsent(m, k -> new DimBonus()); }
    public DimBonus ensureBiome(Biome b)          { return biomeBonus.computeIfAbsent(b, k -> new DimBonus()); }
}

