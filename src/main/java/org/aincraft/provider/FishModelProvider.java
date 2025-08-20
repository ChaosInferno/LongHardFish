package org.aincraft.provider;

import org.aincraft.config.FishConfig;
import org.aincraft.container.FishModel;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

            if (modelNumber <= 0) {
                plugin.getLogger().warning("Invalid or missing 'model' for fish '" + fishKey + "'. Skipping.");
                continue;
            }

            if (name == null || description == null) {
                plugin.getLogger().warning("Missing name or description for fish: " + fishKey);
                continue;
            }

            NamespacedKey key = new NamespacedKey(plugin, fishKey);
            modelMap.put(key, new FishModel(name, description, modelNumber));
        }

        return modelMap;
    }

    public Plugin getPlugin() {
        return this.plugin;
    }
}
