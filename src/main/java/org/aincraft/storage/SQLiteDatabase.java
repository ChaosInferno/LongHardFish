package org.aincraft.storage;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.sqlite.SQLiteDataSource;

public final class SQLiteDatabase implements Database, AutoCloseable {
    private final Path dbPath;
    private final DataSource ds;

    // --- SQL DDL ---
    private static final String DDL_PLAYERS = """
      CREATE TABLE IF NOT EXISTS players (
        player_uuid   TEXT PRIMARY KEY,
        first_seen_at INTEGER NOT NULL DEFAULT (unixepoch())
      )
      """;

    private static final String DDL_FISH = """
      CREATE TABLE IF NOT EXISTS fish (
        fish_key     TEXT PRIMARY KEY,
        display_name TEXT
      )
      """;

    private static final String DDL_STATS = """
      CREATE TABLE IF NOT EXISTS player_fish_stats (
        player_uuid    TEXT NOT NULL,
        fish_key       TEXT NOT NULL,
        caught_count   INTEGER NOT NULL DEFAULT 0,
        drop_seen      INTEGER NOT NULL DEFAULT 0,
        last_caught_at INTEGER,
        last_drop_at   INTEGER,
        PRIMARY KEY (player_uuid, fish_key),
        FOREIGN KEY (player_uuid) REFERENCES players(player_uuid) ON DELETE CASCADE,
        FOREIGN KEY (fish_key)    REFERENCES fish(fish_key)      ON DELETE CASCADE
      )
      """;

    private static final String IDX_STATS_PLAYER = """
      CREATE INDEX IF NOT EXISTS idx_stats_player ON player_fish_stats(player_uuid)
      """;

    private static final String IDX_STATS_FISH = """
      CREATE INDEX IF NOT EXISTS idx_stats_fish ON player_fish_stats(fish_key)
      """;

    // --- SQL UPSERTS / QUERIES (existing) ---
    private static final String SQL_INSERT_PLAYER_IF_NEEDED =
            "INSERT OR IGNORE INTO players(player_uuid) VALUES (?)";

    private static final String SQL_UPSERT_FISH = """
      INSERT INTO fish (fish_key, display_name) VALUES (?, ?)
      ON CONFLICT(fish_key) DO UPDATE SET
        display_name = COALESCE(excluded.display_name, display_name)
      """;

    private static final String SQL_UPSERT_FISH_NAME = """
      INSERT INTO fish (fish_key, display_name) VALUES (?, ?)
      ON CONFLICT(fish_key) DO UPDATE SET
        display_name = excluded.display_name
      """;

    private static final String SQL_UPSERT_CATCH = """
      INSERT INTO player_fish_stats (player_uuid, fish_key, caught_count, last_caught_at)
      VALUES (?, ?, 1, unixepoch())
      ON CONFLICT(player_uuid, fish_key) DO UPDATE SET
        caught_count   = caught_count + 1,
        last_caught_at = unixepoch()
      """;

    private static final String SQL_UPSERT_DROP_SEEN = """
      INSERT INTO player_fish_stats (player_uuid, fish_key, drop_seen, last_drop_at)
      VALUES (?, ?, 1, unixepoch())
      ON CONFLICT(player_uuid, fish_key) DO UPDATE SET
        drop_seen   = 1,
        last_drop_at = unixepoch()
      """;

    private static final String SQL_TOP_FISH = """
      SELECT COALESCE(f.display_name, p.fish_key) AS name, p.caught_count
      FROM player_fish_stats p
      LEFT JOIN fish f ON f.fish_key = p.fish_key
      WHERE p.player_uuid = ? AND p.caught_count > 0
      ORDER BY p.caught_count DESC
      LIMIT ?
      """;

    private static final String SQL_HAS_DROP_SEEN = """
      SELECT 1
      FROM player_fish_stats
      WHERE player_uuid = ? AND fish_key = ? AND drop_seen = 1
      LIMIT 1
      """;

    private static final String SQL_HAS_CAUGHT = """
      SELECT 1
      FROM player_fish_stats
      WHERE player_uuid = ? AND fish_key = ? AND caught_count > 0
      LIMIT 1
      """;

    private static final String SQL_SELECT_CAUGHT_COUNT = """
      SELECT caught_count
      FROM player_fish_stats
      WHERE player_uuid = ? AND fish_key = ?
      LIMIT 1
      """;

    // --- NEW: helpers for admin command ---
    private static final String SQL_SELECT_ALL_FISH_KEYS = "SELECT fish_key FROM fish";

    private static final String SQL_SET_CAUGHT_TRUE = """
      INSERT INTO player_fish_stats (player_uuid, fish_key, caught_count, last_caught_at)
      VALUES (?, ?, 1, unixepoch())
      ON CONFLICT(player_uuid, fish_key) DO UPDATE SET
        caught_count   = CASE WHEN player_fish_stats.caught_count < 1 THEN 1 ELSE player_fish_stats.caught_count END,
        last_caught_at = COALESCE(player_fish_stats.last_caught_at, unixepoch())
      """;

    private static final String SQL_SET_CAUGHT_FALSE = """
      INSERT INTO player_fish_stats (player_uuid, fish_key, caught_count, last_caught_at)
      VALUES (?, ?, 0, NULL)
      ON CONFLICT(player_uuid, fish_key) DO UPDATE SET
        caught_count   = 0,
        last_caught_at = NULL
      """;

    private static final String SQL_SET_SEEN_TRUE = """
      INSERT INTO player_fish_stats (player_uuid, fish_key, drop_seen, last_drop_at)
      VALUES (?, ?, 1, unixepoch())
      ON CONFLICT(player_uuid, fish_key) DO UPDATE SET
        drop_seen   = 1,
        last_drop_at = COALESCE(player_fish_stats.last_drop_at, unixepoch())
      """;

    private static final String SQL_SET_SEEN_FALSE = """
      INSERT INTO player_fish_stats (player_uuid, fish_key, drop_seen, last_drop_at)
      VALUES (?, ?, 0, NULL)
      ON CONFLICT(player_uuid, fish_key) DO UPDATE SET
        drop_seen   = 0,
        last_drop_at = NULL
      """;

    private static final String SQL_SET_CAUGHT_COUNT = """
      INSERT INTO player_fish_stats (player_uuid, fish_key, caught_count, last_caught_at)
      VALUES (?, ?, ?, CASE WHEN ? > 0 THEN unixepoch() ELSE NULL END)
      ON CONFLICT(player_uuid, fish_key) DO UPDATE SET
        caught_count   = excluded.caught_count,
        last_caught_at = excluded.last_caught_at
      """;

    // bulk “all”
    private static final String SQL_BULK_SEEN_TRUE_ALL = """
      INSERT INTO player_fish_stats (player_uuid, fish_key, drop_seen, last_drop_at)
      SELECT ?, fish_key, 1, unixepoch() FROM fish
      ON CONFLICT(player_uuid, fish_key) DO UPDATE SET
        drop_seen   = 1,
        last_drop_at = COALESCE(player_fish_stats.last_drop_at, unixepoch())
      """;

    private static final String SQL_BULK_SEEN_FALSE_ALL = """
      UPDATE player_fish_stats
      SET drop_seen = 0, last_drop_at = NULL
      WHERE player_uuid = ?
      """;

    private static final String SQL_BULK_CAUGHT_ADD_ALL = """
      INSERT INTO player_fish_stats (player_uuid, fish_key, caught_count, last_caught_at, drop_seen, last_drop_at)
      SELECT ?, fish_key, 1, unixepoch(), 1, unixepoch() FROM fish
      ON CONFLICT(player_uuid, fish_key) DO UPDATE SET
        caught_count   = CASE WHEN player_fish_stats.caught_count < 1 THEN 1 ELSE player_fish_stats.caught_count END,
        last_caught_at = CASE WHEN player_fish_stats.caught_count < 1 THEN unixepoch() ELSE player_fish_stats.last_caught_at END,
        drop_seen      = 1,
        last_drop_at   = COALESCE(player_fish_stats.last_drop_at, unixepoch())
      """;

    private static final String SQL_BULK_CAUGHT_REMOVE_ALL = """
      UPDATE player_fish_stats
      SET caught_count = 0, last_caught_at = NULL
      WHERE player_uuid = ?
      """;

    public SQLiteDatabase(Path dbPath) throws SQLException {
        this.dbPath = dbPath;
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (Exception e) {
            throw new SQLException("Failed to create data folder: " + e.getMessage(), e);
        }
        SQLiteDataSource s = new SQLiteDataSource();
        s.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        this.ds = s;
    }

    @Override
    public void init() throws SQLException {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA synchronous = NORMAL");
            st.execute("PRAGMA busy_timeout = 5000");

            st.executeUpdate(DDL_PLAYERS);
            st.executeUpdate(DDL_FISH);
            st.executeUpdate(DDL_STATS);
            st.executeUpdate(IDX_STATS_PLAYER);
            st.executeUpdate(IDX_STATS_FISH);
        }
    }

    @Override
    public void recordCatch(UUID playerId, String fishKey, String displayName) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement psPlayer = c.prepareStatement(SQL_INSERT_PLAYER_IF_NEEDED);
                 PreparedStatement psFish   = c.prepareStatement(SQL_UPSERT_FISH);
                 PreparedStatement psUpsert = c.prepareStatement(SQL_UPSERT_CATCH)) {

                psPlayer.setString(1, playerId.toString());
                psPlayer.executeUpdate();

                psFish.setString(1, fishKey);
                if (displayName != null && !displayName.isBlank()) {
                    psFish.setString(2, displayName);
                } else {
                    psFish.setNull(2, Types.VARCHAR);
                }
                psFish.executeUpdate();

                psUpsert.setString(1, playerId.toString());
                psUpsert.setString(2, fishKey);
                psUpsert.executeUpdate();

                c.commit();
            } catch (Throwable t) {
                c.rollback();
                throw asSql(t, "recordCatch failed");
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    @Override
    public void markDropSeen(UUID playerId, String fishKey, String displayName) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement psPlayer = c.prepareStatement(SQL_INSERT_PLAYER_IF_NEEDED);
                 PreparedStatement psFish   = c.prepareStatement(SQL_UPSERT_FISH);
                 PreparedStatement psUpsert = c.prepareStatement(SQL_UPSERT_DROP_SEEN)) {

                psPlayer.setString(1, playerId.toString());
                psPlayer.executeUpdate();

                psFish.setString(1, fishKey);
                if (displayName != null && !displayName.isBlank()) {
                    psFish.setString(2, displayName);
                } else {
                    psFish.setNull(2, Types.VARCHAR);
                }
                psFish.executeUpdate();

                psUpsert.setString(1, playerId.toString());
                psUpsert.setString(2, fishKey);
                psUpsert.executeUpdate();

                c.commit();
            } catch (Throwable t) {
                c.rollback();
                throw asSql(t, "markDropSeen failed");
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    @Override
    public Map<String, Integer> topFish(UUID playerId, int limit) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(SQL_TOP_FISH)) {
            ps.setString(1, playerId.toString());
            ps.setInt(2, Math.max(1, Math.min(100, limit)));
            LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("name"), rs.getInt("caught_count"));
                }
            }
            return out;
        }
    }

    @Override
    public boolean hasDropSeen(UUID playerId, String fishKey) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(SQL_HAS_DROP_SEEN)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, fishKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean hasCaught(UUID playerId, String fishKey) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(SQL_HAS_CAUGHT)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, fishKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public int caughtCount(UUID playerId, String fishKey) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(SQL_SELECT_CAUGHT_COUNT)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, fishKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("caught_count");
                return 0;
            }
        }
    }

    @Override
    public void refreshFishNames(Map<String, String> keyToName) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(SQL_UPSERT_FISH_NAME)) {
                for (Map.Entry<String, String> e : keyToName.entrySet()) {
                    ps.setString(1, e.getKey());
                    ps.setString(2, e.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (Throwable t) {
                c.rollback();
                throw asSql(t, "refreshFishNames failed");
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // ---------- NEW METHODS required by the admin command ----------

    @Override
    public java.util.List<String> allFishKeys() throws SQLException {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT fish_key FROM fish ORDER BY fish_key");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    @Override
    public void setSeenFlag(UUID playerId, String fishKey, boolean seen, String displayName) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                // ensure player exists
                try (PreparedStatement ps = c.prepareStatement(SQL_INSERT_PLAYER_IF_NEEDED)) {
                    ps.setString(1, playerId.toString());
                    ps.executeUpdate();
                }
                // ensure fish exists (optionally update display name)
                try (PreparedStatement ps = c.prepareStatement(SQL_UPSERT_FISH)) {
                    ps.setString(1, fishKey);
                    if (displayName != null && !displayName.isBlank()) ps.setString(2, displayName);
                    else ps.setNull(2, Types.VARCHAR);
                    ps.executeUpdate();
                }
                // ensure stats row exists (defaults to 0s)
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT OR IGNORE INTO player_fish_stats (player_uuid, fish_key) VALUES (?, ?)")) {
                    ps.setString(1, playerId.toString());
                    ps.setString(2, fishKey);
                    ps.executeUpdate();
                }
                // set seen flag
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE player_fish_stats " +
                                "SET drop_seen = ?, last_drop_at = CASE WHEN ?=1 THEN unixepoch() ELSE NULL END " +
                                "WHERE player_uuid = ? AND fish_key = ?")) {
                    ps.setInt(1, seen ? 1 : 0);
                    ps.setInt(2, seen ? 1 : 0);
                    ps.setString(3, playerId.toString());
                    ps.setString(4, fishKey);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (Throwable t) {
                c.rollback();
                if (t instanceof SQLException e) throw e;
                throw new SQLException("setSeenFlag failed", t);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    @Override
    public void setCaughtFlag(UUID playerId, String fishKey, boolean value, String displayName) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement psPlayer = c.prepareStatement(SQL_INSERT_PLAYER_IF_NEEDED);
                 PreparedStatement psFish   = c.prepareStatement(SQL_UPSERT_FISH);
                 PreparedStatement psSet    = c.prepareStatement(value ? SQL_SET_CAUGHT_TRUE : SQL_SET_CAUGHT_FALSE)) {

                psPlayer.setString(1, playerId.toString());
                psPlayer.executeUpdate();

                psFish.setString(1, fishKey);
                if (displayName != null && !displayName.isBlank()) {
                    psFish.setString(2, displayName);
                } else {
                    psFish.setNull(2, Types.VARCHAR);
                }
                psFish.executeUpdate();

                psSet.setString(1, playerId.toString());
                psSet.setString(2, fishKey);
                psSet.executeUpdate();

                c.commit();
            } catch (Throwable t) {
                c.rollback();
                throw asSql(t, "setCaughtFlag failed");
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    @Override
    public void setCaughtCount(UUID playerId, String fishKey, int count, String displayName) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                // ensure player + fish rows
                try (PreparedStatement ps = c.prepareStatement(SQL_INSERT_PLAYER_IF_NEEDED)) {
                    ps.setString(1, playerId.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(SQL_UPSERT_FISH)) {
                    ps.setString(1, fishKey);
                    if (displayName != null && !displayName.isBlank()) ps.setString(2, displayName);
                    else ps.setNull(2, Types.VARCHAR);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT OR IGNORE INTO player_fish_stats (player_uuid, fish_key) VALUES (?, ?)")) {
                    ps.setString(1, playerId.toString());
                    ps.setString(2, fishKey);
                    ps.executeUpdate();
                }
                // set count (0 clears last_caught_at)
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE player_fish_stats " +
                                "SET caught_count = ?, last_caught_at = CASE WHEN ?>0 THEN unixepoch() ELSE NULL END " +
                                "WHERE player_uuid = ? AND fish_key = ?")) {
                    ps.setInt(1, Math.max(0, count));
                    ps.setInt(2, Math.max(0, count));
                    ps.setString(3, playerId.toString());
                    ps.setString(4, fishKey);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (Throwable t) {
                c.rollback();
                if (t instanceof SQLException e) throw e;
                throw new SQLException("setCaughtCount failed", t);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    @Override
    public void setAllSeen(UUID playerId, boolean seen) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                // ensure player exists
                try (PreparedStatement ps = c.prepareStatement(SQL_INSERT_PLAYER_IF_NEEDED)) {
                    ps.setString(1, playerId.toString());
                    ps.executeUpdate();
                }

                if (seen) {
                    // create missing rows for ALL fish with seen=1
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT OR IGNORE INTO player_fish_stats (player_uuid, fish_key, drop_seen, last_drop_at) " +
                                    "SELECT ?, fish_key, 1, unixepoch() FROM fish")) {
                        ps.setString(1, playerId.toString());
                        ps.executeUpdate();
                    }
                    // and force seen=1 on any existing rows
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE player_fish_stats " +
                                    "SET drop_seen = 1, last_drop_at = unixepoch() " +
                                    "WHERE player_uuid = ?")) {
                        ps.setString(1, playerId.toString());
                        ps.executeUpdate();
                    }
                } else {
                    // set seen=0 on all rows for this player (don’t touch caught)
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE player_fish_stats " +
                                    "SET drop_seen = 0, last_drop_at = NULL " +
                                    "WHERE player_uuid = ?")) {
                        ps.setString(1, playerId.toString());
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (Throwable t) {
                c.rollback();
                if (t instanceof SQLException e) throw e;
                throw new SQLException("setAllSeen failed", t);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    @Override
    public void setAllCaughtAtLeastOne(UUID playerId) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                // ensure player exists
                try (PreparedStatement ps = c.prepareStatement(SQL_INSERT_PLAYER_IF_NEEDED)) {
                    ps.setString(1, playerId.toString());
                    ps.executeUpdate();
                }
                // create rows for all fish (caught_count=1, seen=1) where missing
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT OR IGNORE INTO player_fish_stats (player_uuid, fish_key, caught_count, drop_seen, last_caught_at, last_drop_at) " +
                                "SELECT ?, fish_key, 1, 1, unixepoch(), unixepoch() FROM fish")) {
                    ps.setString(1, playerId.toString());
                    ps.executeUpdate();
                }
                // bump existing zero-count rows to 1, set seen=1
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE player_fish_stats " +
                                "SET caught_count = 1, drop_seen = 1, last_caught_at = unixepoch(), last_drop_at = unixepoch() " +
                                "WHERE player_uuid = ? AND caught_count = 0")) {
                    ps.setString(1, playerId.toString());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (Throwable t) {
                c.rollback();
                if (t instanceof SQLException e) throw e;
                throw new SQLException("setAllCaughtAtLeastOne failed", t);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    @Override
    public void clearAllCaught(UUID playerId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE player_fish_stats SET caught_count = 0, last_caught_at = NULL WHERE player_uuid = ?")) {
            ps.setString(1, playerId.toString());
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------

    public Connection connection() throws SQLException {
        return ds.getConnection();
    }

    @Override
    public void close() {
        // SQLiteDataSource doesn’t need explicit close.
    }

    private static SQLException asSql(Throwable t, String msg) {
        if (t instanceof SQLException se) return se;
        return new SQLException(msg, t);
    }
}
