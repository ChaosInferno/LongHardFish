package org.aincraft.listener;

import org.aincraft.items.BaitRegistry;
import org.aincraft.service.NaturalTrackerService;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class ForageTables {
    private ForageTables() {}

    /** 25%: drop exactly one bug; if drop occurs, pick grubb or tick 50/50. */
    public static void registerHoeTillBugTable(Plugin plugin, BaitForagingService service) {
        service.register(
                new BaitForagingService.Rule.Builder(BaitForagingService.SourceType.HOE_TILL,
                        () -> {
                            // choose one of the two uniformly
                            boolean pickGrubb = ThreadLocalRandom.current().nextBoolean();
                            var id = pickGrubb ? "grubb" : "tick";
                            return BaitRegistry.create(id, 1);
                        })
                        .tools(Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
                                Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE)
                        .blocks(Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.ROOTED_DIRT)
                        // you already check air-above + top face in the event;
                        // if you prefer to keep it inside the rule instead, uncomment the next line:
                        // .extra(ctx -> ctx.block.getRelative(0,1,0).getType().isAir())
                        .chance(0.25)   // 50% chance to drop
                        .amount(1, 1)   // exactly one item
                        .build()
        );
    }

    /** 25% chance to drop exactly 1 of {wasp, spiderling} when breaking any leaves. */
    public static void registerLeavesBugs(Plugin plugin, BaitForagingService service, NaturalTrackerService natural) {
        Set<Material> leaves = Set.of(
                Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
                Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
                Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES, Material.PALE_OAK_LEAVES,
                Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES
        );

        service.register(
                new BaitForagingService.Rule.Builder(BaitForagingService.SourceType.BLOCK_BREAK,
                        () -> {
                            String id = ThreadLocalRandom.current().nextBoolean() ? "wasp" : "spiderling";
                            return BaitRegistry.create(id, 1);
                        })
                        .blocks(leaves.toArray(Material[]::new))
                        .chance(0.25)
                        .amount(1, 1)
                        .extra(ctx -> natural.isNatural(ctx.block) && !hasSilkTouch(ctx.tool) && !isShears(ctx.tool))
                        .build()
        );
    }

    /** Lily pads → 30% chance 1x dragonfly, only if natural. */
    public static void registerLilyDragonfly(Plugin plugin, BaitForagingService service, NaturalTrackerService natural) {
        service.register(
                new BaitForagingService.Rule.Builder(BaitForagingService.SourceType.BLOCK_BREAK,
                        () -> BaitRegistry.create("dragonfly", 1))
                        .blocks(Material.LILY_PAD)
                        .chance(0.30)
                        .amount(1, 1)
                        .extra(ctx -> natural.isNatural(ctx.block))
                        .build()
        );
    }

    /** Fern / large fern → 25% chance 1x rhino_beetle, only if natural. */
    public static void registerFernRhinoBeetle(Plugin plugin, BaitForagingService service, NaturalTrackerService natural) {
        service.register(
                new BaitForagingService.Rule.Builder(BaitForagingService.SourceType.BLOCK_BREAK,
                        () -> BaitRegistry.create("rhino_beetle", 1))
                        .blocks(Material.FERN, Material.LARGE_FERN)
                        .chance(0.25)
                        .amount(1, 1)
                        .extra(ctx -> natural.isNatural(ctx.block))
                        .build()
        );
    }

    /** Dead bush → 25% chance 1x scarab, only if natural and no Silk Touch. */
    public static void registerDeadBushScarab(Plugin plugin, BaitForagingService service, NaturalTrackerService natural) {
        service.register(
                new BaitForagingService.Rule.Builder(BaitForagingService.SourceType.BLOCK_BREAK,
                        () -> BaitRegistry.create("scarab", 1))
                        .blocks(Material.DEAD_BUSH)
                        .chance(0.25)
                        .amount(1, 1)
                        .extra(ctx -> natural.isNatural(ctx.block) && !hasSilkTouch(ctx.tool))
                        .build()
        );
    }

    public static boolean hasSilkTouch(ItemStack tool) {
        if (tool == null) return false;
        return tool.getEnchantments().containsKey(Enchantment.SILK_TOUCH);
    }

    public static boolean isShears(ItemStack tool) {
        return tool != null && tool.getType() == Material.SHEARS;
    }
}

