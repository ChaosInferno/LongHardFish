package org.aincraft.items;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Objects;

public final class TackleBoxStorage {
    // update key ONLY if you change the wire format
    private final NamespacedKey keyData;
    private final int size; // number of slots saved (e.g., 27)

    public TackleBoxStorage(Plugin plugin, int size) {
        this.keyData = new NamespacedKey(plugin, "tacklebox_contents");
        this.size = size;
    }

    /** Load saved contents from the TackleBox item into the provided inventory (size must match). */
    public void loadIntoInventory(ItemStack tackleBox, Inventory inv) {
        Objects.requireNonNull(tackleBox);
        var meta = tackleBox.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        byte[] raw = pdc.get(keyData, PersistentDataType.BYTE_ARRAY);
        if (raw == null) return;

        try (var in = new BukkitObjectInputStream(new ByteArrayInputStream(raw))) {
            int n = in.readInt();
            for (int i = 0; i < n && i < inv.getSize(); i++) {
                Object obj = in.readObject();
                inv.setItem(i, (ItemStack) obj);
            }
        } catch (Exception ignored) {
            // corrupt/old data — just ignore and open empty
        }
    }

    /** Read items from the GUI inventory and save back into the TackleBox item. */
    public void saveFromInventory(ItemStack tackleBox, Inventory inv) {
        Objects.requireNonNull(tackleBox);
        try (var bytes = new ByteArrayOutputStream();
             var out = new BukkitObjectOutputStream(bytes)) {

            int n = Math.min(inv.getSize(), size);
            out.writeInt(n);
            for (int i = 0; i < n; i++) {
                out.writeObject(inv.getItem(i));
            }
            out.flush();

            var meta = tackleBox.getItemMeta();
            meta.getPersistentDataContainer().set(keyData, PersistentDataType.BYTE_ARRAY, bytes.toByteArray());
            tackleBox.setItemMeta(meta);
        } catch (Exception e) {
            // If serialization fails, we do nothing (avoid losing the original NBT)
        }
    }
}

