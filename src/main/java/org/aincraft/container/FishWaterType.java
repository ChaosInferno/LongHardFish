package org.aincraft.container;

public enum FishWaterType {
    OPEN("Lakes & Rivers"),
    CLOSED("Ponds"),
    BOTH("Anywhere");

    private final String frontWaterBodyName;

    FishWaterType(String frontWaterBodyName) {
        this.frontWaterBodyName = frontWaterBodyName;
    }

    public String getFrontRarityName() {
        return frontWaterBodyName;
    }
}