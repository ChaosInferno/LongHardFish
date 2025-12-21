package org.aincraft.packetblocks;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.aincraft.BlockModelData;
import org.aincraft.Bridge;
import org.aincraft.BlockItemMeta;
import org.aincraft.PacketBlockFactory;
import org.aincraft.registry.Registry;
import org.aincraft.registry.RegistryAccess;
import org.aincraft.registry.RegistryAccessKeys;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

public final class GuttingStationRegistry {
    private GuttingStationRegistry() {}

    public static final String ID_STR = "longhardfish:block/gutting_station";
    public static final Key ID = Key.key(ID_STR);

    /** Call this once during onEnable(), after the packetblocks core/bridge plugin has enabled. */
    public static void register() {
        PacketBlockFactory factory = Bridge.bridge().packetBlockFactory();

        // Get registries
        Registry<BlockModelData> modelRegistry = RegistryAccess.registryAccess()
                .getRegistry(RegistryAccessKeys.BLOCK_MODEL_DATA);
        Registry<BlockItemMeta> itemMetaRegistry = RegistryAccess.registryAccess()
                .getRegistry(RegistryAccessKeys.BLOCK_ITEM_META);

        if (modelRegistry.isRegistered(ID)) return; // avoid double-register on reload

        // Build the block model data using factory builder
        BlockModelData data = factory.blockModelDataBuilder(ID)
                .build();

        // Implement BlockItemMeta interface
        BlockItemMeta itemMeta = new BlockItemMeta() {
            @Override
            public Key key() {
                return ID;
            }

            @Override
            public Key itemModel() {
                return Key.key("longhardfish:block/gutting_station");
            }

            @Override
            public Material material() {
                return Material.STONE;
            }

            @Override
            public Component displayName() {
                return Component.text("Gutting Station");
            }

            @Override
            public List<? extends Component> itemLore() {
                return Collections.emptyList();
            }
        };

        // Register both the model data and item meta
        modelRegistry.register(data);
        itemMetaRegistry.register(itemMeta);
    }
}
