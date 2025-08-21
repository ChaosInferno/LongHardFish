package org.aincraft.commands;

import org.aincraft.gui.FishDexFishSelector;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FishDexCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final FishDexFishSelector selector;
    private final Set<String> fishIds; // plain ids like "glider-bananafish"

    public FishDexCommand(JavaPlugin plugin,
                          FishDexFishSelector selector,
                          Set<NamespacedKey> availableFish) {
        this.plugin = plugin;
        this.selector = selector;
        this.fishIds = availableFish.stream().map(NamespacedKey::getKey).collect(Collectors.toSet());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true; // don't print usage
        }
        if (args.length < 1) {
            sender.sendMessage("Usage: /" + label + " <fish-id>");
            return true; // don't print usage again
        }

        String id = args[0];
        if (!fishIds.contains(id)) {
            sender.sendMessage("Unknown fish id: " + id);
            return true;
        }

        NamespacedKey fishKey = new NamespacedKey(plugin, id);
        selector.open(p, fishKey);
        return true; // important: prevent Bukkit from showing usage
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String id : fishIds) {
                if (id.toLowerCase().startsWith(prefix)) out.add(id);
            }
            return out;
        }
        return List.of();
    }
}