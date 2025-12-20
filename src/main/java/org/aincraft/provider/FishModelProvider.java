package org.aincraft.provider;

import org.aincraft.config.FishConfig;
import org.aincraft.container.FishModel;
import org.aincraft.processor.FishMaterial;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class FishModelProvider {
    private final FishConfig holder;
    private final Plugin plugin;

    public FishModelProvider(FishConfig holder, Plugin plugin) {
        this.holder = holder;
        this.plugin = plugin;
    }

    public Map<NamespacedKey, FishModel> parseFishModelObjects() {
        FileConfiguration config = holder.getConfig();
        Set<String> fishKeys = config.getKeys(false);
        Map<NamespacedKey, FishModel> modelMap = new HashMap<>();

        for (String fishKey : fishKeys) {
            ConfigurationSection section = config.getConfigurationSection(fishKey);
            if (section == null) {
                plugin.getLogger().warning("Missing configuration section for fish: " + fishKey);
                continue;
            }

            String name = section.getString("name");
            String description = section.getString("description");
            int modelNumber = section.getInt("model", -1);
            String materialName = section.getString("material");
            String typeName = section.getString("type");
            org.bukkit.Material material = null;
            org.aincraft.container.FishType type = org.aincraft.container.FishType.FOOD; // Default

            // Parse material if specified
            if (materialName != null && !materialName.isEmpty()) {
                try {
                    material = org.bukkit.Material.valueOf(materialName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material '" + materialName + "' for fish '" + fishKey + "'. Ignoring material.");
                }
            }

            // Parse type if specified
            if (typeName != null && !typeName.isEmpty()) {
                try {
                    type = org.aincraft.container.FishType.valueOf(typeName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid type '" + typeName + "' for fish '" + fishKey + "'. Defaulting to FOOD.");
                }
            }

            // If material is specified, model is optional
            if (material == null && modelNumber <= 0) {
                plugin.getLogger().warning("Invalid or missing 'model' for fish '" + fishKey + "'. Skipping.");
                continue;
            }

            if (name == null || description == null) {
                plugin.getLogger().warning("Missing name or description for fish: " + fishKey);
                continue;
            }

            // Parse materials section
            List<FishMaterial> materials = parseFishMaterials(fishKey, section);

            NamespacedKey key = new NamespacedKey(plugin, fishKey);
            modelMap.put(key, new FishModel(name, description, modelNumber, material, type, materials));
        }

        return modelMap;
    }

    /**
     * Parses the "materials" section of a fish configuration.
     *
     * @param fishKey fish key for logging
     * @param section fish configuration section
     * @return list of FishMaterial objects
     */
    private List<FishMaterial> parseFishMaterials(String fishKey, ConfigurationSection section) {
        List<FishMaterial> materials = new ArrayList<>();
        ConfigurationSection materialsSection = section.getConfigurationSection("materials");

        if (materialsSection == null) {
            return materials; // Empty list if no materials defined
        }

        for (String materialId : materialsSection.getKeys(false)) {
            ConfigurationSection matSection = materialsSection.getConfigurationSection(materialId);
            if (matSection == null) {
                plugin.getLogger().warning("Missing configuration section for material '" + materialId + "' in fish '" + fishKey + "'");
                continue;
            }

            // Parse required fields
            double chance = matSection.getDouble("chance", 0.0);
            String range = matSection.getString("range");
            double expected = matSection.getDouble("expected", 0.0);
            boolean canBeZero = matSection.getBoolean("can_be_zero", false);

            // Validate required fields
            if (range == null || range.isEmpty()) {
                plugin.getLogger().warning("Missing 'range' for material '" + materialId + "' in fish '" + fishKey + "'");
                continue;
            }

            try {
                FishMaterial fishMaterial = new FishMaterial(materialId, chance, range, expected, canBeZero);
                materials.add(fishMaterial);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material configuration for '" + materialId + "' in fish '" + fishKey + "': " + e.getMessage());
            }
        }

        return materials;
    }

    public Plugin getPlugin() {
        return this.plugin;
    }
}
