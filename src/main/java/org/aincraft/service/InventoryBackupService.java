package org.aincraft.service;

import org.aincraft.storage.SQLiteDatabase;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.UUID;

public final class InventoryBackupService {

    private static final String DDL_BACKUPS = """
      CREATE TABLE IF NOT EXISTS player_inventory_backups (
        player_uuid TEXT PRIMARY KEY,
        contents    BLOB NOT NULL,
        created_at  INTEGER NOT NULL DEFAULT (unixepoch())
      )
      """;

    private static final String SQL_UPSERT_BACKUP = """
      INSERT INTO player_inventory_backups(player_uuid, contents, created_at)
      VALUES (?, ?, unixepoch())
      ON CONFLICT(player_uuid) DO UPDATE SET
        contents   = excluded.contents,
        created_at = excluded.created_at
      """;

    private static final String SQL_SELECT_BACKUP = """
      SELECT contents FROM player_inventory_backups WHERE player_uuid = ? LIMIT 1
      """;

    private static final String SQL_DELETE_BACKUP = """
      DELETE FROM player_inventory_backups WHERE player_uuid = ?
      """;

    private final SQLiteDatabase db;

    public InventoryBackupService(SQLiteDatabase db) {
        this.db = db;
    }

    /** Create backup table. Call once in onEnable(). */
    public void init() throws SQLException {
        try (Connection c = db.connection(); Statement st = c.createStatement()) {
            st.executeUpdate(DDL_BACKUPS);
        }
    }

    /** Create/overwrite a backup for this player’s current inventory if one doesn’t already exist. */
    public void backupIfNeeded(Player p) throws Exception {
        UUID id = p.getUniqueId();
        // if already backed up, do nothing
        if (hasBackup(id)) return;

        byte[] blob = serialize(p.getInventory());
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(SQL_UPSERT_BACKUP)) {
            ps.setString(1, id.toString());
            ps.setBytes(2, blob);
            ps.executeUpdate();
        }
    }

    /** Restore and clear the backup if present. Safe to call multiple times. */
    public void restoreIfPresent(Player p) throws Exception {
        UUID id = p.getUniqueId();
        byte[] blob = null;

        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(SQL_SELECT_BACKUP)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    blob = rs.getBytes("contents");
                }
            }
        }

        if (blob == null) return; // nothing to restore

        ItemStack[] items = deserialize(blob);
        // Defensive: length may differ across versions; clamp to player inv size
        ItemStack[] current = p.getInventory().getContents();
        if (items.length != current.length) {
            ItemStack[] resized = new ItemStack[current.length];
            System.arraycopy(items, 0, resized, 0, Math.min(items.length, resized.length));
            items = resized;
        }

        p.getInventory().setContents(items);

        // clear row after success
        clearBackup(id);
    }

    public boolean hasBackup(UUID id) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(SQL_SELECT_BACKUP)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void clearBackup(UUID id) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(SQL_DELETE_BACKUP)) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    // --- Serialization helpers ---

    private static byte[] serialize(PlayerInventory inv) throws Exception {
        return serialize(inv.getContents());
    }

    private static byte[] serialize(ItemStack[] items) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeInt(items.length);
            for (ItemStack it : items) {
                oos.writeObject(it);
            }
        }
        return baos.toByteArray();
    }

    private static ItemStack[] deserialize(byte[] blob) throws Exception {
        try (BukkitObjectInputStream ois = new BukkitObjectInputStream(new ByteArrayInputStream(blob))) {
            int len = ois.readInt();
            ItemStack[] items = new ItemStack[len];
            for (int i = 0; i < len; i++) {
                items[i] = (ItemStack) ois.readObject();
            }
            return items;
        }
    }
}
