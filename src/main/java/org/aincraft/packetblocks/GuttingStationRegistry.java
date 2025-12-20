// org/aincraft/packetblocks/GuttingStationRegistry.java
package org.aincraft.packetblocks;

import net.kyori.adventure.key.Key;
import org.aincraft.BlockModel;
import org.aincraft.Bridge;
import org.aincraft.BlockItemMeta;
import org.aincraft.PacketBlock; // or PacketBlockMeta if that's the exposed type
import org.aincraft.registry.Registry;
import org.aincraft.registry.RegistryAccess;
import org.aincraft.registry.RegistryAccessKeys;
import org.bukkit.Material;

import java.util.HashMap;

public final class GuttingStationRegistry {
    private GuttingStationRegistry() {}

    public static final String ID_STR = "longhardfish:block/gutting_station";
    public static final Key ID = Key.key(ID_STR);

    /** Call this once during onEnable(), after the packetblocks core/bridge plugin has enabled. */
    public static void register() {
        Registry<PacketBlock.PacketBlockMeta> metaRegistry = RegistryAccess.registryAccess()
                .getRegistry(RegistryAccessKeys.PACKET_BLOCK_META);

        if (metaRegistry.isRegistered(ID)) return; // avoid double-register on reload

        BlockModel.BlockModelData data = BlockModel.BlockModelData.builder()
                .itemModel(ID)
                .build();

        PacketBlock.PacketBlockMeta meta = Bridge.bridge().packetBlockFactory().createBlockMeta(
                ID,
                new BlockItemMeta() {
                    @Override public Key getItemModel() {
                        // Item representation (inventory icon) — can be the same or different
                        return Key.key("longhardfish:block/gutting_station");
                    }
                    @Override public Material getMaterial() {
                        // The physical block material (server-side placeholder)
                        return Material.STONE;
                    }
                },
                data,                     // <-- typed model data (no setAttribute)
                new HashMap<>()           // extra properties if you need them
        );

        metaRegistry.register(meta);
    }
}