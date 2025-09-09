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
import org.aincraft.rods.RodDefinition;
import org.aincraft.rods.RodProvider;
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
import org.aincraft.container.FishModel;

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
    private final RodProvider rodProvider;
    private final Map<NamespacedKey, FishModel> modelMap;

    public FishCatchListener(JavaPlugin plugin,
                             FishEnvironmentProvider environmentProvider,
                             FishRarityProvider rarityProvider,
                             FishModelProvider modelProvider,
                             StatsService stats,
                             RodProvider rodProvider) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.stats = Objects.requireNonNull(stats, "StatsService is null");
        this.environmentProvider = environmentProvider;
        this.rarityProvider = rarityProvider;
        this.fishCreator = new FishCreator(plugin, modelProvider.parseFishModelObjects());
        this.filter = new FishFilter();
        this.rodProvider = rodProvider;
        this.modelMap = modelProvider.parseFishModelObjects();
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

        int minLure = 10, maxLure = 30, minWait = 20, maxWait = 60;
        double speedSum = 0.0; // additive from time+moon+biome
        try {
            Player p = event.getPlayer();
            ItemStack rodStack = findUsedRod(p);
            String rodId = org.aincraft.rods.RodUtil.getRodId(plugin, rodStack);
            RodDefinition rod = (rodId != null) ? this.rodProvider.get(rodId) : null;

            if (rod != null) {
                var time  = org.aincraft.provider.MinecraftTimeParser.getCurrentTimeCycle();
                var world = p.getWorld();
                var biome = p.getLocation().getBlock().getBiome();
                int moonIndex = (int) (world.getFullTime() / 24000) % 8;
                var moon = org.aincraft.container.FishMoonCycle.values()[moonIndex];

                RodDefinition.DimBonus tb = rod.timeBonus().get(time);
                RodDefinition.DimBonus mb = rod.moonBonus().get(moon);
                RodDefinition.DimBonus bb = rod.biomeBonus().get(biome);
                if (tb != null) speedSum += tb.speed();
                if (mb != null) speedSum += mb.speed();
                if (bb != null) speedSum += bb.speed();
            }
        } catch (Throwable ignored) {}

        // Convert additive speedSum into a scalar (positive => faster, negative => slower)
        double scalar = Math.max(0.05, 1.0 - speedSum); // clamp >= 5% of base time
        hook.setMinLureTime((int)Math.round(minLure * scalar));
        hook.setMaxLureTime((int)Math.round(maxLure * scalar));
        hook.setMinWaitTime((int)Math.round(minWait * scalar));
        hook.setMaxWaitTime((int)Math.round(maxWait * scalar));
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
        boolean loggedSample = false;

        // Build rarity map
        Map<NamespacedKey, FishDistribution> raw = rarityProvider.parseFishDistributorObjects();
        Map<NamespacedKey, FishRarity> rarityMap = new HashMap<>();
        for (var e : raw.entrySet()) rarityMap.put(e.getKey(), e.getValue().getRarity());

        // ✅ Use the existing field 'filter' (DO NOT new FishFilter())
        Map<NamespacedKey, Double> validFish =
                filter.getValidFish(player, hookLocation, hook, environmentProvider, rarityMap);

        plugin.getLogger().info("[SANITY] validFish size=" + validFish.size() +
                " keys=" + validFish.keySet().stream().map(NamespacedKey::getKey).toList());

        ItemStack rodStack = findUsedRod(player);
        String currentRodId = org.aincraft.rods.RodUtil.getRodId(plugin, rodStack);
        RodDefinition currentRod = (currentRodId != null) ? rodProvider.get(currentRodId) : null;
        int rodTier = (currentRod != null) ? Math.max(1, currentRod.tier()) : 1;

        // Get fish tiers once and remove those above the rod’s tier
        Map<NamespacedKey, Integer> fishTierMap = environmentProvider.parseFishTierMap();
        for (Iterator<Map.Entry<NamespacedKey, Double>> itTier = validFish.entrySet().iterator(); itTier.hasNext();) {
            Map.Entry<NamespacedKey, Double> en = itTier.next();
            Integer fishTier = fishTierMap.get(en.getKey());
            int t = (fishTier != null) ? fishTier.intValue() : 1; // default tier-1 if missing
            if (t > rodTier) {
                itTier.remove();
            }
        }
        if (validFish.isEmpty()) {
            player.sendMessage("Your rod can’t catch any fish here (tier too low).");
            return;
        }

        String rodBaitId = null;
        if (rodStack != null) {
            rodBaitId = org.aincraft.items.BaitKeys.getRodBait(plugin, rodStack);
            if (rodBaitId != null) rodBaitId = rodBaitId.toLowerCase(Locale.ENGLISH);
        }
        plugin.getLogger().info("[SANITY] rodBaitId = " + rodBaitId);

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
            if (!loggedSample) {
                plugin.getLogger().info("[SANITY] bait adj: fish=" + fishKey.getKey()
                        + " map=" + baitMap
                        + " rod=" + rodBaitId
                        + " bonus=" + b);
                loggedSample = true;
            }
        }

        // ==== Rod bonuses =========================================================
        RodDefinition rod = currentRod;

        // Declare BEFORE the block so we can use it after for the drop.
        boolean isDouble = false;

        if (rod != null) {
            // current conditions
            var world = hookLocation.getWorld();
            var biome = hookLocation.getBlock().getBiome();
            int moonIndex = (int) (world.getFullTime() / 24000) % 8;
            var moon = org.aincraft.container.FishMoonCycle.values()[moonIndex];
            var time = org.aincraft.provider.MinecraftTimeParser.getCurrentTimeCycle();

            RodDefinition.Weather wx;
            if (world.isThundering())      wx = RodDefinition.Weather.STORM;
            else if (world.hasStorm())     wx = RodDefinition.Weather.RAIN;
            else                           wx = RodDefinition.Weather.CLEAR;

            // global multipliers (same for all fish on this cast)
            double globalMult = 1.0;
            Double b = rod.biomeBonusFlat().get(biome);  if (b != null) globalMult *= (1.0 + b);
            Double t = rod.timeBonusFlat().get(time);    if (t != null) globalMult *= (1.0 + t);
            Double m = rod.moonBonusFlat().get(moon);    if (m != null) globalMult *= (1.0 + m);
            Double w = rod.weatherBonus().get(wx);       if (w != null) globalMult *= (1.0 + w);

            // nested bonuses for this exact context
            RodDefinition.DimBonus tBonus = rod.timeBonus().get(time);
            RodDefinition.DimBonus mBonus = rod.moonBonus().get(moon);
            RodDefinition.DimBonus bBonus = rod.biomeBonus().get(biome);

            // Double-catch chance (union of independent chances)
            double pTime  = (tBonus != null) ? Math.max(0.0, tBonus.doubleChance()) : 0.0;
            double pMoon  = (mBonus != null) ? Math.max(0.0, mBonus.doubleChance()) : 0.0;
            double pBiome = (bBonus != null) ? Math.max(0.0, bBonus.doubleChance()) : 0.0;
            double pDouble = 1.0 - (1.0 - pTime) * (1.0 - pMoon) * (1.0 - pBiome);
            pDouble = Math.max(0.0, Math.min(1.0, pDouble));
            isDouble = (pDouble > 0.0) && (Math.random() < pDouble);

            // per-fish rarity multiplier
            for (Iterator<Map.Entry<NamespacedKey, Double>> it2 = validFish.entrySet().iterator(); it2.hasNext(); ) {
                Map.Entry<NamespacedKey, Double> e2 = it2.next();
                NamespacedKey fishKey2 = e2.getKey();
                Double weight2 = e2.getValue();
                if (weight2 == null || weight2 <= 0d) { it2.remove(); continue; }

                org.aincraft.container.FishRarity r = rarityMap.get(fishKey2);
                double mult = globalMult;

                // legacy global rarity
                if (r != null) {
                    Double rr = rod.rarityBonus().get(r);
                    if (rr != null) mult *= (1.0 + rr);
                }
                // nested per-time rarity
                if (tBonus != null && r != null) {
                    Double tr = tBonus.rarityBonus().get(r);
                    if (tr != null) mult *= (1.0 + tr);
                }
                // nested per-moon rarity
                if (mBonus != null && r != null) {
                    Double mr = mBonus.rarityBonus().get(r);
                    if (mr != null) mult *= (1.0 + mr);
                }
                // nested per-biome rarity
                if (bBonus != null && r != null) {
                    Double br = bBonus.rarityBonus().get(r);
                    if (br != null) mult *= (1.0 + br);
                }

                double newWeight = weight2 * mult;
                if (newWeight <= 0) { it2.remove(); continue; }
                e2.setValue(newWeight);
            }
        }

        Map<NamespacedKey, Double> validFishPercent = FishPercentCalculator.calculatePercentages(validFish);
        if (validFishPercent.isEmpty()) {
            player.sendMessage("No fish are available to catch right now.");
            return;
        }

        try {
            org.bukkit.inventory.ItemStack off = player.getInventory().getItemInOffHand();
            boolean showDetails = false;
            if (off != null && off.hasItemMeta()) {
                var pdc = off.getItemMeta().getPersistentDataContainer();
                var omegaKey = new org.bukkit.NamespacedKey(plugin, org.aincraft.ingame_items.OmegaFishFinderItem.ID);
                Byte tag = pdc.get(omegaKey, org.bukkit.persistence.PersistentDataType.BYTE);
                showDetails = (tag != null && tag == (byte)1);
            }

            if (showDetails) {
                // Sort by descending percent
                java.util.List<Map.Entry<NamespacedKey, Double>> list = new java.util.ArrayList<>(validFishPercent.entrySet());
                list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

                StringBuilder sb = new StringBuilder("§bCatchable now:§7 ");
                boolean first = true;
                for (var en : list) {
                    NamespacedKey id = en.getKey();
                    double pct = en.getValue() * 100.0;

                    // Name: prefer model name if present
                    String name = null;
                    var fm = modelMap.get(id);
                    if (fm != null && fm.getName() != null && !fm.getName().isEmpty()) {
                        name = fm.getName();
                    } else {
                        name = id.getKey().replace('_', ' ');
                        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                    }

                    if (!first) sb.append("§8, ");
                    sb.append("§f").append(name).append(" §7(")
                            .append(String.format(java.util.Locale.ENGLISH, "%.2f%%", pct))
                            .append(")");
                    first = false;
                }
                player.sendMessage(sb.toString());
            }
        } catch (Throwable ignored) {}

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
            if (isDouble) {
                caughtItem.getWorld().dropItemNaturally(caughtItem.getLocation(), customFish.clone());
            }
        } else {
            player.sendMessage("Caught entity is not an item.");
        }
    }
}
