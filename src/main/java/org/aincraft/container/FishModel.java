package org.aincraft.container;

public class FishModel {
    private final String name;
    private final String description;

    public FishModel(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}