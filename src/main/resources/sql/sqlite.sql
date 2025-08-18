-- players (optional but handy if you want more player profile data later)
CREATE TABLE IF NOT EXISTS players (
  player_uuid TEXT PRIMARY KEY,                 -- store Bukkit UUID.toString()
  first_seen_at INTEGER NOT NULL DEFAULT (unixepoch())
);

-- all distinct fish your plugin can produce (optional but recommended)
CREATE TABLE IF NOT EXISTS fish (
  fish_key TEXT PRIMARY KEY,                    -- e.g. "longhardfish:salmon_common"
  display_name TEXT
);

-- the per-player per-fish stats you asked for
CREATE TABLE IF NOT EXISTS player_fish_stats (
  player_uuid TEXT NOT NULL,
  fish_key TEXT  NOT NULL,
  caught_count INTEGER NOT NULL DEFAULT 0,      -- how many caught by that player
  drop_seen     INTEGER NOT NULL DEFAULT 0,     -- 0/1: did this fish ever appear in that player's drop table?
  last_caught_at INTEGER,                       -- optional: debugging/leaderboards
  last_drop_at   INTEGER,                       -- optional: when it first/last appeared in drop table
  PRIMARY KEY (player_uuid, fish_key),
  FOREIGN KEY (player_uuid) REFERENCES players(player_uuid) ON DELETE CASCADE,
  FOREIGN KEY (fish_key)  REFERENCES fish(fish_key)        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_stats_player ON player_fish_stats(player_uuid);
CREATE INDEX IF NOT EXISTS idx_stats_fish   ON player_fish_stats(fish_key);