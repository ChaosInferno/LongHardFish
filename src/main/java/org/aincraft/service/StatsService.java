package org.aincraft.service;

import org.aincraft.domain.record.FishRecord;
import org.aincraft.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class StatsService {
    private final JavaPlugin plugin;
    private final Database db;

    public StatsService(JavaPlugin plugin, Database db) { this.plugin = plugin; this.db = db; }

    public void recordCatchAsync(UUID pid, String fishKey, @Nullable String name) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                db.recordCatch(pid, fishKey, name);
            } catch (Exception e) {
                plugin.getLogger().severe("[StatsService] recordCatch failed for " + pid + " / " + fishKey + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void markDropSeenAsync(UUID pid, String fishKey, @Nullable String name) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                db.markDropSeen(pid, fishKey, name);
            } catch (Exception e) {
                plugin.getLogger().severe("[StatsService] markDropSeen failed for " + pid + " / " + fishKey + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public CompletableFuture<Map<String, Integer>> topFishAsync(UUID pid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Returns a LinkedHashMap in display order (DESC by count)
                return db.topFish(pid, limit);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    public void refreshFishNamesAsync(Map<NamespacedKey, FishRecord> models) {
        Map<String, String> map = new HashMap<>();
        for (var e : models.entrySet()) {
            map.put(e.getKey().toString(), e.getValue().displayName());
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try { db.refreshFishNames(map); }
            catch (Exception e) { plugin.getLogger().severe("[StatsService] refreshFishNames failed: " + e.getMessage()); e.printStackTrace(); }
        });
    }
}
