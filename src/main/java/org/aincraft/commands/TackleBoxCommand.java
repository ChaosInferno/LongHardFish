package org.aincraft.commands;

import org.aincraft.gui.TackleBoxGui;
import org.aincraft.ingame_items.TackleBoxItem;
import org.aincraft.listener.TackleBoxListener;
import org.aincraft.listener.TackleBoxService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public final class TackleBoxCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final TackleBoxService service;

    public TackleBoxCommand(JavaPlugin plugin, TackleBoxService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        // Prefer main hand; if not a tacklebox, try offhand
        if (new TackleBoxItem(plugin).isTackleBox(p.getInventory().getItemInMainHand())) {
            service.openFromHand(p, EquipmentSlot.HAND);
        } else if (new TackleBoxItem(plugin).isTackleBox(p.getInventory().getItemInOffHand())) {
            service.openFromHand(p, EquipmentSlot.OFF_HAND);
        } else {
            p.sendMessage("§7Hold your TackleBox to open it.");
        }
        return true;
    }
}

