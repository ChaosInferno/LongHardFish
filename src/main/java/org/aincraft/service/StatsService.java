package org.aincraft.service;

import org.aincraft.container.FishModel;
import org.aincraft.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

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

    /**
     * Records a catch and calls the callback with true if this was the first time the fish was caught.
     */
    public void recordCatchAsyncWithCallback(UUID pid, String fishKey, @Nullable String name, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean isFirst = db.recordCatchAndGetIsFirst(pid, fishKey, name);
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(isFirst));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[StatsService] recordCatchAsyncWithCallback failed for " + pid + " / " + fishKey + ": " + e.getMessage());
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

    /**
     * Marks a fish as seen and calls the callback with true if this was the first time the fish was marked as seen.
     */
    public void markDropSeenAsyncWithCallback(UUID pid, String fishKey, @Nullable String name, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean isFirst = db.markDropSeenAndGetIsFirst(pid, fishKey, name);
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(isFirst));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[StatsService] markDropSeenAsyncWithCallback failed for " + pid + " / " + fishKey + ": " + e.getMessage());
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

    public void refreshFishNamesAsync(Map<NamespacedKey, FishModel> models) {
        Map<String, String> map = new HashMap<>();
        for (var e : models.entrySet()) {
            map.put(e.getKey().toString(), e.getValue().getName());
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try { db.refreshFishNames(map); }
            catch (Exception e) { plugin.getLogger().severe("[StatsService] refreshFishNames failed: " + e.getMessage()); e.printStackTrace(); }
        });
    }

    /**
     * Marks multiple fish as seen in a single batch database transaction.
     * This is more efficient than calling markDropSeenAsync multiple times
     * and avoids SQLITE_BUSY errors from concurrent writes.
     */
    public void markMultipleDropSeenAsync(UUID pid, List<String> fishKeys) {
        if (fishKeys == null || fishKeys.isEmpty()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                db.markMultipleDropSeen(pid, fishKeys);
            } catch (Exception e) {
                plugin.getLogger().severe("[StatsService] markMultipleDropSeen failed for " + pid + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Marks multiple fish as seen in a single batch database transaction and calls the callback
     * with the count of fish that were newly seen for the first time.
     */
    public void markMultipleDropSeenAsyncWithCallback(UUID pid, List<String> fishKeys, Consumer<Integer> callback) {
        if (fishKeys == null || fishKeys.isEmpty()) {
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(0));
            }
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int newlySeenCount = db.markMultipleDropSeen(pid, fishKeys);
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(newlySeenCount));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[StatsService] markMultipleDropSeenAsyncWithCallback failed for " + pid + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
