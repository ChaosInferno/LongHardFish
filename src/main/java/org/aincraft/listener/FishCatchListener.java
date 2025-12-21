package org.aincraft.listener;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.calculator.FishCalculator;
import org.aincraft.container.FishDistribution;
import org.aincraft.container.FishRarity;
import org.aincraft.i18n.MessageKey;
import org.aincraft.i18n.MessageService;
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
import org.bukkit.enchantments.Enchantment;
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
    private final MessageService messageService;

    private final FishEnvironmentProvider environmentProvider;
    private final FishRarityProvider rarityProvider;
    private final FishCreator fishCreator;
    private final FishFilter filter;
    private static double clamp01(double v) {
        return (v < 0.0) ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
    private final RodProvider rodProvider;
    private final Map<NamespacedKey, FishModel> modelMap;

    // Cached provider data (avoid re-parsing on every fish catch)
    private volatile Map<NamespacedKey, FishDistribution> cachedDistributions;
    private volatile Map<NamespacedKey, FishRarity> cachedRarityMap;
    private volatile Map<NamespacedKey, Integer> cachedTierMap;
    private volatile Map<NamespacedKey, org.aincraft.container.FishEnvironment> cachedEnvironments;

    public FishCatchListener(JavaPlugin plugin,
                             FishEnvironmentProvider environmentProvider,
                             FishRarityProvider rarityProvider,
                             FishModelProvider modelProvider,
                             StatsService stats,
                             RodProvider rodProvider,
                             MessageService messageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.stats = Objects.requireNonNull(stats, "StatsService is null");
        this.messageService = Objects.requireNonNull(messageService, "MessageService is null");
        this.environmentProvider = environmentProvider;
        this.rarityProvider = rarityProvider;
        this.fishCreator = new FishCreator(plugin, modelProvider.parseFishModelObjects());
        this.filter = new FishFilter();
        this.rodProvider = rodProvider;
        this.modelMap = modelProvider.parseFishModelObjects();

        // Initialize caches
        refreshCaches();
    }

    /**
     * Refreshes all cached provider data. Call this when fish configs are reloaded.
     */
    public void refreshCaches() {
        // Use single-pass parsing to avoid double iteration
        org.aincraft.provider.FishRarityProvider.ParseResult result = rarityProvider.parseAll();

        this.cachedDistributions = result.distributions;
        this.cachedRarityMap = result.rarityMap;
        this.cachedTierMap = environmentProvider.parseFishTierMap();
        this.cachedEnvironments = environmentProvider.parseFishEnvironmentObjects();
    }

    /**
     * Invalidates caches, forcing re-parse on next fish catch.
     */
    public void invalidateCaches() {
        this.cachedDistributions = null;
        this.cachedRarityMap = null;
        this.cachedTierMap = null;
        this.cachedEnvironments = null;
    }

    // Lazy cache getters (thread-safe double-checked locking pattern)
    private Map<NamespacedKey, FishRarity> getRarityMap() {
        Map<NamespacedKey, FishRarity> map = cachedRarityMap;
        if (map == null) {
            synchronized (this) {
                map = cachedRarityMap;
                if (map == null) {
                    refreshCaches();
                    map = cachedRarityMap;
                }
            }
        }
        return map;
    }

    private Map<NamespacedKey, Integer> getTierMap() {
        Map<NamespacedKey, Integer> map = cachedTierMap;
        if (map == null) {
            synchronized (this) {
                map = cachedTierMap;
                if (map == null) {
                    refreshCaches();
                    map = cachedTierMap;
                }
            }
        }
        return map;
    }

    private Map<NamespacedKey, org.aincraft.container.FishEnvironment> getEnvironmentMap() {
        Map<NamespacedKey, org.aincraft.container.FishEnvironment> map = cachedEnvironments;
        if (map == null) {
            synchronized (this) {
                map = cachedEnvironments;
                if (map == null) {
                    refreshCaches();
                    map = cachedEnvironments;
                }
            }
        }
        return map;
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

        // 1) Start from vanilla/Paper defaults for THIS hook (version-safe)
        final int baseMinLure = hook.getMinLureTime();
        final int baseMaxLure = hook.getMaxLureTime();
        final int baseMinWait = hook.getMinWaitTime();
        final int baseMaxWait = hook.getMaxWaitTime();

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

        // 2) Convert additive to scalar and apply to the *base* values
        double scalar = Math.max(0.05, 1.0 - speedSum); // never faster than 5% of base

        int minLure = (int) Math.max(1, Math.round(baseMinLure * scalar));
        int maxLure = (int) Math.max(minLure, Math.round(baseMaxLure * scalar));

        int minWait = (int) Math.max(1, Math.round(baseMinWait * scalar));
        int maxWait = (int) Math.max(minWait, Math.round(baseMaxWait * scalar));

        hook.setMinLureTime(minLure);
        hook.setMaxLureTime(maxLure);
        hook.setMinWaitTime(minWait);
        hook.setMaxWaitTime(maxWait);

        hook.setApplyLure(true);
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

        // Use cached rarity map and environment map (no re-parsing per event)
        Map<NamespacedKey, FishRarity> rarityMap = getRarityMap();
        Map<NamespacedKey, org.aincraft.container.FishEnvironment> environments = getEnvironmentMap();

        // ✅ Use the existing field 'filter' with cached data (DO NOT new FishFilter())
        Map<NamespacedKey, Double> validFish =
                filter.getValidFish(player, hookLocation, hook, environments, rarityMap);

        ItemStack rodStack = findUsedRod(player);
        String currentRodId = org.aincraft.rods.RodUtil.getRodId(plugin, rodStack);
        RodDefinition currentRod = (currentRodId != null) ? rodProvider.get(currentRodId) : null;
        int rodTier = (currentRod != null) ? Math.max(1, currentRod.tier()) : 1;

        // Get bait ID for filtering
        String rodBaitId = null;
        if (rodStack != null) {
            rodBaitId = org.aincraft.items.BaitKeys.getRodBait(plugin, rodStack);
            if (rodBaitId != null) rodBaitId = rodBaitId.toLowerCase(Locale.ENGLISH);
        }

        // Use cached maps (no re-parsing per event)
        Map<NamespacedKey, Integer> fishTierMap = getTierMap();
        Map<NamespacedKey, org.aincraft.container.FishEnvironment> envMap = getEnvironmentMap();

        // Single pass: apply tier filter + bait filter/bonus together
        for (Iterator<Map.Entry<NamespacedKey, Double>> it = validFish.entrySet().iterator(); it.hasNext();) {
            Map.Entry<NamespacedKey, Double> e = it.next();
            NamespacedKey fishKey = e.getKey();
            double weight = e.getValue();

            // Skip invalid weights
            if (weight <= 0) { it.remove(); continue; }

            // Tier filter: remove fish above rod tier
            Integer fishTier = fishTierMap.get(fishKey);
            int t = (fishTier != null) ? fishTier.intValue() : 1;
            if (t > rodTier) { it.remove(); continue; }

            // Bait filter/bonus
            org.aincraft.container.FishEnvironment env = envMap.get(fishKey);
            if (env != null) {
                Map<String, Double> baitMap = env.getEnvironmentBaits();
                if (baitMap != null && !baitMap.isEmpty()) {
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
            }
        }

        if (validFish.isEmpty()) {
            player.sendMessage(messageService.getMessage(MessageKey.Fishing.CATCH_NO_VALID_FISH));
            return;
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
            player.sendMessage(messageService.getMessage(MessageKey.Fishing.CATCH_NO_FISH_AVAILABLE));
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
                        name = org.aincraft.util.BiomeUtils.prettifyKey(id.getKey());
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
            player.sendMessage(messageService.getMessage(MessageKey.Fishing.CATCH_PICK_ERROR));
            return;
        }

        // Create custom item
        ItemStack customFish = fishCreator.createFishItem(
                chosenFishKey,
                rarityMap.get(chosenFishKey));
        if (customFish == null) {
            player.sendMessage(messageService.getMessage(MessageKey.Fishing.CATCH_CREATE_ERROR));
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
            displayNameText = org.aincraft.util.BiomeUtils.prettifyKey(chosenFishKey.getKey());
        }
        final String finalDisplayName = displayNameText;

        // Get the fish rarity for sound priority determination
        FishRarity caughtFishRarity = rarityMap.get(chosenFishKey);

        // Collect fish keys to mark as "seen" (all except the caught one)
        java.util.List<String> seenFishKeys = new java.util.ArrayList<>();
        for (NamespacedKey key : validFish.keySet()) {
            if (!key.equals(chosenFishKey)) {
                seenFishKeys.add(key.toString());
            }
        }

        // Record the catch with callback to handle sound effects
        // Then mark other fish as seen to avoid database lock conflicts
        stats.recordCatchAsyncWithCallback(player.getUniqueId(), chosenFishKey.toString(), displayNameText, isFirstCatch -> {
            // Mark other eligible fish as "seen" after recording the catch
            // Sequential execution prevents SQLITE_BUSY errors
            if (!seenFishKeys.isEmpty()) {
                stats.markMultipleDropSeenAsyncWithCallback(player.getUniqueId(), seenFishKeys, newlySeenCount -> {
                    // Combine messages: play caught sound and display message with seen count appended if applicable
                    playFishDiscoverySoundWithSeenCount(player, isFirstCatch, caughtFishRarity, finalDisplayName, newlySeenCount);
                });
            } else {
                // No other fish to mark as seen, just play the caught sound
                playFishDiscoverySoundWithSeenCount(player, isFirstCatch, caughtFishRarity, finalDisplayName, 0);
            }
        });

        // Swap the caught entity's stack
        if (event.getCaught() instanceof Item caughtItem) {
            caughtItem.setItemStack(customFish);
            if (isDouble) {
                caughtItem.getWorld().dropItemNaturally(caughtItem.getLocation(), customFish.clone());
            }
        } else {
            player.sendMessage(messageService.getMessage(MessageKey.Fishing.CATCH_ENTITY_ERROR));
        }

        // Manually apply durability damage to fishing rod
        // Required because replacing caught item prevents vanilla durability from applying
        applyRodDurabilityDamage(player);
    }

    /**
     * Applies durability damage to the player's fishing rod.
     * Vanilla durability doesn't apply when we replace the caught item, so we handle it manually.
     * Takes Unbreaking enchantment into account.
     *
     * @param player The player whose rod should take damage
     */
    private void applyRodDurabilityDamage(Player player) {
        ItemStack rod = findUsedRod(player);
        if (rod == null || rod.getType() != org.bukkit.Material.FISHING_ROD) return;

        ItemMeta meta = rod.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.Damageable damageable)) return;

        // Check Unbreaking enchantment level
        int unbreakingLevel = rod.getEnchantmentLevel(Enchantment.UNBREAKING);

        // Unbreaking has a chance to prevent durability loss
        // Formula: (100 / (level + 1))% chance to take damage
        if (unbreakingLevel > 0) {
            double damageChance = 1.0 / (unbreakingLevel + 1.0);
            if (Math.random() >= damageChance) {
                return; // Unbreaking prevented damage
            }
        }

        // Apply 1 point of damage
        int currentDamage = damageable.getDamage();
        int maxDurability = rod.getType().getMaxDurability();

        if (currentDamage + 1 >= maxDurability) {
            // Rod breaks
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            rod.setAmount(0); // Remove the item
        } else {
            // Apply damage
            damageable.setDamage(currentDamage + 1);
            rod.setItemMeta(meta);
        }
    }

    /**
     * Plays the appropriate fish discovery sound based on priority and sends accompanying message.
     * Combines caught and seen messages when both conditions apply.
     * Priority system (higher number = overrides lower):
     * 1. catch.new-seen - When fish is marked as seen (not caught) for the first time
     * 2. catch.new-caught - When any fish is caught for the first time
     * 3. catch.new-caught-rare - When a RARE fish is caught for the first time
     * 4. catch.new-caught-legendary - When a LEGENDARY fish is caught for the first time
     *
     * @param player The player to play sound for
     * @param isFirstCatch True if this is the first catch of the caught fish
     * @param rarity The rarity of the caught fish (can be null if not a first catch)
     * @param newlySeenCount Count of newly seen fish (0 if none were seen)
     */
    private void playFishDiscoverySoundWithSeenCount(Player player, boolean isFirstCatch, @javax.annotation.Nullable FishRarity rarity, String fishName, int newlySeenCount) {
        String soundKey = null;
        Component message = null;

        // Build hover text for fish info
        String rarityRaw = (rarity != null) ? rarity.name() : "UNKNOWN";
        String rarityName = rarityRaw.charAt(0) + rarityRaw.substring(1).toLowerCase();
        NamedTextColor rarityColor = switch (rarity) {
            case LEGENDARY -> NamedTextColor.GOLD;
            case RARE -> NamedTextColor.AQUA;
            case UNCOMMON -> NamedTextColor.GREEN;
            case EVENT -> NamedTextColor.LIGHT_PURPLE;
            case null, default -> NamedTextColor.GRAY;
        };

        // Format current date/time with timezone
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        String dateStr = now.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z"));

        Component hoverText = Component.text(fishName, NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Component.text(rarityName, rarityColor))
                .append(Component.newline())
                .append(Component.text("Caught on " + dateStr, NamedTextColor.GRAY));

        // Hover event for fish info
        var hover = net.kyori.adventure.text.event.HoverEvent.showText(hoverText);

        // Determine the primary message and sound based on whether this is a first catch
        if (isFirstCatch) {
            // Playing a catch sound - apply priority based on rarity
            if (rarity == FishRarity.LEGENDARY) {
                soundKey = "longhardfish:catch.new-caught-legendary";
                message = Component.text("Unknown species discovered", NamedTextColor.GOLD).hoverEvent(hover)
                        .append(Component.text(", registered to index.", NamedTextColor.GREEN));
            } else if (rarity == FishRarity.RARE) {
                soundKey = "longhardfish:catch.new-caught-rare";
                message = Component.text("Rare fish discovered", NamedTextColor.GOLD).hoverEvent(hover)
                        .append(Component.text(", registered to index.", NamedTextColor.GREEN));
            } else {
                // For COMMON, UNCOMMON, EVENT: play generic first-catch sound
                soundKey = "longhardfish:catch.new-caught";
                message = Component.text("New fish discovered", NamedTextColor.GOLD).hoverEvent(hover)
                        .append(Component.text(", registered to index.", NamedTextColor.GREEN));
            }

            // Append seen message if other fish were newly seen (on new line)
            if (newlySeenCount == 1) {
                message = message.append(Component.newline())
                        .append(Component.text("New fish detected in water, updating index.", NamedTextColor.GREEN));
            } else if (newlySeenCount > 1) {
                message = message.append(Component.newline())
                        .append(Component.text("Multiple new fish detected in water, updating index.", NamedTextColor.GREEN));
            }
        } else if (newlySeenCount == 1) {
            // Not a first catch, but one fish was newly seen
            soundKey = "longhardfish:catch.new-seen";
            message = Component.text("New fish detected in water, updating index.", NamedTextColor.GREEN);
        } else if (newlySeenCount > 1) {
            // Not a first catch, but multiple fish were newly seen
            soundKey = "longhardfish:catch.new-seen";
            message = Component.text("Multiple new fish detected in water, updating index.", NamedTextColor.GREEN);
        }

        // Play sound and display message
        if (soundKey != null) {
            try {
                player.playSound(Sound.sound(Key.key(soundKey), Sound.Source.PLAYER, 1.0f, 1.0f));
                if (message != null) {
                    player.sendMessage(message);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to play fish discovery sound '" + soundKey + "': " + e.getMessage());
            }
        }
    }
}
