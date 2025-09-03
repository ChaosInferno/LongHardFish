package org.aincraft.listener;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.aincraft.calculator.FishCalculator;
import org.aincraft.container.FishDistribution;
import org.aincraft.container.FishRarity;
import org.aincraft.list.FishCreator;
import org.aincraft.list.FishFilter;
import org.aincraft.list.FishPercentCalculator;
import org.aincraft.provider.FishEnvironmentProvider;
import org.aincraft.provider.FishModelProvider;
import org.aincraft.provider.FishRarityProvider;
import org.aincraft.service.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FishCatchListener implements Listener {

    private final JavaPlugin plugin;
    private final StatsService stats;

    private final FishEnvironmentProvider environmentProvider;
    private final FishRarityProvider rarityProvider;
    private final FishCreator fishCreator;
    private final FishFilter filter;
    private static double clamp01(double v) {
        return (v < 0.0) ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    public FishCatchListener(JavaPlugin plugin,
                             FishEnvironmentProvider environmentProvider,
                             FishRarityProvider rarityProvider,
                             FishModelProvider modelProvider,
                             StatsService stats) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.stats = Objects.requireNonNull(stats, "StatsService is null");
        this.environmentProvider = environmentProvider;
        this.rarityProvider = rarityProvider;
        this.fishCreator = new FishCreator(plugin, modelProvider.parseFishModelObjects());
        this.filter = new FishFilter();
    }

    private static ItemStack findUsedRod(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        if (main != null && main.getType() == org.bukkit.Material.FISHING_ROD) return main;
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off != null && off.getType() == org.bukkit.Material.FISHING_ROD) return off;
        return null;
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

    private static final Set<UUID> processingHooks = ConcurrentHashMap.newKeySet();

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        FishHook hook = (FishHook) event.getHook();
        UUID hookId = hook.getUniqueId();
        Player player = event.getPlayer();
        Location hookLocation = hook.getLocation();
        if (!processingHooks.add(hookId)) return;           // already handled
        Bukkit.getScheduler().runTask(plugin, () -> processingHooks.remove(hookId));

        // Build rarity map
        Map<NamespacedKey, FishDistribution> raw = rarityProvider.parseFishDistributorObjects();
        Map<NamespacedKey, FishRarity> rarityMap = new HashMap<>();
        for (var e : raw.entrySet()) rarityMap.put(e.getKey(), e.getValue().getRarity());

        // ✅ Use the existing field 'filter' (DO NOT new FishFilter())
        Map<NamespacedKey, Double> validFish =
                filter.getValidFish(player, hookLocation, hook, environmentProvider, rarityMap);

        ItemStack usedRod = findUsedRod(player);
        String rodBaitId = null;
        if (usedRod != null) {
            rodBaitId = org.aincraft.items.BaitKeys.getRodBait(plugin, usedRod);
            if (rodBaitId != null) rodBaitId = rodBaitId.toLowerCase(Locale.ENGLISH);
        }

        Map<NamespacedKey, org.aincraft.container.FishEnvironment> envMap =
                environmentProvider.parseFishEnvironmentObjects();

        Iterator<Map.Entry<NamespacedKey, Double>> it = validFish.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<NamespacedKey, Double> e = it.next();
            NamespacedKey fishKey = e.getKey();
            double weight = e.getValue();
            if (weight <= 0) { it.remove(); continue; }

            org.aincraft.container.FishEnvironment env = envMap.get(fishKey);
            if (env == null) continue;

            Map<String, Double> baitMap = env.getEnvironmentBaits(); // id -> bonus
            if (baitMap == null || baitMap.isEmpty()) {
                // no bait table defined -> nothing to do
                continue;
            }

            // Choose which bait key applies:
            // 1) exact match on attached bait
            // 2) fallback to 'none' if present (allows catch without (or despite wrong) bait)
            Double bonus = null;
            if (rodBaitId != null && baitMap.containsKey(rodBaitId)) {
                bonus = baitMap.get(rodBaitId);
            } else if (baitMap.containsKey("none")) {
                bonus = baitMap.get("none");
            } else {
                // bait table exists, no matching bait and no 'none' -> block this fish
                it.remove();
                continue;
            }

            // Apply bonus (multiplicative)
            double b = (bonus == null ? 0.0 : bonus.doubleValue());
            weight = weight * (1.0 + b);
            if (weight <= 0.0) { it.remove(); continue; }
            e.setValue(weight);
        }

        Map<NamespacedKey, Double> validFishPercent = FishPercentCalculator.calculatePercentages(validFish);
        if (validFishPercent.isEmpty()) {
            player.sendMessage("No fish are available to catch right now.");
            return;
        }

        // Weighted choice
        FishCalculator calc = new FishCalculator(validFishPercent);
        NamespacedKey chosenFishKey = calc.getRandomFish();
        if (chosenFishKey == null) {
            player.sendMessage("Something went wrong while picking a fish.");
            return;
        }

        // Create custom item
        ItemStack customFish = fishCreator.createFishItem(
                chosenFishKey,
                rarityMap.get(chosenFishKey));
        if (customFish == null) {
            player.sendMessage("Failed to create custom fish item.");
            return;
        }

        String displayNameText = null;

        Component nameComp = customFish.getData(DataComponentTypes.ITEM_NAME);
        if (nameComp != null) {
            displayNameText = PlainTextComponentSerializer.plainText().serialize(nameComp);
        }

        if (displayNameText == null) {
            ItemMeta meta2 = customFish.getItemMeta();
            if (meta2 != null) {
                Component comp = meta2.displayName();
                if (comp != null) {
                    displayNameText = PlainTextComponentSerializer.plainText().serialize(comp);
                } else if (meta2.hasDisplayName()) { // legacy
                    displayNameText = meta2.getDisplayName();
                }
            }
        }

        if (displayNameText == null) {
            String pretty = chosenFishKey.getKey().replace('_',' ');
            displayNameText = Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1);
        }

        // 🧮 Record the catch in the DB (this increments caught_count)
        stats.recordCatchAsync(player.getUniqueId(), chosenFishKey.toString(), displayNameText);

        // Mark other eligible fish as "seen" (not the one we caught)
        for (NamespacedKey key : validFish.keySet()) {
                if (!key.equals(chosenFishKey)) {
                       stats.markDropSeenAsync(player.getUniqueId(), key.toString(), null);
                    }
            }

        // Swap the caught entity’s stack
        if (event.getCaught() instanceof Item caughtItem) {
            caughtItem.setItemStack(customFish);
        } else {
            player.sendMessage("Caught entity is not an item.");
        }
    }
}
