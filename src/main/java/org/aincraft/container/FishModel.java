package org.aincraft.container;

public class FishModel {
    private final String name;
    private final String description;
    private final int modelNumber;

    public FishModel(String name, String description, int modelNumber) {
        this.name = name;
        this.description = description;
        this.modelNumber = modelNumber;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getModelNumber() { return modelNumber; }
}