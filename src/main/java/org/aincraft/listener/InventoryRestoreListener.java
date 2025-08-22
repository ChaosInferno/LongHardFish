package org.aincraft.listener;

import org.aincraft.service.InventoryBackupService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.logging.Level;

public final class InventoryRestoreListener implements Listener {
    private final InventoryBackupService backup;

    public InventoryRestoreListener(InventoryBackupService backup) {
        this.backup = backup;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // If a snapshot survived a crash/stop, restore it when they rejoin
        Player p = (Player) e.getPlayer();
        try {
            backup.restoreIfPresent(p);   // <— wrap in try/catch
        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "Failed to restore inventory for " + p.getName(), ex);
        }
    }
}
