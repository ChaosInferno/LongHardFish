package org.aincraft.packetblocks;

import net.kyori.adventure.key.Key;
import org.aincraft.*;
import org.aincraft.registry.Registry;
import org.aincraft.registry.RegistryAccess;
import org.aincraft.registry.RegistryAccessKeys;
import org.bukkit.Material;

import java.util.HashMap;

public final class GuttingStationRegistry {
    private GuttingStationRegistry() {}

    // Your packet block id (used for placement AND the item's model id)
    public static final String ID_STR = "longhardfish:block/gutting_station";
    public static final Key ID = Key.key(ID_STR);

    /** Call once during onEnable() (after PacketBlocks is ready). */
    public static void register() {
        Registry<PacketBlock.PacketBlockMeta> metaRegistry = RegistryAccess.registryAccess()
                .getRegistry(RegistryAccessKeys.PACKET_BLOCK_META);

        // prevent duplicate registration (reloads)
        try {
            if (metaRegistry.isRegistered(ID)) return;
        } catch (Throwable ignored) {}

        // This sets the ITEM model id used for the icon / client hints
        EntityModelData data = EntityModelData.create();
        data.setAttribute(EntityModelAttributes.ITEM_MODEL, ID);

        // Create a meta that renders IN-WORLD using electrum_ore’s model (works per your example)
        PacketBlock.PacketBlockMeta meta = Bridge.bridge().packetBlockFactory()
                .createBlockMeta(
                        ID,
                        new BlockItemMeta() {
                            @Override public Key getItemModel() {
                                // in-world block model (works in your example)
                                return Key.key("packetblocks:electrum_ore");
                            }
                            @Override public Material getMaterial() {
                                // server-side reference material
                                return Material.STONE;
                            }
                        },
                        data,
                        new HashMap<>() // extra props (none)
                );

        metaRegistry.register(meta);
    }
}