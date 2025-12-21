package org.aincraft.container;

import org.aincraft.processor.FishMaterial;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

public class FishModel {
    private final String name;
    private final String description;
    private final int modelNumber;
    private final Material material;
    private final FishType type;
    private final List<FishMaterial> materials;
    private final boolean canBeGutted;

    public FishModel(String name, String description, int modelNumber) {
        this(name, description, modelNumber, null, FishType.FOOD, Collections.emptyList(), true);
    }

    public FishModel(String name, String description, int modelNumber, Material material) {
        this(name, description, modelNumber, material, FishType.FOOD, Collections.emptyList(), true);
    }

    public FishModel(String name, String description, int modelNumber, Material material, FishType type) {
        this(name, description, modelNumber, material, type, Collections.emptyList(), true);
    }

    public FishModel(String name, String description, int modelNumber, Material material, FishType type, List<FishMaterial> materials) {
        this(name, description, modelNumber, material, type, materials, true);
    }

    public FishModel(String name, String description, int modelNumber, Material material, FishType type, List<FishMaterial> materials, boolean canBeGutted) {
        this.name = name;
        this.description = description;
        this.modelNumber = modelNumber;
        this.material = material;
        this.type = type != null ? type : FishType.FOOD;
        this.materials = materials != null ? materials : Collections.emptyList();
        this.canBeGutted = canBeGutted;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getModelNumber() { return modelNumber; }

    public Material getMaterial() { return material; }

    public FishType getType() { return type; }

    public List<FishMaterial> getMaterials() { return materials; }

    public boolean canBeGutted() { return canBeGutted; }
}