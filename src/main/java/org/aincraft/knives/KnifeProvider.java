// src/main/java/org/aincraft/knives/KnifeProvider.java
package org.aincraft.knives;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public final class KnifeProvider {
    private final JavaPlugin plugin;
    private final FileConfiguration cfg;
    private final Map<String, KnifeDefinition> defs = new HashMap<>();

    private static final String DEFAULT_NAMESPACE = "longhardfish";
    // Back to the original location you used (no "item/" prefix).
    private static final String DEFAULT_MODEL_PREFIX = "knives/";

    public KnifeProvider(JavaPlugin plugin, FileConfiguration knivesYaml) {
        this.plugin = plugin;
        this.cfg = knivesYaml;
    }

    public void parse() {
        defs.clear();
        ConfigurationSection root = cfg.getConfigurationSection("knives");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;

            String displayName = s.getString("name", "&fKnife");

            Material base = Material.matchMaterial(s.getString("base", "IRON_SWORD"));
            if (base == null) base = Material.IRON_SWORD;

            // Accept:
            //  - "knives/iron_knife"
            //  - "longhardfish:knives/iron_knife"
            //  - a pasted path like "assets/longhardfish/models/knives/iron_knife.json"
            String rawModel = s.getString("model", DEFAULT_MODEL_PREFIX + id);
            NamespacedKey modelKey = normalizeModelKey(rawModel);

            Integer durability  = s.isInt("durability") ? s.getInt("durability") : null;
            Double attackDamage = s.isSet("attack.damage") ? s.getDouble("attack.damage") : null;
            Double attackSpeed  = s.isSet("attack.speed")  ? s.getDouble("attack.speed")  : null;
            Integer enchantability = s.isInt("enchantability") ? s.getInt("enchantability") : null;

            Set<Enchantment> allowed = parseEnchantList(s.getStringList("allowed_enchantments"));
            Set<Enchantment> disallowed = parseEnchantList(s.getStringList("disallowed_enchantments"));

            KnifeDefinition def = new KnifeDefinition(
                    id,
                    displayName,
                    base,
                    modelKey,
                    durability,
                    attackDamage,
                    attackSpeed,
                    enchantability,
                    allowed,
                    disallowed
            );

            defs.put(id.toLowerCase(Locale.ENGLISH), def);
        }
        plugin.getLogger().info("[Knives] Loaded " + defs.size() + " knife definitions.");
    }

    /** Leaves the body exactly as provided; only strips RP prefix, removes .json, and injects namespace if missing. */
    private NamespacedKey normalizeModelKey(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String path = raw.replace('\\', '/');

        // If a full resource-pack path was pasted, trim to post-"/models/" portion.
        int modelsIdx = path.indexOf("/models/");
        if (path.startsWith("assets/") && modelsIdx >= 0) {
            path = path.substring(modelsIdx + "/models/".length()); // e.g. "knives/iron_knife"
        }
        if (path.endsWith(".json")) path = path.substring(0, path.length() - 5);

        // Inject default namespace if missing
        if (!path.contains(":")) {
            path = DEFAULT_NAMESPACE + ":" + path; // e.g. "longhardfish:knives/iron_knife"
        }
        return NamespacedKey.fromString(path);
    }

    private static Set<Enchantment> parseEnchantList(List<String> names) {
        if (names == null || names.isEmpty()) return Collections.emptySet();
        Set<Enchantment> out = new HashSet<>();
        for (String n : names) {
            if (n == null || n.isBlank()) continue;
            // Try modern key first: "minecraft:sharpness" or "sharpness"
            Enchantment ench = null;
            String lower = n.toLowerCase(Locale.ENGLISH).trim();
            try {
                // Allow both namespaced and plain
                var key = lower.contains(":")
                        ? org.bukkit.NamespacedKey.fromString(lower)
                        : org.bukkit.NamespacedKey.minecraft(lower);
                if (key != null) ench = Enchantment.getByKey(key);
            } catch (Throwable ignored) {}

            if (ench == null) {
                // Fallback legacy name (e.g., "SHARPNESS")
                ench = Enchantment.getByName(n.toUpperCase(Locale.ENGLISH));
            }
            if (ench != null) out.add(ench);
        }
        return out;
    }

    public KnifeDefinition get(String id) {
        return defs.get(id.toLowerCase(Locale.ENGLISH));
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(defs.keySet());
    }
}