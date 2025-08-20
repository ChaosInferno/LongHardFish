package org.aincraft.gui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;
import org.aincraft.container.FishEnvironment;
import org.aincraft.container.FishModel;
import org.aincraft.container.FishMoonCycle;
import org.aincraft.container.FishTimeCycle;
import org.aincraft.container.FishDistribution;
import org.aincraft.container.FishRarity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static net.kyori.adventure.key.Key.key;
import static net.kyori.adventure.text.Component.text;

/**
 * Opens a “FishDex” GUI for a specific fishId. Icons (time/moon/biome/weather/open-water)
 * reflect the fish's allowed environment from fish.yml. The fish art is resolved by a
 * pluggable mapping (by default: "fish/fish-{modelNumber}").
 */
public class FishDexFishSelector {

    /** Called to fetch the parsed FishEnvironment for a fish id. */
    public interface EnvLookup {
        FishEnvironment get(NamespacedKey fishId);
    }

    /** Called to fetch the parsed FishModel (name/description/modelNumber) for a fish id. */
    public interface ModelLookup {
        FishModel get(NamespacedKey fishId);
    }

    public interface DistributionLookup {
        FishDistribution get(NamespacedKey fishId);
    }

    /** Maps a FishModel to a resource-pack texture key suffix (e.g. "fish/fish-37"). */
    public interface FishIconSuffix extends Function<FishModel, String> {}

    public static FishDexFishSelector create(
            JavaPlugin plugin,
            NamespacedKey immovableKey,
            BiConsumer<Player, Inventory> maskApplier,
            EnvLookup envLookup,
            ModelLookup modelLookup
    ) {
        // Default art: "fish/fish-{modelNumber}". Adjust if your RP uses different paths.
        FishIconSuffix defaultMapper = model -> "fish/fish-" + model.getModelNumber();
        return new FishDexFishSelector(plugin, immovableKey, maskApplier, envLookup, modelLookup, id -> null, defaultMapper);
    }

    public static FishDexFishSelector create(
            JavaPlugin plugin,
            NamespacedKey immovableKey,
            BiConsumer<Player, Inventory> maskApplier,
            EnvLookup envLookup,
            ModelLookup modelLookup,
            DistributionLookup distributionLookup,
            FishIconSuffix fishIconSuffix
    ) {
        return new FishDexFishSelector(plugin, immovableKey, maskApplier, envLookup, modelLookup, distributionLookup, fishIconSuffix);
    }

    private final JavaPlugin plugin;
    private final NamespacedKey IMMOVABLE_KEY;
    private final BiConsumer<Player, Inventory> maskApplier;
    private final EnvLookup envLookup;
    private final ModelLookup modelLookup;
    private final FishIconSuffix fishIconSuffix;
    private final DistributionLookup distributionLookup;

    private static final String NS = "longhardfish";
    private static final int PAGE_SIZE = 35;

    private static final Map<FishTimeCycle, Integer> TIME_SLOTS = Map.of(
            FishTimeCycle.DAWN, 7,
            FishTimeCycle.DAY, 8,
            FishTimeCycle.EVENING, 17,
            FishTimeCycle.NIGHT, 26
    );
    private static final Map<FishTimeCycle, String> TIME_ICON_SUFFIX = Map.of(
            FishTimeCycle.DAWN, "icons/morning-icon",
            FishTimeCycle.DAY, "icons/sun-icon",
            FishTimeCycle.EVENING, "icons/evening-icon",
            FishTimeCycle.NIGHT, "icons/moon-icon"
    );

    private static final List<FishMoonCycle> MOON_ORDER = List.of(
            FishMoonCycle.FULL_MOON,
            FishMoonCycle.WANING_GIBBOUS,
            FishMoonCycle.LAST_QUARTER,
            FishMoonCycle.WANING_CRESCENT,
            FishMoonCycle.NEW_MOON,
            FishMoonCycle.WAXING_CRESCENT,
            FishMoonCycle.FIRST_QUARTER,
            FishMoonCycle.WAXING_GIBBOUS
    );
    private static final Map<FishMoonCycle, String> MOON_ICON_SUFFIX = Map.of(
            FishMoonCycle.FULL_MOON, "icons/full_moon",
            FishMoonCycle.WANING_GIBBOUS, "icons/waning_gibbous",
            FishMoonCycle.LAST_QUARTER, "icons/last_quarter",
            FishMoonCycle.WANING_CRESCENT, "icons/waning_crescent",
            FishMoonCycle.NEW_MOON, "icons/new_moon",
            FishMoonCycle.WAXING_CRESCENT, "icons/waxing_crescent",
            FishMoonCycle.FIRST_QUARTER, "icons/first_quarter",
            FishMoonCycle.WAXING_GIBBOUS, "icons/waxing_gibbous"
    );

    // Main 7×5 grid
    private static final int[] FISH_GRID_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43,
            46, 47, 48, 49, 50, 51, 52
    };

    // Where to drop “environment chrome” in the player’s main inventory
    private static final int[][] ENV_ICON_SLOTS = new int[][]{
            {1,3},{1,1},{1,2},{2,0},{2,1},{2,2},{2,3},
            {1,6},{1,7},{1,5},{2,6},{2,7},{2,8},{2,5},{1,4},{2,4}
    };

    private FishDexFishSelector(
            JavaPlugin plugin,
            NamespacedKey immovableKey,
            BiConsumer<Player, Inventory> maskApplier,
            EnvLookup envLookup,
            ModelLookup modelLookup,
            FishIconSuffix fishIconSuffix
    ) {
        this(plugin, immovableKey, maskApplier, envLookup, modelLookup, id -> null, fishIconSuffix);
    }

    private FishDexFishSelector(
            JavaPlugin plugin,
            NamespacedKey immovableKey,
            BiConsumer<Player, Inventory> maskApplier,
            EnvLookup envLookup,
            ModelLookup modelLookup,
            DistributionLookup distributionLookup,
            FishIconSuffix fishIconSuffix
    ) {
        this.plugin = plugin;
        this.IMMOVABLE_KEY = immovableKey;
        this.maskApplier = maskApplier;
        this.envLookup = envLookup;
        this.modelLookup = modelLookup;
        this.distributionLookup = distributionLookup;
        this.fishIconSuffix = fishIconSuffix;
    }

    /** Open the GUI for a specific fish. */
    public void open(Player player, NamespacedKey fishId) {
        Inventory gui = Bukkit.createInventory(
                (InventoryHolder) null,
                54,
                text("\ue001\ua020\ue002\ua021").font(key(NS, "interface")).color(TextColor.color(0xFFFFFF))
        );
        if (maskApplier != null) maskApplier.accept(player, gui);

        // Background offset art (adjust if your RP uses a different path)
        putIcon(gui, 9, fishId.getKey() + "-offset");

        // Resolve config-backed data
        FishEnvironment env = (envLookup != null) ? envLookup.get(fishId) : null;
        FishModel model = (modelLookup != null) ? modelLookup.get(fishId) : null;
        FishDistribution dist = (distributionLookup != null) ? distributionLookup.get(fishId) : null;

        // Place the fish’s own icon in the first grid cell
        if (model != null && fishIconSuffix != null) {
            String fishSuffix = fishIconSuffix.apply(model);
            if (fishSuffix != null && !fishSuffix.isEmpty()) {
                putIcon(gui, FISH_GRID_SLOTS[0], fishSuffix);
            }
            putIcon(gui, chooseMenuSlotFromModel(model.getModelNumber()), "icons/fish-menu");
        } else {
            putIcon(gui, 0, "icons/fish-menu");
        }

        // Time & Moon icons derived from this fish’s allowed environment
        placeTimeIconsForFish(gui, env);
        placeMoonIconsForFish(gui, player, env);

        // Biome “chrome”, rain/sun flag, open-water flag
        placeEnvironmentGroupsForFish(player, env);
        placeFlags(player, env);

        // Rarity badge in slot 45
        placeRarityIcon(gui, dist);

        putPlayerIconMain(player, 0, 3, "icons/bait-icon-full");
        putPlayerIconMain(player, 0, 4, "icons/description-gem-icon");

        // Nav icons
        putPlayerIconHotbar(player, 6, "icons/next-1");
        putPlayerIconHotbar(player, 7, "icons/next-all");
        putPlayerIconHotbar(player, 2, "icons/previous-1");
        putPlayerIconHotbar(player, 1, "icons/previous-all");

        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(gui));
    }

    // --- Icon placement helpers ---

    // --- Time icons: always place; use *_empty if fish can't be found at that time
    private void placeTimeIconsForFish(Inventory gui, FishEnvironment env) {
        // build the set without reassigning the reference
        Set<FishTimeCycle> allowed = EnumSet.noneOf(FishTimeCycle.class);
        if (env == null || env.getEnvironmentTimes() == null || env.getEnvironmentTimes().isEmpty()) {
            allowed.addAll(EnumSet.allOf(FishTimeCycle.class));
        } else {
            env.getEnvironmentTimes().forEach((t, weight) -> {
                if (weight != null && weight > 0d) {
                    allowed.add(t);
                }
            });
        }

        for (FishTimeCycle t : List.of(FishTimeCycle.DAWN, FishTimeCycle.DAY, FishTimeCycle.EVENING, FishTimeCycle.NIGHT)) {
            Integer slot = TIME_SLOTS.get(t);
            String base = TIME_ICON_SUFFIX.get(t);
            if (slot == null || base == null) continue;

            boolean ok = allowed.contains(t);
            String suffix = ok ? base : (base + "_empty");
            putIcon(gui, slot, suffix);
        }
    }

    // --- Moon icons: always place; use *_empty if fish can't be found during that phase
    private void placeMoonIconsForFish(Inventory gui, Player player, FishEnvironment env) {
        // build the set without reassigning the reference
        Set<FishMoonCycle> allowed = EnumSet.noneOf(FishMoonCycle.class);
        if (env == null || env.getEnvironmentMoons() == null || env.getEnvironmentMoons().isEmpty()) {
            allowed.addAll(EnumSet.allOf(FishMoonCycle.class));
        } else {
            env.getEnvironmentMoons().forEach((m, weight) -> {
                if (weight != null && weight > 0d) {
                    allowed.add(m);
                }
            });
        }

        int i = 0;
        for (FishMoonCycle moon : MOON_ORDER) {
            String base = MOON_ICON_SUFFIX.get(moon);
            if (base == null) { i++; continue; }

            boolean ok = allowed.contains(moon);
            String suffix = ok ? base : (base + "_empty");

            if (i == 0)      putIcon(gui, 35, suffix);
            else if (i == 1) putIcon(gui, 44, suffix);
            else if (i == 2) putIcon(gui, 53, suffix);
            else if (i == 3) putPlayerIconMain(player, 0, 7, suffix);
            else if (i == 4) putPlayerIconHotbar(player, 8, suffix);
            else if (i == 5) putPlayerIconMain(player, 1, 8, suffix);
            else if (i == 6) putPlayerIconMain(player, 0, 6, suffix);
            else if (i == 7) putPlayerIconMain(player, 0, 8, suffix);
            i++;
        }
    }

    // ---- Environment GROUPS -> icon placements ----
    private static final class EnvGroup {
        final String iconBase;     // e.g. "icons/env-warm-oceans"
        final int row, col;        // player main inventory grid position
        final Set<String> biomeNames; // biome names in UPPER_CASE

        EnvGroup(String iconBase, int row, int col, Set<String> biomeNames) {
            this.iconBase = iconBase;
            this.row = row;
            this.col = col;
            this.biomeNames = biomeNames;
        }
    }
    private static EnvGroup group(String iconBase, int row, int col, String... biomeNames) {
        // store names as provided (UPPER_CASE expected)
        return new EnvGroup(iconBase, row, col, new HashSet<>(Arrays.asList(biomeNames)));
    }

    /** The 16 groups you listed, with their target slots and icon keys */
    private static final List<EnvGroup> ENV_GROUPS = List.of(
            group("icons/env-warm-oceans",        1, 3, "WARM_OCEAN","LUKEWARM_OCEAN","DEEP_LUKEWARM_OCEAN","DEEP_OCEAN"),
            group("icons/env-cold-oceans",        1, 1, "COLD_OCEAN","DEEP_COLD_OCEAN","FROZEN_OCEAN","DEEP_FROZEN_OCEAN"),
            group("icons/env-coasts",             1, 2, "BEACH","STONY_SHORE","OCEAN","MUSHROOM_FIELDS"),
            group("icons/env-inland-wetlands",    2, 0, "RIVER","FROZEN_RIVER","SWAMP","MANGROVE_SWAMP"),
            group("icons/env-snowy-peaks",        2, 1, "JAGGED_PEAKS","FROZEN_PEAKS","STONY_PEAKS","SNOWY_SLOPES"),
            group("icons/env-meadows-and-groves", 2, 2, "MEADOW","GROVE","CHERRY_GROVE","PALE_GARDEN"),
            group("icons/env-snowy-lowlands",     2, 3, "SNOWY_PLAINS","ICE_SPIKES","SNOWY_BEACH"),
            group("icons/env-windswept",          1, 6, "WINDSWEPT_HILLS","WINDSWEPT_GRAVELLY_HILLS","WINDSWEPT_FOREST"),
            group("icons/env-mesa-badlands",      1, 7, "BADLANDS","ERODED_BADLANDS","WOODED_BADLANDS"),
            group("icons/env-hot-lands",          1, 5, "DESERT","SAVANNA","WINDSWEPT_SAVANNA"),
            group("icons/env-open-lands",         2, 6, "PLAINS","SUNFLOWER_PLAINS","SAVANNA_PLATEAU","FLOWER_FOREST"),
            group("icons/env-temperate-forests",  2, 7, "FOREST","BIRCH_FOREST","OLD_GROWTH_BIRCH_FOREST","DARK_FOREST"),
            group("icons/env-taiga-growth",       2, 8, "TAIGA","SNOWY_TAIGA","OLD_GROWTH_SPRUCE_TAIGA","OLD_GROWTH_PINE_TAIGA"),
            group("icons/env-jungles",            2, 5, "JUNGLE","SPARSE_JUNGLE","BAMBOO_JUNGLE"),
            group("icons/env-caves",              1, 4, "LUSH_CAVES","DRIPSTONE_CAVES","DEEP_DARK"),
            group("icons/env-the-end",            2, 4, "THE_END","END_HIGHLANDS","END_MIDLANDS","END_BARRENS","SMALL_END_ISLANDS")
    );

    /**
     * For the selected fish, show each group icon “filled” if the fish can spawn in ANY biome
     * from that group; otherwise show the same icon with the "_empty" suffix.
     */
    private void placeEnvironmentGroupsForFish(Player player, FishEnvironment env) {
        // Biomes the fish actually uses, by NAME (so we can match custom/non-vanilla too)
        Set<String> fishBiomes = new HashSet<>();
        if (env != null && env.getEnvironmentBiomes() != null) {
            env.getEnvironmentBiomes().forEach((biome, weight) -> {
                if (weight != null && weight > 0d && biome != null) {
                    fishBiomes.add(biome.name()); // UPPER_CASE
                }
            });
        }

        for (EnvGroup g : ENV_GROUPS) {
            boolean found = !Collections.disjoint(fishBiomes, g.biomeNames);
            String suffix = g.iconBase + (found ? "" : "_empty");
            // same call pattern you showed:
            putPlayerIconMain(player, g.row, g.col, suffix);
        }
    }


    private void placeFlags(Player player, FishEnvironment env) {
        boolean rainReq = env != null && Boolean.TRUE.equals(env.getRainRequired());
        boolean openReq = env != null && Boolean.TRUE.equals(env.getOpenWaterRequired());

        // Weather indicator
        putPlayerIconMain(player, 0, 5, rainReq ? "icons/rainy-icon" : "icons/sunny-icon");
        // Open-water badge (only if you have this texture in your RP)
        if (openReq) putPlayerIconMain(player, 0, 2, "icons/open-water-required");
    }

    // --- Utilities ---

    private int chooseMenuSlotFromModel(int modelNumber) {
        int pageIndex = Math.max(0, (modelNumber - 1) / PAGE_SIZE);
        return Math.min(6, pageIndex); // top row 0..6
    }

    // --- Icon wrappers ---

    private void putIcon(Inventory inv, int slot, String modelSuffix) {
        GuiItemSlot.putImmovableIcon(inv, slot, Material.COD, Key.key(NS, modelSuffix), IMMOVABLE_KEY);
    }

    private void putPlayerIconMain(Player p, int row, int col, String modelSuffix) {
        GuiItemSlot.putImmovableIcon(p.getInventory(), GuiItemSlot.main(row, col),
                Material.COD, Key.key(NS, modelSuffix), IMMOVABLE_KEY, false);
    }

    private void putPlayerIconHotbar(Player p, int index, String modelSuffix) {
        GuiItemSlot.putImmovableIcon(p.getInventory(), GuiItemSlot.hotbar(index),
                Material.COD, Key.key(NS, modelSuffix), IMMOVABLE_KEY, false);
    }

    private static String iconSuffixForRarity(FishRarity rarity) {
        if (rarity == null) return "icons/rarity-common";
        switch (rarity) {
            case UNCOMMON:  return "icons/rarity-uncommon";
            case RARE:      return "icons/rarity-rare";
            case LEGENDARY: return "icons/rarity-legendary";
            case COMMON:
            default:        return "icons/rarity-common";
        }
    }

    /** Places the rarity badge at GUI slot 45 based on the fish's rarity. */
    private void placeRarityIcon(Inventory gui, FishDistribution dist) {
        FishRarity rarity = (dist != null) ? dist.getRarity() : null;
        String suffix = iconSuffixForRarity(rarity);
        // Equivalent to: GuiItemSlot.putImmovableIcon(gui,45,Material.COD,Key.key("longhardfish", suffix), IMM...)
        putIcon(gui, 45, suffix);
    }
}
