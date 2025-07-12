package org.aincraft.container;

public class FishModel {
    private final String name;
    private final int model;
    private final String description;

    public FishModel(String name, int model, String description) {
        this.name = name;
        this.model = model;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public int getModel() {
        return model;
    }

    public String getDescription() {
        return description;
    }
}