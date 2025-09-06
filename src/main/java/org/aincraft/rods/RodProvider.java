// org/aincraft/rods/RodProvider.java
package org.aincraft.rods;

import org.aincraft.container.FishMoonCycle;
import org.aincraft.container.FishRarity;
import org.aincraft.container.FishTimeCycle;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.*;

public final class RodProvider {
    private final Plugin plugin;
    private final RodsConfig rodsConfig;

    private Map<String, RodDefinition> cache = Collections.emptyMap();

    public RodProvider(Plugin plugin, RodsConfig rodsConfig) {
        this.plugin = plugin; this.rodsConfig = rodsConfig;
    }

    public Map<String, RodDefinition> parse() {
        FileConfiguration cfg = rodsConfig.getConfig();
        Map<String, RodDefinition> out = new LinkedHashMap<>();

        for (String rawId : cfg.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ENGLISH);
            ConfigurationSection s = cfg.getConfigurationSection(rawId);
            if (s == null) continue;

            String desc = s.getString("description", "");
            int tier = Math.max(1, s.getInt("tier", 1));
            RodDefinition def = new RodDefinition(id, desc, tier);

            ConfigurationSection bonus = s.getConfigurationSection("bonus");
            if (bonus != null) {
                // --- global rarity (flat) ---
                ConfigurationSection rSec = bonus.getConfigurationSection("rarity");
                if (rSec != null) {
                    for (String k : rSec.getKeys(false)) {
                        try {
                            FishRarity r = FishRarity.valueOf(k.toUpperCase(Locale.ENGLISH));
                            def.rarityBonus().put(r, rSec.getDouble(k));
                        } catch (IllegalArgumentException ex) {
                            plugin.getLogger().warning("[Rods] Unknown rarity '" + k + "' in rod '" + id + "'");
                        }
                    }
                }

                // --- TIME: flat or nested ---
                ConfigurationSection tSec = bonus.getConfigurationSection("time");
                if (tSec != null) {
                    for (String tKey : tSec.getKeys(false)) {
                        FishTimeCycle t;
                        try { t = FishTimeCycle.valueOf(tKey.toUpperCase(Locale.ENGLISH)); }
                        catch (IllegalArgumentException ex) { plugin.getLogger().warning("[Rods] Unknown time '" + tKey + "' in rod '" + id + "'"); continue; }

                        if (tSec.isConfigurationSection(tKey)) {
                            // nested
                            ConfigurationSection node = tSec.getConfigurationSection(tKey);
                            RodDefinition.DimBonus tb = def.ensureTime(t);

                            ConfigurationSection rSub = node.getConfigurationSection("rarity");
                            if (rSub != null) {
                                for (String rk : rSub.getKeys(false)) {
                                    try {
                                        FishRarity rar = FishRarity.valueOf(rk.toUpperCase(Locale.ENGLISH));
                                        tb.rarityBonus().put(rar, rSub.getDouble(rk));
                                    } catch (IllegalArgumentException ex) {
                                        plugin.getLogger().warning("[Rods] Unknown rarity '" + rk + "' under time '" + tKey + "' in rod '" + id + "'");
                                    }
                                }
                            }
                            if (node.isDouble("speed") || node.isInt("speed"))   tb.speed(node.getDouble("speed", 0.0));
                            if (node.isDouble("double") || node.isInt("double")) tb.doubleChance(node.getDouble("double", 0.0));
                        } else {
                            // flat number
                            def.timeBonusFlat().put(t, tSec.getDouble(tKey));
                        }
                    }
                }

                // --- MOON: flat or nested ---
                ConfigurationSection mSec = bonus.getConfigurationSection("moon");
                if (mSec != null) {
                    for (String mKey : mSec.getKeys(false)) {
                        FishMoonCycle m;
                        try { m = FishMoonCycle.valueOf(mKey.toUpperCase(Locale.ENGLISH)); }
                        catch (IllegalArgumentException ex) { plugin.getLogger().warning("[Rods] Unknown moon '" + mKey + "' in rod '" + id + "'"); continue; }

                        if (mSec.isConfigurationSection(mKey)) {
                            // nested
                            ConfigurationSection node = mSec.getConfigurationSection(mKey);
                            RodDefinition.DimBonus mb = def.ensureMoon(m);

                            ConfigurationSection rSub = node.getConfigurationSection("rarity");
                            if (rSub != null) {
                                for (String rk : rSub.getKeys(false)) {
                                    try {
                                        FishRarity rar = FishRarity.valueOf(rk.toUpperCase(Locale.ENGLISH));
                                        mb.rarityBonus().put(rar, rSub.getDouble(rk));
                                    } catch (IllegalArgumentException ex) {
                                        plugin.getLogger().warning("[Rods] Unknown rarity '" + rk + "' under moon '" + mKey + "' in rod '" + id + "'");
                                    }
                                }
                            }
                            if (node.isDouble("speed") || node.isInt("speed"))   mb.speed(node.getDouble("speed", 0.0));
                            if (node.isDouble("double") || node.isInt("double")) mb.doubleChance(node.getDouble("double", 0.0));
                        } else {
                            // flat number
                            def.moonBonusFlat().put(m, mSec.getDouble(mKey));
                        }
                    }
                }

                // --- BIOME: flat or nested ---
                ConfigurationSection bSec = bonus.getConfigurationSection("biome");
                if (bSec != null) {
                    for (String bKey : bSec.getKeys(false)) {
                        Biome b;
                        try { b = Biome.valueOf(bKey.toUpperCase(Locale.ENGLISH)); }
                        catch (IllegalArgumentException ex) { plugin.getLogger().warning("[Rods] Unknown biome '" + bKey + "' in rod '" + id + "'"); continue; }

                        if (bSec.isConfigurationSection(bKey)) {
                            // nested
                            ConfigurationSection node = bSec.getConfigurationSection(bKey);
                            RodDefinition.DimBonus bb = def.ensureBiome(b);

                            ConfigurationSection rSub = node.getConfigurationSection("rarity");
                            if (rSub != null) {
                                for (String rk : rSub.getKeys(false)) {
                                    try {
                                        FishRarity rar = FishRarity.valueOf(rk.toUpperCase(Locale.ENGLISH));
                                        bb.rarityBonus().put(rar, rSub.getDouble(rk));
                                    } catch (IllegalArgumentException ex) {
                                        plugin.getLogger().warning("[Rods] Unknown rarity '" + rk + "' under biome '" + bKey + "' in rod '" + id + "'");
                                    }
                                }
                            }
                            if (node.isDouble("speed") || node.isInt("speed"))   bb.speed(node.getDouble("speed", 0.0));
                            if (node.isDouble("double") || node.isInt("double")) bb.doubleChance(node.getDouble("double", 0.0));
                        } else {
                            // flat number
                            def.biomeBonusFlat().put(b, bSec.getDouble(bKey));
                        }
                    }
                }

                // --- WEATHER (flat stays the same) ---
                ConfigurationSection wSec = bonus.getConfigurationSection("weather");
                if (wSec != null) {
                    for (String k : wSec.getKeys(false)) {
                        try {
                            RodDefinition.Weather w = RodDefinition.Weather.valueOf(k.toUpperCase(Locale.ENGLISH));
                            def.weatherBonus().put(w, wSec.getDouble(k));
                        } catch (IllegalArgumentException ex) {
                            plugin.getLogger().warning("[Rods] Unknown weather '" + k + "' in rod '" + id + "'");
                        }
                    }
                }
            }

            out.put(id, def);
        }

        cache = out;
        return out;
    }

    public RodDefinition get(String id) {
        Map<String, RodDefinition> m = cache.isEmpty() ? parse() : cache;
        return (id == null) ? null : m.get(id.toLowerCase(Locale.ENGLISH));
    }

    public Collection<RodDefinition> all() {
        Map<String, RodDefinition> m = cache.isEmpty() ? parse() : cache;
        return m.values();
    }

    public Set<String> ids() {
        Map<String, RodDefinition> m = cache.isEmpty() ? parse() : cache;
        return Collections.unmodifiableSet(new TreeSet<>(m.keySet()));
    }

    public boolean exists(String id) {
        Map<String, RodDefinition> m = cache.isEmpty() ? parse() : cache;
        return id != null && m.containsKey(id.toLowerCase(Locale.ENGLISH));
    }
}

