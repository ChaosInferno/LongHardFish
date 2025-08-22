package org.aincraft.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;

public final class InventorySerializer {

    private InventorySerializer() {}

    public static String toBase64(ItemStack[] contents) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeInt(contents.length);
            for (ItemStack it : contents) {
                oos.writeObject(it);
            }
            oos.flush();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    public static ItemStack[] fromBase64(String data) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getDecoder().decode(data);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            int len = ois.readInt();
            ItemStack[] out = new ItemStack[len];
            for (int i = 0; i < len; i++) {
                out[i] = (ItemStack) ois.readObject();
            }
            return out;
        }
    }
}

