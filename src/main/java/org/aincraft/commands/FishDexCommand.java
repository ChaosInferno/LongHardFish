package org.aincraft.commands;

import org.aincraft.gui.FishDexFishSelector;
import org.aincraft.service.InventoryBackupService;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class FishDexCommand implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final FishDexFishSelector selector;
    private final InventoryBackupService backup;

    private final Map<String, NamespacedKey> byPath; // path -> key (e.g. "glider-bananafish")
    private final Map<String, NamespacedKey> byFull; // "ns:path" -> key

    public FishDexCommand(Plugin plugin,
                          FishDexFishSelector selector,
                          Collection<NamespacedKey> fishKeys,
                          InventoryBackupService backup) {
        this.plugin = plugin;
        this.selector = selector;
        this.backup = backup;

        Map<String, NamespacedKey> p = new HashMap<>();
        Map<String, NamespacedKey> f = new HashMap<>();
        for (NamespacedKey k : fishKeys) {
            p.put(k.getKey().toLowerCase(Locale.ENGLISH), k);
            f.put(k.getNamespace().toLowerCase(Locale.ENGLISH) + ":" + k.getKey().toLowerCase(Locale.ENGLISH), k);
        }
        this.byPath = p;
        this.byFull = f;
    }

    private static String normalizePath(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(Locale.ENGLISH);
        return t.replace(' ', '-').replace('_', '-');
    }

    private NamespacedKey resolveKey(String userArg) {
        if (userArg == null || userArg.isBlank()) return null;
        String raw = userArg.trim().toLowerCase(Locale.ENGLISH);

        // exact ns:path
        if (raw.contains(":")) {
            NamespacedKey k = byFull.get(raw);
            if (k != null) return k;
        }

        // path-only
        String path = normalizePath(raw);
        NamespacedKey exact = byPath.get(path);
        if (exact != null) return exact;

        // startsWith fallback
        List<NamespacedKey> candidates = byPath.entrySet().stream()
                .filter(e -> e.getKey().startsWith(path))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
        if (!candidates.isEmpty()) return candidates.get(0);

        return null;
    }

    /** Backs up the inventory once per session and clears it for the GUI. */
    private boolean ensureBackupAndClear(Player p) {
        if (backup == null) return true; // nothing to do
        try {
            if (!backup.hasBackup(p.getUniqueId())) {
                backup.backupIfNeeded(p); // your service should save *and* clear safely
            } else {
                // already backed up (user reopened the GUI) — just ensure it’s clear
                p.getInventory().clear();
                p.getInventory().setArmorContents(null);
                p.getInventory().setExtraContents(null);
                p.getInventory().setItemInOffHand(null);
                p.setItemOnCursor(null);
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to backup inventory for " + p.getName(), ex);
            p.sendMessage("§cCouldn't open the FishDex safely (inventory backup failed).");
            return false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        // Choose target fish
        NamespacedKey target;
        if (args.length == 0) {
            target = byPath.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .findFirst().orElse(null);
            if (target == null) {
                p.sendMessage("No fish registered.");
                return true;
            }
        } else {
            String joined = String.join(" ", args);
            target = resolveKey(joined);
            if (target == null) {
                p.sendMessage("Unknown fish id: " + joined);
                return true;
            }
        }

        try {
            if (!backup.hasBackup(p.getUniqueId())) {
                backup.backupIfNeeded(p);
            }
            p.getInventory().clear();
            p.getInventory().setExtraContents(null);
            p.getInventory().setItemInOffHand(null);
            p.setItemOnCursor(null);
            // p.getInventory().setArmorContents(null); // optional
        } catch (Exception ex) {
            p.sendMessage("§cCouldn't open the FishDex safely (inventory backup failed).");
            return true;
        }

        selector.open(p, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String prefix = (args.length > 0 ? String.join(" ", args) : "")
                .toLowerCase(Locale.ENGLISH)
                .replace(' ', '-')
                .replace('_', '-');

        List<String> out = new ArrayList<>();
        for (String path : byPath.keySet()) {
            if (path.startsWith(prefix)) out.add(path);
        }
        Collections.sort(out);
        return out;
    }
}
