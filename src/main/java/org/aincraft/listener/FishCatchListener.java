package org.aincraft.listener;

import org.aincraft.container.FishDistrubution;
import org.aincraft.container.FishRarity;
import org.aincraft.list.FishFilter;
import org.aincraft.list.FishPercentCalculator;
import org.aincraft.provider.FishEnvironmentProvider;
import org.aincraft.provider.FishRarityProvider;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.HashMap;
import java.util.Map;

public class FishCatchListener implements Listener {

    private final FishEnvironmentProvider environmentProvider;
    private final FishRarityProvider rarityProvider;

    public FishCatchListener(FishEnvironmentProvider environmentProvider, FishRarityProvider rarityProvider) {
        this.environmentProvider = environmentProvider;
        this.rarityProvider = rarityProvider;
    }

    @EventHandler
    public void debugFishSpeed(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) return;

        FishHook hook = (FishHook) event.getHook();
        hook.setMinLureTime(10);
        hook.setMaxLureTime(30);
        hook.setMinWaitTime(20);
        hook.setMaxWaitTime(60);

        hook.setApplyLure(false);
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();
        Location hookLocation = event.getHook().getLocation();
        FishHook hook = (FishHook) event.getHook();
        FishFilter fishFilter = new FishFilter();
        FishPercentCalculator fishPercent = new FishPercentCalculator();

        Map<NamespacedKey, FishDistrubution> rawRarityMap = rarityProvider.parseFishDistributorObjects();
        Map<NamespacedKey, FishRarity> rarityMap = new HashMap<>();
        for (Map.Entry<NamespacedKey, FishDistrubution> entry : rawRarityMap.entrySet()) {
            rarityMap.put(entry.getKey(), entry.getValue().getRarity());
        }

        Map<NamespacedKey, Double> validFish = fishFilter.getValidFish(player, hookLocation, hook, environmentProvider, rarityMap);
        Map<NamespacedKey, Double> validFishPercent = FishPercentCalculator.calculatePercentages(validFish);


        if (validFishPercent.isEmpty()) {
                player.sendMessage("No fish are available to catch right now.");
        } else {
                player.sendMessage("Available fish and scores:");
                validFishPercent.forEach((key, score) ->
                        player.sendMessage(" - " + key.getKey() + " | Chance: " + String.format("%.2f", score) + "%")
                );
        }
    }
}

