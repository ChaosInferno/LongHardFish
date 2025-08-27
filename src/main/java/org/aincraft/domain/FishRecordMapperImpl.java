package org.aincraft.domain;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.aincraft.api.FishObject;
import org.aincraft.container.Rarity;
import org.aincraft.domain.record.FishEnvironmentRecord;
import org.aincraft.domain.record.FishRecord;
import org.aincraft.registry.Registry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

final class FishRecordMapperImpl implements DomainMapper<FishObject, FishRecord> {

  private static final Material DEFAULT_FISH_MATERIAL = Material.COD;
  private static final String FISH_KEY_IDENTIFIER = "fish_id";
  private final Plugin plugin;
  private NamespacedKey fishKey = null;
  private final DomainMapper<FishEnvironment, FishEnvironmentRecord> fishEnvironmentMapper;
  private final Registry<Rarity> rarityRegistry;

  @Inject
  FishRecordMapperImpl(Plugin plugin,
      DomainMapper<FishEnvironment, FishEnvironmentRecord> fishEnvironmentMapper,
      Registry<Rarity> rarityRegistry) {
    this.plugin = plugin;
    this.fishEnvironmentMapper = fishEnvironmentMapper;
    this.rarityRegistry = rarityRegistry;
  }

  @Override
  public @NotNull FishObject toDomain(@NotNull FishRecord record) throws IllegalArgumentException {
    NamespacedKey rarityKey = NamespacedKey.fromString(record.rarityKey());
    Preconditions.checkArgument(rarityRegistry.isRegistered(rarityKey));
    ItemStack itemStack = new ItemStack(DEFAULT_FISH_MATERIAL);
    Component displayName = MiniMessage.miniMessage().deserialize(record.displayName());
    Component description = MiniMessage.miniMessage().deserialize(record.description());
    NamespacedKey fishKey = new NamespacedKey(plugin, record.fishKey());
    Rarity rarity = rarityRegistry.getOrThrow(rarityKey);
    FishEnvironment environment = fishEnvironmentMapper.toDomain(record.environmentRecord());
    itemStack.setData(DataComponentTypes.ITEM_MODEL, fishKey);
    itemStack.setData(DataComponentTypes.ITEM_NAME, displayName);
    itemStack.setData(DataComponentTypes.LORE,
        ItemLore.lore(List.of(rarity)));
    itemStack.editPersistentDataContainer(pdc -> {
      pdc.set(fishIdentifierKey(), PersistentDataType.STRING, record.fishKey());
    });
    return new FishObjectImpl(itemStack, fishKey, displayName, description,
        record.identificationNumber(), rarity, environment, record.openWaterRequired(),
        record.rainRequired());
  }

  @NotNull
  private NamespacedKey fishIdentifierKey() {
    if (fishKey == null) {
      fishKey = new NamespacedKey(plugin, FISH_KEY_IDENTIFIER);
    }
    return fishKey;
  }
}
