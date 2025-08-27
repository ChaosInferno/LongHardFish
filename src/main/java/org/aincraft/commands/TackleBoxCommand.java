package org.aincraft.commands;

import org.aincraft.gui.TackleBoxGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class TackleBoxCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public TackleBoxCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        // Open with no mask (pass a BiConsumer if you need pre-fill logic later)
        TackleBoxGui.open(plugin, p, null);
        return true;
    }
}

