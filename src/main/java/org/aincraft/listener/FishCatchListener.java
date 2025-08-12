package org.aincraft.listener;

import org.aincraft.calculator.FishCalculator;
import org.aincraft.container.FishDistrubution;
import org.aincraft.container.FishRarity;
import org.aincraft.list.FishCreator;
import org.aincraft.list.FishFilter;
import org.aincraft.list.FishPercentCalculator;
import org.aincraft.provider.FishEnvironmentProvider;
import org.aincraft.provider.FishModelProvider;
import org.aincraft.provider.FishRarityProvider;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class FishCatchListener implements Listener {

    private final FishEnvironmentProvider environmentProvider;
    private final FishRarityProvider rarityProvider;
    private final FishCreator fishCreator;

    public FishCatchListener(FishEnvironmentProvider environmentProvider,
                             FishRarityProvider rarityProvider,
                             FishModelProvider modelProvider) {
        this.environmentProvider = environmentProvider;
        this.rarityProvider = rarityProvider;
        this.fishCreator = new FishCreator(modelProvider.parseFishModelObjects());
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
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();
        FishHook hook = (FishHook) event.getHook();
        Location hookLocation = hook.getLocation();

        // Get rarity data
        Map<NamespacedKey, FishDistrubution> rawRarityMap = rarityProvider.parseFishDistributorObjects();
        Map<NamespacedKey, FishRarity> rarityMap = new HashMap<>();
        for (Map.Entry<NamespacedKey, FishDistrubution> entry : rawRarityMap.entrySet()) {
            rarityMap.put(entry.getKey(), entry.getValue().getRarity());
        }

        // Get valid fish from filter
        FishFilter fishFilter = new FishFilter();
        Map<NamespacedKey, Double> validFish = fishFilter.getValidFish(player, hookLocation, hook, environmentProvider, rarityMap);
        Map<NamespacedKey, Double> validFishPercent = FishPercentCalculator.calculatePercentages(validFish);

        if (validFishPercent.isEmpty()) {
            player.sendMessage("No fish are available to catch right now.");
            return;
        }

        // Debug list of catchable fish
        player.sendMessage("Available fish and scores:");
        validFishPercent.forEach((key, score) ->
                player.sendMessage(" - " + key.getKey() + " | Chance: " + String.format("%.2f", score) + "%")
        );

        // Choose fish using weighted chance
        FishCalculator fishCalculator = new FishCalculator(validFishPercent);
        NamespacedKey chosenFishKey = fishCalculator.getRandomFish();
        if (chosenFishKey == null) {
            player.sendMessage("Something went wrong while picking a fish.");
            return;
        }

        // Create the custom fish item
        ItemStack customFish = fishCreator.createFishItem(chosenFishKey);
        if (customFish != null) {
            if (customFish.hasItemMeta() && customFish.getItemMeta().hasCustomModelData()) {
                int modelData = customFish.getItemMeta().getCustomModelData();
            }

            // Replace the item stack inside the caught entity
            if (event.getCaught() instanceof Item caughtItem) {
                caughtItem.setItemStack(customFish);
            } else {
                player.sendMessage("Caught entity is not an item.");
            }
        } else {
            player.sendMessage("Failed to create custom fish item.");
        }
    }
}
