package org.aincraft.container;

import org.bukkit.block.Biome;

import java.util.Map;

public enum FishRarity {
    COMMON("C",0.7),
    UNCOMMON("B",0.2),
    RARE("A",0.0985),
    LEGENDARY("S",0.0015),
    EVENT("SS",0);

    private final String frontRarityName;
    private final double backRarityValue;

    FishRarity(String frontRarityName, double backRarityValue) {
        this.frontRarityName = frontRarityName;
        this.backRarityValue = backRarityValue;
    }

    public double getBackRarityValue() {
        return backRarityValue;
    }

    public String getFrontRarityName() {
        return frontRarityName;
    }
}