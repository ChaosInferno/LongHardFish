package org.aincraft.commands;

import org.aincraft.gui.FishDexFishSelector;
import org.aincraft.container.FishModel;
import org.aincraft.service.InventoryBackupService;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public final class FishDexCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final FishDexFishSelector selector;
    private final Set<NamespacedKey> allFishIds;
    private final FishDexFishSelector.ModelLookup modelLookup;
    private final InventoryBackupService backup;

    public FishDexCommand(JavaPlugin plugin,
                          FishDexFishSelector selector,
                          Set<NamespacedKey> allFishIds,
                          FishDexFishSelector.ModelLookup modelLookup,
                          InventoryBackupService backup) {
        this.plugin = plugin;
        this.selector = selector;
        this.allFishIds = allFishIds;
        this.modelLookup = modelLookup;
        this.backup = backup;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        NamespacedKey target;

        if (args.length == 0) {
            // Default: fish with modelNumber == 1
            target = findModelNumberOne();
            if (target == null) {
                // Fallback: the lowest model number we can find (just in case 1 is missing)
                target = allFishIds.stream()
                        .min(Comparator.comparingInt(id -> {
                            FishModel fm = modelLookup.get(id);
                            return fm != null ? fm.getModelNumber() : Integer.MAX_VALUE;
                        }))
                        .orElse(null);
            }
        } else {
            // Try resolve by full key or by key-only (e.g., "clownfish")
            target = resolveArgToKey(args[0]);
        }

        if (target == null) {
            p.sendMessage("§cCouldn't find that fish.");
            return true;
        }

        // optional: snapshot inventory before we paint hotbar/main with GUI icons
        try { backup.backupIfNeeded(p); } catch (Exception ignored) {}

        selector.open(p, target, /*playOpenSound=*/true);
        return true;
    }

    private NamespacedKey findModelNumberOne() {
        for (NamespacedKey id : allFishIds) {
            FishModel fm = modelLookup.get(id);
            if (fm != null && fm.getModelNumber() == 1) {
                return id;
            }
        }
        return null;
    }

    private NamespacedKey resolveArgToKey(String raw) {
        // 1) Try NamespacedKey string first (defaults namespace to this plugin if omitted)
        NamespacedKey k = NamespacedKey.fromString(raw, plugin);
        if (k != null && allFishIds.contains(k)) return k;

        // 2) Try match by key-only (case-insensitive)
        String keyOnly = raw.toLowerCase(Locale.ENGLISH);
        for (NamespacedKey id : allFishIds) {
            if (id.getKey().equalsIgnoreCase(keyOnly)) return id;
        }
        return null;
    }

    // Simple tab-complete of keys
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ENGLISH);
            return allFishIds.stream()
                    .map(NamespacedKey::getKey)
                    .filter(k -> k.startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
