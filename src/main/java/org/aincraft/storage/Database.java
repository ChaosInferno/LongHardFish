// org/aincraft/storage/Database.java
package org.aincraft.storage;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface Database extends AutoCloseable {
    void init() throws Exception;
    void recordCatch(UUID playerId, String fishKey, @Nullable String displayName) throws Exception;
    void markDropSeen(UUID playerId, String fishKey, @Nullable String displayName) throws Exception;
    boolean hasCaught(UUID playerId, String fishKey) throws Exception;
    int caughtCount(UUID playerId, String fishKey) throws Exception;

    Map<String,Integer> topFish(UUID playerId, int limit) throws Exception;
    boolean hasDropSeen(UUID playerId, String fishKey) throws Exception;
    void refreshFishNames(Map<String, String> keyToName) throws Exception;

    // New:
    void setSeenFlag(UUID playerId, String fishKey, boolean value, @Nullable String displayName) throws Exception;
    void setCaughtFlag(UUID playerId, String fishKey, boolean value, @Nullable String displayName) throws Exception; // idempotent: true => >=1, false => 0
    void setCaughtCount(UUID playerId, String fishKey, int count, @Nullable String displayName) throws Exception;

    // Bulk helpers (used by “all”)
    List<String> allFishKeys() throws Exception;
    void setAllSeen(UUID playerId, boolean seen) throws Exception;               // seen add/remove all
    void setAllCaughtAtLeastOne(UUID playerId) throws Exception;                 // caught add all
    void clearAllCaught(UUID playerId) throws Exception;                         // caught remove all
}
