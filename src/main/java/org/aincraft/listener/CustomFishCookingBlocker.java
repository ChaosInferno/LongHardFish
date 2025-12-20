package org.aincraft.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.items.FishKeys;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listener that prevents custom fish from being cooked in furnaces and campfires.
 * Vanilla fish are unaffected by this listener.
 */
public final class CustomFishCookingBlocker implements Listener {

    private final JavaPlugin plugin;

    public CustomFishCookingBlocker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if the given ItemStack is a custom fish.
     *
     * @param stack The ItemStack to check
     * @return true if the item is a custom fish, false otherwise
     */
    private boolean isCustomFish(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        String fishKey = FishKeys.getFishKey(plugin, stack);
        return fishKey != null && !fishKey.isBlank();
    }

    /**
     * Prevents fuel from being consumed when custom fish are in the furnace.
     * Uses HIGHEST priority to run after other plugins.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        // Get the furnace block state
        Block furnaceBlock = event.getBlock();
        if (!(furnaceBlock.getState() instanceof Furnace furnace)) return;

        // Get the item being cooked in the furnace
        FurnaceInventory inventory = furnace.getInventory();
        ItemStack source = inventory.getSmelting();

        if (!isCustomFish(source)) return;

        // Cancel the fuel burn to prevent fuel consumption
        event.setCancelled(true);
    }

    /**
     * Prevents custom fish from being smelted in furnaces.
     * Uses HIGHEST priority to run after other plugins.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        ItemStack source = event.getSource();

        if (!isCustomFish(source)) return;

        // Cancel the smelting completion
        event.setCancelled(true);
    }

    /**
     * Prevents custom fish from being cooked on any block (campfires, smokers, blast furnaces, etc.).
     * Uses HIGHEST priority to run after other plugins.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockCook(BlockCookEvent event) {
        ItemStack source = event.getSource();

        if (!isCustomFish(source)) return;

        // Cancel any block cooking
        event.setCancelled(true);
    }

    /**
     * Prevents players from placing custom fish on campfires via right-click.
     * Provides immediate feedback to players.
     * Uses HIGHEST priority to ensure we can intercept before vanilla logic.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteractCampfire(PlayerInteractEvent event) {
        // Only handle right-clicks on blocks
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK) return;

        // Check if the clicked block is a campfire
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        Material blockType = clicked.getType();
        if (blockType != Material.CAMPFIRE && blockType != Material.SOUL_CAMPFIRE) return;

        // Get the item in player's hand
        ItemStack item = event.getItem();
        if (!isCustomFish(item)) return;

        // Block the interaction
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        // Provide player feedback
        Player player = event.getPlayer();
        player.sendActionBar(Component.text(
            "This fish cannot be cooked on a campfire!",
            NamedTextColor.RED
        ));
    }
}
