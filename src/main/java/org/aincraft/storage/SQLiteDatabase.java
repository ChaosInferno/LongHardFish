package org.aincraft.storage;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.sqlite.SQLiteDataSource;

public final class SQLiteDatabase implements Database, AutoCloseable {
    private final Path dbPath;
    private final DataSource ds;

    // --- SQL DDL ---
    private static final String DDL_PLAYERS = """
      CREATE TABLE IF NOT EXISTS players (
        player_uuid TEXT PRIMARY KEY,
        first_seen_at INTEGER NOT NULL DEFAULT (unixepoch())
      )
      """;

    private static final String DDL_FISH = """
      CREATE TABLE IF NOT EXISTS fish (
        fish_key TEXT PRIMARY KEY,
        display_name TEXT
      )
      """;

    private static final String DDL_STATS = """
      CREATE TABLE IF NOT EXISTS player_fish_stats (
        player_uuid   TEXT NOT NULL,
        fish_key      TEXT NOT NULL,
        caught_count  INTEGER NOT NULL DEFAULT 0,
        drop_seen     INTEGER NOT NULL DEFAULT 0,
        last_caught_at INTEGER,
        last_drop_at   INTEGER,
        PRIMARY KEY (player_uuid, fish_key),
        FOREIGN KEY (player_uuid) REFERENCES players(player_uuid) ON DELETE CASCADE,
        FOREIGN KEY (fish_key)    REFERENCES fish(fish_key)      ON DELETE CASCADE
      )
      """;

    private static final String IDX_STATS_PLAYER = "CREATE INDEX IF NOT EXISTS idx_stats_player ON player_fish_stats(player_uuid)";
    private static final String IDX_STATS_FISH   = "CREATE INDEX IF NOT EXISTS idx_stats_fish   ON player_fish_stats(fish_key)";

    // --- SQL UPSERTS / QUERIES ---
    private static final String SQL_INSERT_PLAYER_IF_NEEDED =
            "INSERT OR IGNORE INTO players(player_uuid) VALUES (?)";

    // Keep fish display name fresh if we get a non-null name later
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
        caught_count   = player_fish_stats.caught_count + 1,
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
      WHERE p.player_uuid = ?
      ORDER BY p.caught_count DESC
      LIMIT ?
      """;

    private static final String SQL_HAS_DROP_SEEN = """
      SELECT 1
      FROM player_fish_stats
      WHERE player_uuid = ? AND fish_key = ? AND drop_seen = 1
      LIMIT 1
      """;

    public SQLiteDatabase(Path dbPath) throws SQLException {
        this.dbPath = dbPath;
        // Ensure parent directory exists
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (Exception e) {
            throw new SQLException("Failed to create data folder: " + e.getMessage(), e);
        }

        SQLiteDataSource s = new SQLiteDataSource();
        // You can also add ?busy_timeout=5000 in the URL; we set PRAGMAs in init()
        s.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        this.ds = s;
    }

    @Override
    public void init() throws SQLException {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            // Reasonable defaults for plugins
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
                if (t instanceof SQLException e) throw e;
                throw new SQLException("recordCatch failed", t);
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
                if (t instanceof SQLException e) throw e;
                throw new SQLException("markDropSeen failed", t);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    @Override
    public Map<String, Integer> topFish(UUID playerId, int limit) throws SQLException {
        String sql = """
      SELECT COALESCE(f.display_name, p.fish_key) AS name, p.caught_count
      FROM player_fish_stats p
      LEFT JOIN fish f ON f.fish_key = p.fish_key
      WHERE p.player_uuid = ?
      ORDER BY p.caught_count DESC
      LIMIT ?
      """;

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
                if (t instanceof SQLException se) throw se;
                throw new SQLException("refreshFishNames failed", t);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    @Override
    public void close() {
        // SQLiteDataSource doesn’t need explicit close; nothing to do here.
        // If you switch to a pooled DataSource, close it here.
    }
}

