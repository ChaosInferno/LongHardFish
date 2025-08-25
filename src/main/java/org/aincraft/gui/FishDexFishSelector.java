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
import org.aincraft.sfx.FishDexSFX;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
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
    public interface TierLookup { Integer get(NamespacedKey fishId); }
    public enum Progress { UNSEEN, SEEN, CAUGHT }
    public interface ProgressLookup { Progress get(java.util.UUID playerId, NamespacedKey fishId); }

    /** Holder for the GUI so we can recognize it and store page mapping + metadata. */
    public static final class DexHolder implements InventoryHolder {
        private Inventory inv;
        public Map<Integer, NamespacedKey> slotToFish = new HashMap<>();
        public NamespacedKey selected;

        // Paging state for listener nav
        public List<NamespacedKey> ordered;
        public int pageIndex = 0;     // 0-based
        public int pageCount = 1;     // total pages (>=1)
        public boolean pagingEnabled = false;

        @Override public Inventory getInventory() { return inv; }
    }

    /** Create WITH page-fill (tier optional). */
    public static FishDexFishSelector createWithPaging(
            JavaPlugin plugin,
            NamespacedKey immovableKey,
            BiConsumer<Player, Inventory> maskApplier,
            EnvLookup envLookup,
            ModelLookup modelLookup,
            DistributionLookup distributionLookup,
            Supplier<Collection<NamespacedKey>> allFishIds,
            TierLookup tierLookup,
            ProgressLookup progressLookup
    ) {
        return new FishDexFishSelector(plugin, immovableKey, maskApplier, envLookup, modelLookup, distributionLookup, allFishIds, tierLookup, progressLookup);
    }

    private final JavaPlugin plugin;
    private final NamespacedKey IMMOVABLE_KEY;
    private final BiConsumer<Player, Inventory> maskApplier;
    private final EnvLookup envLookup;
    private final ModelLookup modelLookup;
    private final DistributionLookup distributionLookup;
    private final Supplier<Collection<NamespacedKey>> allFishIds; // enables page-fill
    private final TierLookup tierLookup;
    private final ProgressLookup progressLookup;
    private static final String NAV_EMPTY_TEX = "icons/empty";

    private static final String NS = "longhardfish";
    private static final int PAGE_SIZE = 35;

    // Expose hotbar indices for listener
    public static final int HOTBAR_PREV_ALL = 1;  // "previous-all"
    public static final int HOTBAR_PREV_1   = 2;  // "previous-1"
    public static final int HOTBAR_NEXT_1   = 6;  // "next-1"
    public static final int HOTBAR_NEXT_ALL = 7;  // "next-all"

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
            Supplier<Collection<NamespacedKey>> allFishIds,
            TierLookup tierLookup,
            ProgressLookup progressLookup
    ) {
        this.plugin = plugin;
        this.IMMOVABLE_KEY = immovableKey;
        this.maskApplier = maskApplier;
        this.envLookup = envLookup;
        this.modelLookup = modelLookup;
        this.distributionLookup = distributionLookup;
        this.allFishIds = allFishIds;
        this.tierLookup = tierLookup;
        this.progressLookup = progressLookup;
    }

    /** Expose plugin so listener can schedule reopen on click. */
    public JavaPlugin plugin() { return plugin; }

    /** Open the GUI for a specific fish. */
    public void open(Player player, NamespacedKey fishId, boolean playOpenSound) {
        DexHolder holder = new DexHolder();
        holder.selected = fishId;

        Inventory gui = Bukkit.createInventory(
                holder,
                54,
                text("\ue001\ua020\ue002\ua021").font(key(NS, "interface")).color(TextColor.color(0xFFFFFF))
        );
        holder.inv = gui;

        if (maskApplier != null) maskApplier.accept(player, gui);

        // Background/offset art
        putIcon(gui, 9, fishId.getKey() + "-offset");
        hideTooltipTop(gui, 9);

        // Resolve data
        FishEnvironment env = (envLookup != null) ? envLookup.get(fishId) : null;
        FishModel model       = (modelLookup != null) ? modelLookup.get(fishId) : null;
        FishDistribution dist = (distributionLookup != null) ? distributionLookup.get(fishId) : null;

        // ===== Fish list & paging =====
        holder.slotToFish.clear();
        List<NamespacedKey> sorted = null;
        int pageIndex;

        if (allFishIds != null) {
            sorted = new ArrayList<>(allFishIds.get());
            sorted.sort(Comparator.comparingInt(id -> {
                FishModel fm = modelLookup.get(id);
                return (fm != null) ? fm.getModelNumber() : Integer.MAX_VALUE;
            }));
            holder.ordered = sorted;
            holder.pagingEnabled = true;

            int modelNumber = (model != null) ? model.getModelNumber() : 1;
            pageIndex = Math.max(0, (modelNumber - 1) / PAGE_SIZE);
            int start = pageIndex * PAGE_SIZE;

            for (int i = 0; i < FISH_GRID_SLOTS.length && (start + i) < sorted.size(); i++) {
                NamespacedKey idOnPage = sorted.get(start + i);
                FishModel fm = modelLookup.get(idOnPage);

                String baseTex = idOnPage.getKey(); // your per-fish model key
                if (baseTex != null && !baseTex.isEmpty()) {
                    int guiSlot = FISH_GRID_SLOTS[i];

                    // NEW: pick texture by progress
                    String texToUse = textureForProgress(player, idOnPage, baseTex);

                    putIcon(gui, guiSlot, texToUse);
                    holder.slotToFish.put(guiSlot, idOnPage);
                    applyFishTooltip(gui, guiSlot, fm);
                }
            }
            // Page indicator (uses fish-menu_(pageNumber))
            int menuSlot = Math.min(6, pageIndex);
            putIcon(gui, menuSlot, "icons/fish-menu_" + (pageIndex + 1));
            hideTooltipTop(gui, menuSlot);

        } else {
            // No paging (fallback)
            String baseTex = fishId.getKey();
            if (baseTex != null && !baseTex.isEmpty()) {
                int slot = FISH_GRID_SLOTS[0];

                // NEW: pick texture by progress
                String texToUse = textureForProgress(player, fishId, baseTex);

                putIcon(gui, slot, texToUse);
                holder.slotToFish.put(slot, fishId);
                applyFishTooltip(gui, slot, model);
            }
            pageIndex = (model != null) ? Math.max(0, (model.getModelNumber() - 1) / PAGE_SIZE) : 0;
            putIcon(gui, Math.min(6, pageIndex), "icons/fish-menu_" + (pageIndex + 1));
        }

        // Compute pageCount if paging
        int pageCount = 1;
        if (sorted != null && !sorted.isEmpty()) {
            pageCount = (sorted.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        }
        holder.pageIndex = pageIndex;
        holder.pageCount = pageCount;

        // ===== Time & Moon =====
        placeTimeIconsForFish(gui, env);
        placeMoonIconsForFish(gui, player, env);

        // ===== Environment groups + flags (with tooltips) =====
        placeEnvironmentGroupsForFish(player, env);
        placeFlags(player, env);

        // ===== Rarity (slot 45) =====
        placeRarityIcon(gui, dist);

        // ===== Tier badge (player main(1,0)) =====
        placeTierIcon(player, fishId);

        // ===== Description gem (tooltip with 3 lines) =====
        placeDescriptionGem(player, model);

        // ===== Nav buttons (textures unchanged; tooltips restored; disable with _empty on edges) =====
        placeNavButtons(player, pageIndex, pageCount);

        Bukkit.getScheduler().runTask(plugin, () -> {
            player.openInventory(gui);
            if (playOpenSound) FishDexSFX.playOpen(player);
        });
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
            hideTooltipTop(gui, slot);
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

            if      (i == 0) { putIcon(gui, 35, suffix);                     hideTooltipTop(gui, 35); }
            else if (i == 1) { putIcon(gui, 44, suffix);                     hideTooltipTop(gui, 44); }
            else if (i == 2) { putPlayerIconHotbar(player, 8, suffix);      hideTooltipHotbar(player, 8); }
            else if (i == 3) { putPlayerIconMain(player, 0, 7, suffix);  hideTooltipMain(player, 0, 7); }
            else if (i == 4) { putIcon(gui, 53, suffix);                     hideTooltipTop(gui, 53); }
            else if (i == 5) { putPlayerIconMain(player, 1, 8, suffix);  hideTooltipMain(player, 1, 8); }
            else if (i == 6) { putPlayerIconMain(player, 0, 6, suffix);  hideTooltipMain(player, 0, 6); }
            else if (i == 7) { putPlayerIconMain(player, 0, 8, suffix);  hideTooltipMain(player, 0, 8); }
            i++;
        }
    }

    // ===== Environment groups (icons + tooltips) =====
    private void placeEnvironmentGroupsForFish(Player player, FishEnvironment env) {
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
            putPlayerIconMain(player, g.row, g.col, suffix);

            if (found) {
                // Intersect and humanize the biome names for the lore (no dashes, no group title)
                List<String> lines = g.biomeNames.stream()
                        .filter(fishBiomes::contains)
                        .map(this::humanizeBiome)  // "WARM_OCEAN" -> "Warm Ocean"
                        .toList();

                setMainSlotTooltip(player, g.row, g.col, "Can be found in:", toComponents(lines));
            } else {
                // Friendly group name for the "not found" message
                String groupLabel = friendlyGroupName(g.iconBase); // "env-warm-oceans" -> "warm oceans"
                setMainSlotTooltip(player, g.row, g.col, "Not found in " + groupLabel, null);
            }
        }
    }

    private String humanizeBiome(String biomeUpper) {
        String[] parts = biomeUpper.toLowerCase(Locale.ENGLISH).split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(parts[i].charAt(0)))
                    .append(parts[i].substring(1));
            if (i + 1 < parts.length) sb.append(' ');
        }
        return sb.toString();
    }

    private String friendlyGroupName(String iconBase) {
        // iconBase like "icons/env-warm-oceans"
        String last = iconBase.substring(iconBase.lastIndexOf('/') + 1); // "env-warm-oceans"
        if (last.startsWith("env-")) last = last.substring(4);
        return last.replace('-', ' ');
    }


    private static String prettyEnvGroupName(String iconBase) {
        // iconBase like "icons/env-warm-oceans"
        String s = iconBase;
        if (s.startsWith("icons/env-")) s = s.substring("icons/env-".length());
        return s.replace('-', ' ');
    }

    // ===== Flags (rain required) + tooltip =====
    private void placeFlags(Player player, FishEnvironment env) {
        boolean rainReq = env != null && Boolean.TRUE.equals(env.getRainRequired());
        String suffix = rainReq ? "icons/rainy-icon" : "icons/sunny-icon";
        putPlayerIconMain(player, 0, 5, suffix);

        // Tooltip for the flag
        setMainSlotTooltip(
                player, 0, 5,
                rainReq ? "Rain required" : "Can be found without rain",
                null
        );
    }

    // ===== Tier badge =====
    private void placeTierIcon(Player player, NamespacedKey fishId) {
        Integer tier = (tierLookup != null) ? tierLookup.get(fishId) : null;
        String suffix = iconSuffixForTier(tier);
        putPlayerIconMain(player, 1, 0, suffix);
        hideTooltipMain(player, 1, 0);
        // Optional tooltip:
        // setMainSlotTooltip(player, 1, 0, "Tier " + ((tier == null) ? 1 : tier), null);
    }
    private static String iconSuffixForTier(Integer tier) {
        if (tier == null) return "icons/1-star";
        switch (tier) {
            case 4:  return "icons/4-star";
            case 3:  return "icons/3-star";
            case 2:  return "icons/2-star";
            case 1:
            default: return "icons/1-star";
        }
    }

    // ===== Description gem with 3 wrapped lines =====
    // ===== Description gem with wrapped description; name as display title =====
    private void placeDescriptionGem(Player player, FishModel model) {
        // keep the same icon
        putPlayerIconMain(player, 0, 4, "icons/description-gem-icon");

        String name = (model != null && model.getName() != null) ? model.getName() : "Unknown Fish";
        String desc = (model != null && model.getDescription() != null) ? model.getDescription() : "";

        // 3 lines, ~35 chars per line (tweak if needed)
        List<String> wrapped = wrapToMaxLines(desc, 3, 40);

        // Set the display name to the fish name, and the lore to the wrapped description lines
        setMainSlotTooltip(player, 0, 4, name, toComponents(wrapped));
    }


    // ===== Nav buttons (set texture + tooltip; disable with _empty on edges) =====
    private void placeNavButtons(Player player, int pageIndex, int pageCount) {
        boolean onFirst = pageIndex <= 0;
        boolean onLast  = pageIndex >= pageCount - 1;

        // We always place the same invisible texture so the ghosted hotbar shows nothing,
        // but we still set the tooltip text so users get guidance when hovering.
        putPlayerIconHotbar(player, HOTBAR_PREV_ALL, NAV_EMPTY_TEX);
        setHotbarTooltip(player, HOTBAR_PREV_ALL, "First");

        putPlayerIconHotbar(player, HOTBAR_PREV_1, NAV_EMPTY_TEX);
        setHotbarTooltip(player, HOTBAR_PREV_1, "Previous");

        putPlayerIconHotbar(player, HOTBAR_NEXT_1, NAV_EMPTY_TEX);
        setHotbarTooltip(player, HOTBAR_NEXT_1, "Next");

        putPlayerIconHotbar(player, HOTBAR_NEXT_ALL, NAV_EMPTY_TEX);
        setHotbarTooltip(player, HOTBAR_NEXT_ALL, "Last");
    }

    // ===== Utilities =====
    private int chooseMenuSlotFromModel(int modelNumber) {
        int pageIndex = Math.max(0, (modelNumber - 1) / PAGE_SIZE);
        return Math.min(6, pageIndex);
    }

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
        hideTooltipTop(gui, 45);
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

        meta.displayName(Component.text(fm.getName()));
        meta.lore(List.of(Component.text("No. " + modelNo3(fm.getModelNumber()))));

        stack.setItemMeta(meta);
        gui.setItem(slot, stack);
    }

    private void setHotbarTooltip(Player player, int hotbarIndex, String title) {
        int idx = GuiItemSlot.hotbar(hotbarIndex);
        ItemStack stack = player.getInventory().getItem(idx);
        if (stack == null) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        meta.displayName(text(title));
        meta.lore(null);

        stack.setItemMeta(meta);
        player.getInventory().setItem(idx, stack);
    }

    private static List<Component> toComponents(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String s : lines) out.add(text(s));
        return out;
    }

    /** Very basic wrapper: split on spaces to ~N chars per line, limit to maxLines. */
    private static List<String> wrapToMaxLines(String s, int maxLines, int approxWidth) {
        if (s == null || s.isEmpty()) return List.of();
        String[] words = s.split("\\s+");
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            if (cur.length() == 0) {
                cur.append(w);
            } else if (cur.length() + 1 + w.length() <= approxWidth) {
                cur.append(" ").append(w);
            } else {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(w);
                if (lines.size() >= maxLines - 1) break;
            }
        }
        if (cur.length() > 0 && lines.size() < maxLines) lines.add(cur.toString());
        return lines;
    }

    public void openPage(Player player, int targetPageIndex) {
        if (allFishIds == null) return; // paging not enabled

        // Build sorted list the same way as in open(...)
        List<NamespacedKey> sorted = new ArrayList<>(allFishIds.get());
        sorted.sort(Comparator.comparingInt(id -> {
            FishModel fm = modelLookup.get(id);
            return (fm != null) ? fm.getModelNumber() : Integer.MAX_VALUE;
        }));

        if (sorted.isEmpty()) return;

        int pageCount = (sorted.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int clamped   = Math.max(0, Math.min(targetPageIndex, pageCount - 1));
        int start     = clamped * PAGE_SIZE;

        NamespacedKey firstOnPage = sorted.get(Math.min(start, sorted.size() - 1));
        open(player, firstOnPage, /*playOpenSound=*/false);
    }

    private void setMainSlotTooltip(Player p, int row, int col, String title, List<Component> lines) {
        int slot = GuiItemSlot.main(row, col);
        ItemStack stack = p.getInventory().getItem(slot);
        if (stack == null) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        try { meta.removeItemFlags(ItemFlag.HIDE_ATTRIBUTES); } catch (Throwable ignored) {}
        try { meta.removeItemFlags(ItemFlag.HIDE_UNBREAKABLE); } catch (Throwable ignored) {}

        if (title != null) {
            meta.displayName(Component.text(title));
        }
        meta.lore((lines != null && !lines.isEmpty()) ? lines : null);

        stack.setItemMeta(meta);
        p.getInventory().setItem(slot, stack);
    }

    // -- Tooltip hiders -----------------------------------------------------------
    private static void trySetHideTooltip(ItemMeta meta, boolean hide) {
        // Paper 1.20.5+/1.21+: meta.setHideTooltip(boolean)
        try {
            meta.getClass().getMethod("setHideTooltip", boolean.class).invoke(meta, hide);
        } catch (Throwable ignored) {
            // Fallback: blank name + no lore + hide extra lines
            meta.displayName(Component.empty());
            meta.lore(null);
            try { meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); } catch (Throwable __) {}
            try { meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE); } catch (Throwable __) {}
        }
    }

    private void hideTooltipTop(Inventory gui, int slot) {
        ItemStack s = gui.getItem(slot);
        if (s == null) return;
        ItemMeta m = s.getItemMeta();
        if (m == null) return;
        m.setHideTooltip(true);      // <-- real “no tooltip”
        m.displayName(null);         // ensure no custom name
        m.lore(null);                // ensure no lore
        s.setItemMeta(m);
        gui.setItem(slot, s);
    }

    private void hideTooltipMain(Player p, int row, int col) {
        int slot = GuiItemSlot.main(row, col);
        ItemStack s = p.getInventory().getItem(slot);
        if (s == null) return;
        ItemMeta m = s.getItemMeta();
        if (m == null) return;
        m.setHideTooltip(true);
        m.displayName(null);
        m.lore(null);
        s.setItemMeta(m);
        p.getInventory().setItem(slot, s);
    }

    private void hideTooltipHotbar(Player p, int index) {
        int slot = GuiItemSlot.hotbar(index);
        ItemStack s = p.getInventory().getItem(slot);
        if (s == null) return;
        ItemMeta m = s.getItemMeta();
        if (m == null) return;
        m.setHideTooltip(true);
        m.displayName(null);
        m.lore(null);
        s.setItemMeta(m);
        p.getInventory().setItem(slot, s);
    }

    private String textureForProgress(Player player, NamespacedKey fishId, String baseTex) {
        if (progressLookup == null || player == null || fishId == null) return baseTex;

        Progress prog = progressLookup.get(player.getUniqueId(), fishId);
        if (prog == null) return baseTex;

        switch (prog) {
            case UNSEEN:
                // single generic model named "unknown" in the longhardfish namespace
                return "unknown";
            case SEEN:
                // per-fish “seen but not caught” variant
                return baseTex + "_UD";
            case CAUGHT:
            default:
                return baseTex;
        }
    }

}
