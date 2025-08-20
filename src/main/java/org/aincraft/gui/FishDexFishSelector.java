package org.aincraft.gui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;
import org.aincraft.container.FishEnvironment;
import org.aincraft.container.FishModel;
import org.aincraft.container.FishMoonCycle;
import org.aincraft.container.FishTimeCycle;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.kyori.adventure.key.Key.key;
import static net.kyori.adventure.text.Component.text;

public class FishDexFishSelector {

    // ===== Dependencies you inject =====
    public interface EnvLookup {
        FishEnvironment get(NamespacedKey fishId);
    }
    public interface ModelLookup {
        FishModel get(NamespacedKey fishId);
    }
    /** Map a fish to its model *texture* suffix in your RP (e.g. "amethyst-riptideon") */
    public interface FishIconSuffix extends Function<FishModel, String> {}

    public static FishDexFishSelector create(
            JavaPlugin plugin,
            NamespacedKey immovableKey,
            BiConsumer<Player, Inventory> maskApplier,
            EnvLookup envLookup,
            ModelLookup modelLookup,
            FishIconSuffix fishIconSuffix
    ) {
        return new FishDexFishSelector(plugin, immovableKey, maskApplier, envLookup, modelLookup, fishIconSuffix);
    }

    private final JavaPlugin plugin;
    private final NamespacedKey IMMOVABLE_KEY; // lock key for immovable items (Bukkit key)
    private final BiConsumer<Player, Inventory> maskApplier;
    private final EnvLookup envLookup;
    private final ModelLookup modelLookup;
    private final FishIconSuffix fishIconSuffix;

    private static final String NS = "longhardfish";
    private static final int PAGE_SIZE = 35; // 1–35, 36–70, ...

    public FishDexFishSelector(
            JavaPlugin plugin,
            NamespacedKey immovableKey,
            BiConsumer<Player, Inventory> maskApplier,
            EnvLookup envLookup,
            ModelLookup modelLookup,
            FishIconSuffix fishIconSuffix
    ) {
        this.plugin = plugin;
        this.IMMOVABLE_KEY = immovableKey;
        this.maskApplier = maskApplier;
        this.envLookup = envLookup;
        this.modelLookup = modelLookup;
        this.fishIconSuffix = fishIconSuffix;
    }

    // ===== Fixed slots you already used =====
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

    // Main 7×5 grid (35)
    private static final int[] FISH_GRID_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43,
            46, 47, 48, 49, 50, 51, 52
    };

    // Where to drop environment “chrome” in the player main inv (row, col)
    private static final int[][] ENV_ICON_SLOTS = new int[][]{
            {1,3},{1,1},{1,2},{2,0},{2,1},{2,2},{2,3},
            {1,6},{1,7},{1,5},{2,6},{2,7},{2,8},{2,5},{1,4},{2,4}
    };

    // ===== Public API =====

    /**
     * Open the Dex for a specific fish ID; icons reflect that fish's environment.
     */
    public void open(Player player, NamespacedKey fishId) {
        Inventory gui = Bukkit.createInventory(
                (InventoryHolder) null,
                54,
                text("\ue001\ua020\ue002\ua021").font(key(NS, "interface")).color(TextColor.color(0xFFFFFF))
        );
        if (maskApplier != null) maskApplier.accept(player, gui);

        // Offset art (unchanged)
        putIcon(gui, 9, fishId + "-offset");

        // Resolve fish data
        FishEnvironment env = (envLookup != null) ? envLookup.get(fishId) : null;
        FishModel model = (modelLookup != null) ? modelLookup.get(fishId) : null;

        // If we have a model, show its fish icon in the first grid cell
        if (model != null && fishIconSuffix != null) {
            String fishSuffix = fishIconSuffix.apply(model);
            if (fishSuffix != null && !fishSuffix.isEmpty()) {
                putIcon(gui, FISH_GRID_SLOTS[0], fishSuffix);
            }
            // Menu page indicator by model number
            int menuSlot = chooseMenuSlotFromModel(model.getModelNumber());
            putIcon(gui, menuSlot, "icons/fish-menu");
        } else {
            // Fallback if no model
            putIcon(gui, 0, "icons/fish-menu");
        }

        // Time & Moon from this fish's environment
        placeTimeIconsForFish(gui, env);
        placeMoonIconsForFish(gui, player, env);

        // Environment chrome from this fish's biome set
        placeEnvironmentChromeForFish(player, env);

        // Flags
        placeFlags(player, env);

        // Nav icons (kept)
        putPlayerIconHotbar(player, 6, "icons/next-1");
        putPlayerIconHotbar(player, 7, "icons/next-all");
        putPlayerIconHotbar(player, 2, "icons/previous-1");
        putPlayerIconHotbar(player, 1, "icons/previous-all");

        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(gui));
    }

    // ===== Icon placement helpers =====

    private void placeTimeIconsForFish(Inventory gui, FishEnvironment env) {
        // If no env, show all
        Set<FishTimeCycle> allowed = (env != null && env.getEnvironmentTimes() != null)
                ? env.getEnvironmentTimes().entrySet().stream()
                .filter(e -> e.getValue() > 0d)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(FishTimeCycle.class)))
                : EnumSet.allOf(FishTimeCycle.class);

        for (FishTimeCycle t : List.of(FishTimeCycle.DAWN, FishTimeCycle.DAY, FishTimeCycle.EVENING, FishTimeCycle.NIGHT)) {
            if (!allowed.contains(t)) continue;
            Integer slot = TIME_SLOTS.get(t);
            String suffix = TIME_ICON_SUFFIX.get(t);
            if (slot != null && suffix != null) putIcon(gui, slot, suffix);
        }
    }

    private void placeMoonIconsForFish(Inventory gui, Player player, FishEnvironment env) {
        Set<FishMoonCycle> allowed = (env != null && env.getEnvironmentMoons() != null)
                ? env.getEnvironmentMoons().entrySet().stream()
                .filter(e -> e.getValue() > 0d)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(FishMoonCycle.class)))
                : EnumSet.allOf(FishMoonCycle.class);

        int placed = 0;
        for (FishMoonCycle moon : MOON_ORDER) {
            if (!allowed.contains(moon)) continue;
            String suffix = MOON_ICON_SUFFIX.get(moon);
            if (suffix == null) continue;

            if (placed == 0) putIcon(gui, 35, suffix);
            else if (placed == 1) putIcon(gui, 44, suffix);
            else if (placed == 2) putIcon(gui, 53, suffix);
            else if (placed == 3) putPlayerIconMain(player, 0, 7, suffix);
            else if (placed == 4) putPlayerIconHotbar(player, 8, suffix);
            else if (placed == 5) putPlayerIconMain(player, 1, 8, suffix);
            else if (placed == 6) putPlayerIconMain(player, 0, 6, suffix);
            else if (placed == 7) putPlayerIconMain(player, 0, 8, suffix);
            placed++;
        }
    }

    private void placeEnvironmentChromeForFish(Player player, FishEnvironment env) {
        // Derive distinct “category” icons from concrete biomes
        List<String> envIcons = new ArrayList<>();
        if (env != null && env.getEnvironmentBiomes() != null) {
            List<String> finalEnvIcons = envIcons;
            env.getEnvironmentBiomes().entrySet().stream()
                    .filter(e -> e.getValue() > 0d)
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue())) // strongest first
                    .map(Map.Entry::getKey)
                    .map(this::iconSuffixForBiome)
                    .filter(Objects::nonNull)
                    .forEach(icon -> {
                        if (!finalEnvIcons.contains(icon)) finalEnvIcons.add(icon);
                    });
        }

        // If nothing resolved, show a small generic set as fallback
        if (envIcons.isEmpty()) {
            envIcons = List.of(
                    "icons/env-river",
                    "icons/env-woodlands",
                    "icons/env-flat-lands",
                    "icons/env-rain-forest",
                    "icons/env-cold-places"
            );
        }

        // Place up to the available chrome slots
        int limit = Math.min(envIcons.size(), ENV_ICON_SLOTS.length);
        for (int i = 0; i < limit; i++) {
            int[] rc = ENV_ICON_SLOTS[i];
            putPlayerIconMain(player, rc[0], rc[1], envIcons.get(i));
        }
    }

    private void placeFlags(Player player, FishEnvironment env) {
        boolean rainReq = env != null && env.getRainRequired();
        boolean openReq = env != null && env.getOpenWaterRequired();

        // Weather indicator (your pack already had a sunny icon)
        putPlayerIconMain(player, 0, 5, rainReq ? "icons/rainy-icon" : "icons/sunny-icon");

        // Show open-water requirement badge if you have one in your pack; otherwise skip
        if (openReq) {
            putPlayerIconMain(player, 0, 2, "icons/open-water-required"); // adjust to your actual key
        }
        // Optional: description gem & bait left as-is
        putPlayerIconMain(player, 0, 3, "icons/bait-icon-full");
        putPlayerIconMain(player, 0, 4, "icons/description-gem-icon");
    }

    // ===== Utilities =====

    private int chooseMenuSlotFromModel(int modelNumber) {
        int pageIndex = Math.max(0, (modelNumber - 1) / PAGE_SIZE);
        return Math.min(6, pageIndex); // 0..6 top row
    }

    /** Very simple bucket → icon mapping. Tweak to your exact RP keys. */
    private String iconSuffixForBiome(Biome biome) {
        String n = biome.name();

        if (n.contains("RIVER")) return "icons/env-river";
        if (n.contains("OCEAN")) {
            if (n.contains("WARM")) return "icons/env-warm-saltwater";
            if (n.contains("COLD") || n.contains("FROZEN")) return "icons/env-cold-saltwater";
            if (n.contains("DEEP")) return "icons/env-deep-saltwater";
            return "icons/env-shallow-saltwater";
        }
        if (n.contains("BEACH") || n.contains("STONY_SHORE")) return "icons/env-shallow-saltwater";
        if (n.contains("JUNGLE") || n.contains("BAMBOO")) return "icons/env-rain-forest";
        if (n.contains("SWAMP") || n.contains("MANGROVE")) return "icons/env-wet-lands";
        if (n.contains("FOREST")) return "icons/env-woodlands";
        if (n.contains("TAIGA") || n.contains("GROVE") || n.contains("SNOWY")) return "icons/env-cold-places";
        if (n.contains("BADLANDS") || n.contains("DESERT") || n.contains("SAVANNA")) return "icons/env-hot-places";
        if (n.contains("MUSHROOM")) return "icons/env-weird";
        if (n.contains("HILLS") || n.contains("MOUNTAIN") || n.contains("PEAKS") || n.contains("WINDSWEPT")) return "icons/env-mountain";
        if (n.contains("LUSH_CAVES") || n.contains("DRIPSTONE") || n.contains("DEEP_DARK")) return "icons/env-caves";
        if (n.contains("PLAINS") || n.contains("MEADOW") || n.contains("SUNFLOWER")) return "icons/env-flat-lands";
        // End/Nether as “weird” by default
        if (n.contains("END") || n.contains("NETHER") || n.contains("THE_VOID")) return "icons/env-weird";

        // Default catch-all
        return "icons/env-woodlands";
    }

    // ===== Optional: keep these if you still need the “current env” view =====

    private FishTimeCycle resolveTime(long worldTime) {
        for (FishTimeCycle t : FishTimeCycle.values()) {
            double start = t.getStartTime();
            double end = t.getEndTime();
            if (start <= end) {
                if (worldTime >= start && worldTime < end) return t;
            } else {
                if (worldTime >= start || worldTime < end) return t;
            }
        }
        return FishTimeCycle.DAY;
    }

    private FishMoonCycle resolveMoon(World world) {
        long day = (world.getFullTime() / 24000L) % 8L;
        int phase = (int) day;
        for (FishMoonCycle m : FishMoonCycle.values()) {
            if (m.getMoonTime() == phase) return m;
        }
        return FishMoonCycle.FULL_MOON;
    }

    // ===== Icon wrappers =====

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
}
