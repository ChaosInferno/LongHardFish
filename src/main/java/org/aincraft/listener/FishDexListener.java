package org.aincraft.listener;

import org.aincraft.config.FishConfig;
import org.aincraft.container.FishModel;
import org.aincraft.gui.FishDexFishSelector;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class FishDexListener extends JavaPlugin {
    private FishDexFishSelector selector;

    @Override
    public void onEnable() {
        FishConfig fishConfig = new FishConfig("fish.yml", this);
    }
}
