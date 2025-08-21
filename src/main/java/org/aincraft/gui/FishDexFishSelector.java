package org.aincraft.gui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.aincraft.container.FishDistribution;
import org.aincraft.container.FishEnvironment;
import org.aincraft.container.FishModel;
import org.aincraft.container.FishMoonCycle;
import org.aincraft.container.FishRarity;
import org.aincraft.container.FishTimeCycle;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static net.kyori.adventure.key.Key.key;
import static net.kyori.adventure.text.Component.text;

public class FishDexFishSelector {

    // ===== Lookups =====
    public interface EnvLookup { FishEnvironment get(NamespacedKey fishId); }
    public interface ModelLookup { FishModel get(NamespacedKey fishId); }
    public interface DistributionLookup { FishDistribution get(NamespacedKey fishId); }

    /** Holder so we can recognize the GUI and keep paging/click mapping. */
    public static final class DexHolder implements InventoryHolder {
        private Inventory inv;
        public final Map<Integer, NamespacedKey> slotToFish = new HashMap<>();
        public NamespacedKey selected;

        // paging state
        public List<NamespacedKey> ordered = List.of();
        public int pageIndex = 0;
        public int pageCount = 1;

        @Override public Inventory getInventory() { return inv; }
    }

    /** Factory with paging (pass e.g. () -> modelMap.keySet()). */
    public static FishDexFishSelector createWithPaging(
            JavaPlugin plugin,
            NamespacedKey immovableKey,
            BiConsumer<Player, Inventory> maskApplier,
            EnvLookup envLookup,
            ModelLookup modelLookup,
            DistributionLookup distributionLookup,
            Supplier<Collection<NamespacedKey>> allFishIds
    ) {
        return new FishDexFishSelector(plugin, immovableKey, maskApplier, envLookup, modelLookup, distributionLookup, allFishIds);
    }

    private final JavaPlugin plugin;
    private final NamespacedKey IMMOVABLE_KEY;
    private final BiConsumer<Player, Inventory> maskApplier;
    private final EnvLookup envLookup;
    private final ModelLookup modelLookup;
    private final DistributionLookup distributionLookup;
    private final Supplier<Collection<NamespacedKey>> allFishIds; // enables page-fill

    private static final String NS = "longhardfish";
    private static final int PAGE_SIZE = 35;

    // Hotbar nav indices (0..8)
    public static final int HOTBAR_PREV_ALL = 1;
    public static final int HOTBAR_PREV_1   = 2;
    public static final int HOTBAR_NEXT_1   = 6;
    public static final int HOTBAR_NEXT_ALL = 7;

    // Time icons
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

    // Moon icons
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

    // Main 7×5 grid (top inventory slots)
    private static final int[] FISH_GRID_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43,
            46, 47, 48, 49, 50, 51, 52
    };

    // ---- Environment GROUPS -> icon placements ----
    private static final class EnvGroup {
        final String iconBase;     // e.g. "icons/env-warm-oceans"
        final int row, col;        // player main inventory grid position
        final Set<String> biomeNames; // biome names in UPPER_CASE
        EnvGroup(String iconBase, int row, int col, Set<String> biomeNames) {
            this.iconBase = iconBase; this.row = row; this.col = col; this.biomeNames = biomeNames;
        }
    }
    private static EnvGroup group(String iconBase, int row, int col, String... biomeNames) {
        return new EnvGroup(iconBase, row, col, new HashSet<>(Arrays.asList(biomeNames)));
    }
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

    private FishDexFishSelector(
            JavaPlugin plugin,
            NamespacedKey immovableKey,
            BiConsumer<Player, Inventory> maskApplier,
            EnvLookup envLookup,
            ModelLookup modelLookup,
            DistributionLookup distributionLookup,
            Supplier<Collection<NamespacedKey>> allFishIds
    ) {
        this.plugin = plugin;
        this.IMMOVABLE_KEY = immovableKey;
        this.maskApplier = maskApplier;
        this.envLookup = envLookup;
        this.modelLookup = modelLookup;
        this.distributionLookup = distributionLookup;
        this.allFishIds = allFishIds;
    }

    /** Expose plugin so listener can schedule reopen on click. */
    public JavaPlugin plugin() { return plugin; }

    /** Open the GUI for a specific fish. */
    public void open(Player player, NamespacedKey fishId) {
        DexHolder holder = new DexHolder();
        holder.selected = fishId;

        Inventory gui = Bukkit.createInventory(
                holder, // IMPORTANT: use the holder here
                54,
                text("\ue001\ua020\ue002\ua021").font(key(NS, "interface")).color(TextColor.color(0xFFFFFF))
        );
        //noinspection ConstantConditions
        holder.inv = gui;

        if (maskApplier != null) maskApplier.accept(player, gui);

        // Background / offset art
        putIcon(gui, 9, fishId.getKey() + "-offset");

        // Resolve data
        FishEnvironment env = envLookup != null ? envLookup.get(fishId) : null;
        FishModel model       = modelLookup != null ? modelLookup.get(fishId) : null;
        FishDistribution dist = distributionLookup != null ? distributionLookup.get(fishId) : null;

        // ===== Fish grid (with paging) =====
        holder.slotToFish.clear();

        if (allFishIds != null && model != null) {
            // Build ordered list (by model number)
            List<NamespacedKey> ordered = new ArrayList<>(allFishIds.get());
            ordered.sort(Comparator.comparingInt(id -> {
                FishModel fm = modelLookup.get(id);
                return (fm != null) ? fm.getModelNumber() : Integer.MAX_VALUE;
            }));
            holder.ordered = ordered;

            int modelNumber = model.getModelNumber();
            int pageIndex = Math.max(0, (modelNumber - 1) / PAGE_SIZE);
            int pageCount = Math.max(1, (int) Math.ceil(ordered.size() / (double) PAGE_SIZE));
            holder.pageIndex = pageIndex;
            holder.pageCount = pageCount;

            int start = pageIndex * PAGE_SIZE;
            for (int i = 0; i < FISH_GRID_SLOTS.length && (start + i) < ordered.size(); i++) {
                NamespacedKey idOnPage = ordered.get(start + i);
                FishModel fm = modelLookup.get(idOnPage);

                // Your RP mapping: longhardfish:<fish-id> (JSON files in assets/longhardfish/items/<fish-id>.json)
                String suffix = idOnPage.getKey();

                if (suffix != null && !suffix.isEmpty()) {
                    int guiSlot = FISH_GRID_SLOTS[i];
                    putIcon(gui, guiSlot, suffix);
                    holder.slotToFish.put(guiSlot, idOnPage);
                    applyFishTooltip(gui, guiSlot, fm);
                }
            }

            placeMenuIcon(gui, pageIndex);
            placeNavIcons(player, pageIndex, pageCount);

        } else {
            // No paging: show only the selected fish in first slot
            String fishSuffix = fishId.getKey();
            if (fishSuffix != null && !fishSuffix.isEmpty()) {
                int slot = FISH_GRID_SLOTS[0];
                putIcon(gui, slot, fishSuffix);
                holder.slotToFish.put(slot, fishId);
                applyFishTooltip(gui, slot, model);
            }
            int pageIndex = 0;
            if (model != null) {
                pageIndex = Math.max(0, (model.getModelNumber() - 1) / PAGE_SIZE);
            }
            placeMenuIcon(gui, pageIndex);
            holder.ordered = List.of(fishId);
            holder.pageIndex = 0;
            holder.pageCount = 1;
            placeNavIcons(player, 0, 1);
        }

        // ===== Time & Moon =====
        placeTimeIconsForFish(gui, env);
        placeMoonIconsForFish(gui, player, env);

        // ===== Environment groups + flags =====
        placeEnvironmentGroupsForFish(player, env);
        placeFlags(player, env);

        // ===== Rarity badge =====
        placeRarityIcon(gui, dist);

        // Extra chrome
        putPlayerIconMain(player, 0, 3, "icons/bait-icon-full");

        placeDescriptionGemWithTooltip(player, model);

        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(gui));
    }

    // Enable/disable nav buttons by page
    private void placeNavIcons(Player p, int pageIndex, int pageCount) {
        boolean atFirst = pageIndex <= 0;
        boolean atLast  = pageIndex >= pageCount - 1;

        putPlayerIconHotbar(p, HOTBAR_PREV_1,   atFirst ? "icons/previous-1_empty"   : "icons/previous-1");
        putPlayerIconHotbar(p, HOTBAR_PREV_ALL, atFirst ? "icons/previous-all_empty" : "icons/previous-all");
        putPlayerIconHotbar(p, HOTBAR_NEXT_1,   atLast  ? "icons/next-1_empty"       : "icons/next-1");
        putPlayerIconHotbar(p, HOTBAR_NEXT_ALL, atLast  ? "icons/next-all_empty"     : "icons/next-all");

        setHotbarTooltip(p, 6, Component.text("Next"));
        setHotbarTooltip(p, 7, Component.text("Last"));
        setHotbarTooltip(p, 2, Component.text("Previous"));
        setHotbarTooltip(p, 1, Component.text("First"));
    }

    private void placeMenuIcon(Inventory gui, int pageIndex) {
        int slot = Math.min(6, pageIndex);        // same slot logic as before
        String tex = "icons/fish-menu_" + (pageIndex + 1); // 1-based page number
        putIcon(gui, slot, tex);
    }

    // ===== Time & moon =====
    private void placeTimeIconsForFish(Inventory gui, FishEnvironment env) {
        Set<FishTimeCycle> allowed = EnumSet.noneOf(FishTimeCycle.class);
        if (env == null || env.getEnvironmentTimes() == null || env.getEnvironmentTimes().isEmpty()) {
            allowed.addAll(EnumSet.allOf(FishTimeCycle.class));
        } else {
            env.getEnvironmentTimes().forEach((t, weight) -> {
                if (weight != null && weight > 0d) allowed.add(t);
            });
        }
        for (FishTimeCycle t : List.of(FishTimeCycle.DAWN, FishTimeCycle.DAY, FishTimeCycle.EVENING, FishTimeCycle.NIGHT)) {
            Integer slot = TIME_SLOTS.get(t);
            String base = TIME_ICON_SUFFIX.get(t);
            if (slot == null || base == null) continue;
            putIcon(gui, slot, allowed.contains(t) ? base : (base + "_empty"));
        }
    }

    private void placeMoonIconsForFish(Inventory gui, Player player, FishEnvironment env) {
        Set<FishMoonCycle> allowed = EnumSet.noneOf(FishMoonCycle.class);
        if (env == null || env.getEnvironmentMoons() == null || env.getEnvironmentMoons().isEmpty()) {
            allowed.addAll(EnumSet.allOf(FishMoonCycle.class));
        } else {
            env.getEnvironmentMoons().forEach((m, weight) -> {
                if (weight != null && weight > 0d) allowed.add(m);
            });
        }
        int i = 0;
        for (FishMoonCycle moon : MOON_ORDER) {
            String base = MOON_ICON_SUFFIX.get(moon);
            if (base == null) { i++; continue; }
            String suffix = allowed.contains(moon) ? base : (base + "_empty");

            if      (i == 0) putIcon(gui, 35, suffix);
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

    // ===== Environment group badges =====
    private void placeEnvironmentGroupsForFish(Player player, FishEnvironment env) {
        Set<String> fishBiomes = new HashSet<>();
        if (env != null && env.getEnvironmentBiomes() != null) {
            env.getEnvironmentBiomes().forEach((biome, weight) -> {
                if (weight != null && weight > 0d && biome != null) {
                    fishBiomes.add(biome.name());
                }
            });
        }
        for (EnvGroup g : ENV_GROUPS) {
            boolean found = !Collections.disjoint(fishBiomes, g.biomeNames);
            String suffix = g.iconBase + (found ? "" : "_empty");
            putPlayerIconMain(player, g.row, g.col, suffix);
        }
    }

    private void placeFlags(Player player, FishEnvironment env) {
        boolean rainReq = env != null && Boolean.TRUE.equals(env.getRainRequired());
        putPlayerIconMain(player, 0, 5, rainReq ? "icons/rainy-icon" : "icons/sunny-icon");
    }

    // ===== Utilities =====
    private int chooseMenuSlotFromModel(int modelNumber) {
        int pageIndex = Math.max(0, (modelNumber - 1) / PAGE_SIZE);
        return Math.min(6, pageIndex);
    }

    private static String modelNo3(int n) {
        return String.format("%03d", n);
    }

    private void applyFishTooltip(Inventory gui, int slot, FishModel fm) {
        if (fm == null) return;
        ItemStack stack = gui.getItem(slot);
        if (stack == null) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        // Unhide tooltip if supported (1.20.5+/1.21+); ignore if not available
        try { meta.getClass().getMethod("setHideTooltip", boolean.class).invoke(meta, false); }
        catch (Throwable ignored) {}

        meta.displayName(Component.text(fm.getName()));
        meta.lore(List.of(Component.text("No. " + modelNo3(fm.getModelNumber()))));

        stack.setItemMeta(meta);
        gui.setItem(slot, stack);
    }

    // ===== Icon helpers =====
    private void putIcon(Inventory inv, int slot, String modelSuffix) {
        GuiItemSlot.putImmovableIcon(inv, slot, Material.COD, Key.key(NS, modelSuffix), IMMOVABLE_KEY, false);
    }
    private void putPlayerIconMain(Player p, int row, int col, String modelSuffix) {
        GuiItemSlot.putImmovableIcon(p.getInventory(), GuiItemSlot.main(row, col),
                Material.COD, Key.key(NS, modelSuffix), IMMOVABLE_KEY, false);
    }
    private void putPlayerIconHotbar(Player p, int index, String modelSuffix) {
        GuiItemSlot.putImmovableIcon(p.getInventory(), GuiItemSlot.hotbar(index),
                Material.COD, Key.key(NS, modelSuffix), IMMOVABLE_KEY, false);
    }

    // ===== Rarity =====
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
    private void placeRarityIcon(Inventory gui, FishDistribution dist) {
        String suffix = iconSuffixForRarity(dist != null ? dist.getRarity() : null);
        putIcon(gui, 45, suffix);
    }

    private void placeDescriptionGemWithTooltip(Player player, FishModel model) {
        // Place the icon first (keeps your RP texture + immovable tag)
        putPlayerIconMain(player, 0, 4, "icons/description-gem-icon");

        int slot = GuiItemSlot.main(0, 4);
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack == null) return;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        // Unhide tooltip if supported (1.20.5+/1.21+)
        try { meta.getClass().getMethod("setHideTooltip", boolean.class).invoke(meta, false); }
        catch (Throwable ignored) {}

        String name = (model != null && model.getName() != null) ? model.getName() : "";
        String desc = (model != null && model.getDescription() != null) ? model.getDescription() : "";

        meta.displayName(Component.text(name));
        // Wrap description to up to 3 lines, ~32 chars per line (tweak if you want tighter/looser)
        meta.lore(wrapLore(desc, 32, 3));

        stack.setItemMeta(meta);
        player.getInventory().setItem(slot, stack);
    }

    private static List<Component> wrapLore(String text, int maxLen, int maxLines) {
        if (text == null || text.isBlank()) {
            return List.of(Component.text(""));
        }

        List<String> lines = new ArrayList<>(maxLines);
        StringBuilder curr = new StringBuilder();
        String[] words = text.trim().split("\\s+");

        for (int i = 0; i < words.length; i++) {
            String w = words[i];

            // hard-split very long words
            while (w.length() > maxLen) {
                if (lines.size() == maxLines - 1) {
                    lines.add((curr.length() > 0 ? curr + " " : "")
                            + w.substring(0, Math.max(0, maxLen - 1)) + "…");
                    return toComponents(lines);
                }
                lines.add(w.substring(0, maxLen));
                w = w.substring(maxLen);
            }

            if (curr.length() == 0) {
                curr.append(w);
            } else if (curr.length() + 1 + w.length() <= maxLen) {
                curr.append(' ').append(w);
            } else {
                lines.add(curr.toString());
                if (lines.size() == maxLines) {
                    String last = lines.remove(lines.size() - 1);
                    if (last.length() >= maxLen - 1) last = last.substring(0, maxLen - 1) + "…";
                    else last = last + "…";
                    lines.add(last);
                    return toComponents(lines);
                }
                curr.setLength(0);
                curr.append(w);
            }
        }

        if (curr.length() > 0 && lines.size() < maxLines) {
            lines.add(curr.toString());
        }

        return toComponents(lines);
    }

    private static List<Component> toComponents(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String s : lines) out.add(Component.text(s));
        return out;
    }

    private void setHotbarTooltip(Player p, int index, Component displayName) {
        int slot = GuiItemSlot.hotbar(index);
        ItemStack stack = p.getInventory().getItem(slot);
        if (stack == null) return;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        // Make sure tooltips aren’t hidden (reflective for 1.20.5+/1.21+; safe no-op otherwise)
        try {
            meta.getClass().getMethod("setHideTooltip", boolean.class).invoke(meta, false);
        } catch (Throwable ignored) {}

        meta.displayName(displayName);
        // optional: clear lore so only the title shows
        meta.lore(null);

        stack.setItemMeta(meta);
        p.getInventory().setItem(slot, stack);
    }
}
