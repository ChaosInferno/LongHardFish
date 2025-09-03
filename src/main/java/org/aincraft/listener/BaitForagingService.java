package org.aincraft.listener;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Unified bait foraging listener:
 * - Register rules for different sources (hoe-tilling, breaking certain blocks, etc)
 * - Each rule defines chance, allowed blocks/tools, and bait item supplier
 */
public final class BaitForagingService implements Listener {

    // What kind of event produced the context?
    public enum SourceType { HOE_TILL, BLOCK_BREAK }

    // Minimal context the rules can use to match and decide drops
    public static final class Context {
        public final SourceType type;
        public final Player player;
        public final Block block;          // the affected block (current state)
        public final ItemStack tool;
        public final Material beforeType;  // NEW
        public final Material afterType;   // NEW

        Context(SourceType type, Player player, Block block, ItemStack tool,
                Material beforeType, Material afterType) {
            this.type = type; this.player = player; this.block = block; this.tool = tool;
            this.beforeType = beforeType; this.afterType = afterType;
        }
    }

    /** A single drop rule. */
    public static final class Rule {
        // Basic filters
        public final SourceType type;
        public final Set<Material> blockWhitelist; // empty = accept any block
        public final Set<Material> toolWhitelist;  // empty = accept any tool

        // Optional extra predicate (e.g., “air above”, “biome is swamp”, etc.)
        public final Predicate<Context> extraMatch;

        // Drop config
        public final double chance;  // 0..1
        public final int amountMin;  // inclusive
        public final int amountMax;  // inclusive
        public final Supplier<ItemStack> baitSupplier;

        private Rule(Builder b) {
            this.type = b.type;
            this.blockWhitelist = Set.copyOf(b.blockWhitelist);
            this.toolWhitelist  = Set.copyOf(b.toolWhitelist);
            this.extraMatch     = b.extraMatch;
            this.chance         = b.chance;
            this.amountMin      = b.amountMin;
            this.amountMax      = b.amountMax;
            this.baitSupplier   = b.baitSupplier;
        }

        public static final class Builder {
            private final SourceType type;
            private final Supplier<ItemStack> baitSupplier;
            private final Set<Material> blockWhitelist = new LinkedHashSet<>();
            private final Set<Material> toolWhitelist  = new LinkedHashSet<>();
            private Predicate<Context> extraMatch = c -> true;
            private double chance = 0.50;
            private int amountMin = 1;
            private int amountMax = 1;

            public Builder(SourceType type, Supplier<ItemStack> baitSupplier) {
                this.type = Objects.requireNonNull(type);
                this.baitSupplier = Objects.requireNonNull(baitSupplier);
            }
            public Builder blocks(Material... blocks) {
                if (blocks != null) Collections.addAll(blockWhitelist, blocks);
                return this;
            }
            public Builder tools(Material... tools) {
                if (tools != null) Collections.addAll(toolWhitelist, tools);
                return this;
            }
            public Builder extra(Predicate<Context> predicate) {
                if (predicate != null) this.extraMatch = predicate;
                return this;
            }
            public Builder chance(double p) {
                this.chance = Math.max(0.0, Math.min(1.0, p));
                return this;
            }
            public Builder amount(int min, int max) {
                this.amountMin = Math.max(1, min);
                this.amountMax = Math.max(this.amountMin, max);
                return this;
            }
            public Rule build() { return new Rule(this); }
        }
    }

    private final Plugin plugin;
    private final List<Rule> rules = new ArrayList<>();

    public BaitForagingService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Register a rule. Call this during onEnable. */
    public void register(Rule rule) {
        this.rules.add(Objects.requireNonNull(rule));
    }

    // -------------------- Events --------------------

    // Hoe till detection: right-click block with a hoe; we don't cancel vanilla, just drop extra
    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onHoeTill(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        final Player p = e.getPlayer();
        final ItemStack tool = p.getInventory().getItemInMainHand();
        if (tool == null) return;

        final Material hoe = tool.getType();
        if (!isAnyHoe(hoe)) return;

        final Block block = e.getClickedBlock();
        final Material before = block.getType();

        // Vanilla till preconditions:
        // 1) click the TOP face
        if (e.getBlockFace() != org.bukkit.block.BlockFace.UP) return;
        // 2) air (or empty) above the block
        final Material above = block.getRelative(0, 1, 0).getType();
        if (!(above.isAir() || above == Material.CAVE_AIR || above == Material.VOID_AIR)) return;
        // 3) block is one of the tillable variants
        final boolean isTillable =
                before == Material.DIRT
                        || before == Material.GRASS_BLOCK
                        || before == Material.COARSE_DIRT
                        || before == Material.ROOTED_DIRT;
        if (!isTillable) return;

        // At this point, a till will occur → apply HOE_TILL rules NOW using BEFORE material
        applyMatchingRules(new Context(
                SourceType.HOE_TILL, p, block, tool, before, before
        ));
    }



    // Breaking leaves / dead bush / lilypad etc.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e) {
        final Player p = e.getPlayer();
        final ItemStack tool = p.getInventory().getItemInMainHand();
        final Block block = e.getBlock();
        final Material before = block.getType();

        applyMatchingRules(new Context(SourceType.BLOCK_BREAK, p, block, tool, before, before));
    }

    // -------------------- Core matcher --------------------

    private void applyMatchingRules(Context ctx) {

        for (Rule r : rules) {
            if (r.type != ctx.type) continue;

            // Use BEFORE type for whitelist when tilling; current block may already be farmland/dirt
            Material matForWhitelist =
                    (ctx.type == SourceType.HOE_TILL) ? ctx.beforeType : ctx.beforeType;

            if (!r.blockWhitelist.isEmpty() && !r.blockWhitelist.contains(matForWhitelist)) continue;

            Material toolType = ctx.tool != null ? ctx.tool.getType() : null;
            if (!r.toolWhitelist.isEmpty() && (toolType == null || !r.toolWhitelist.contains(toolType))) continue;

            if (!r.extraMatch.test(ctx)) continue;

            if (ThreadLocalRandom.current().nextDouble() <= r.chance) {
                int amount = (r.amountMin == r.amountMax)
                        ? r.amountMin
                        : ThreadLocalRandom.current().nextInt(r.amountMin, r.amountMax + 1);
                ItemStack stack = r.baitSupplier.get();
                if (stack == null) continue;
                stack.setAmount(Math.max(1, amount));
                ctx.player.getWorld().dropItemNaturally(ctx.block.getLocation().add(0.5, 0.8, 0.5), stack);
            }
        }
    }


    // -------------------- Convenience helpers --------------------

    public static boolean isAnyHoe(Material m) {
        return m == Material.WOODEN_HOE || m == Material.STONE_HOE || m == Material.IRON_HOE
                || m == Material.GOLDEN_HOE || m == Material.DIAMOND_HOE || m == Material.NETHERITE_HOE;
    }
}

