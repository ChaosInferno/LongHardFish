package org.aincraft.commands;

import org.aincraft.gui.FishDexFishSelector;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class FishDexCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final FishDexFishSelector selector;
    private final Set<NamespacedKey> validIds; // ids parsed from fish.yml

    public FishDexCommand(JavaPlugin plugin,
                          FishDexFishSelector selector,
                          Set<NamespacedKey> validIds) {
        this.plugin = plugin;
        this.selector = selector;
        this.validIds = validIds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true; // prevent Bukkit usage echo
        }
        if (args.length < 1) {
            sender.sendMessage("Usage: /" + label + " <fish-id>");
            return true;
        }

        // Accept either "glider-bananafish" or "namespace:glider-bananafish"
        NamespacedKey id = NamespacedKey.fromString(args[0], plugin);

        // normalize to plugin namespace if user typed just the key
        if (id == null) {
            sender.sendMessage("Invalid fish id format.");
            return true;
        }

        // make sure it exists in our parsed set
        if (!validIds.contains(id)) {
            // try a last-ditch: if user typed raw key without namespace, check that variant
            NamespacedKey pluginScoped = new NamespacedKey(plugin, id.getKey());
            if (!validIds.contains(pluginScoped)) {
                sender.sendMessage("Unknown fish id: " + args[0]);
                // optional: show a few suggestions
                String suggestions = validIds.stream()
                        .map(NamespacedKey::getKey)
                        .filter(k -> k.toLowerCase(Locale.ROOT).contains(args[0].toLowerCase(Locale.ROOT)))
                        .sorted()
                        .limit(8)
                        .collect(Collectors.joining(", "));
                if (!suggestions.isEmpty()) sender.sendMessage("Did you mean: " + suggestions);
                return true;
            }
            id = pluginScoped;
        }

        Player p = (Player) sender;
        selector.open(p, id);
        return true; // IMPORTANT: true to avoid usage echo
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return validIds.stream()
                    .map(NamespacedKey::getKey)
                    .filter(k -> k.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .limit(50)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
