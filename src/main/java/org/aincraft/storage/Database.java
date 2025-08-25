package org.aincraft.storage;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public interface Database extends AutoCloseable {
        void init() throws Exception;                       // create tables
        void recordCatch(UUID playerId, String fishKey, @Nullable String displayName) throws Exception;
        void markDropSeen(UUID playerId, String fishKey, @Nullable String displayName) throws Exception;
        boolean hasCaught(java.util.UUID playerId, String fishKey) throws java.sql.SQLException;

        // (optional helpers)
        Map<String,Integer> topFish(UUID playerId, int limit) throws Exception;
        boolean hasDropSeen(UUID playerId, String fishKey) throws Exception;
        void refreshFishNames(Map<String, String> keyToName) throws Exception;
    }
