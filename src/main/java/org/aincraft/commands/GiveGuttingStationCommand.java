// org/aincraft/commands/GiveGuttingStationCommand.java
package org.aincraft.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.aincraft.ItemService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class GiveGuttingStationCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        ItemStack item = new ItemStack(Material.STONE);
        ItemService.itemService().write(item, "longhardfish:block/gutting_station");

        ItemMeta meta = item.getItemMeta();
        NamespacedKey modelKey = NamespacedKey.fromString("longhardfish:block/gutting_station");
        if (modelKey != null) meta.setItemModel(modelKey);
        meta.displayName(
                Component.text("Gutting Station")
                        .decoration(TextDecoration.ITALIC, false)
        );
        item.setItemMeta(meta);

        player.getInventory().addItem(item);
        player.sendMessage("§aGave Gutting Station.");
        return true;
    }

    // simple tab complete: suggest the subcommand
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        ArrayList<String> out = new ArrayList<>();
        if (args.length == 1) out.add("gutting_station");
        return out;
    }
}
