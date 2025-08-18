CREATE TABLE IF NOT EXISTS lhf_fish_stats (
  player_uuid   TEXT    NOT NULL,
  fish_key      TEXT    NOT NULL,
  catch_count   INTEGER NOT NULL DEFAULT 0,
  drop_seen     INTEGER NOT NULL DEFAULT 0,  -- 0=false, 1=true
  PRIMARY KEY (player_uuid, fish_key)
);

CREATE INDEX IF NOT EXISTS idx_lhf_stats_player ON lhf_fish_stats (player_uuid);
CREATE INDEX IF NOT EXISTS idx_lhf_stats_fish   ON lhf_fish_stats (fish_key);