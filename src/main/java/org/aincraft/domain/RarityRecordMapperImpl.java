package org.aincraft.domain;

import com.google.inject.Inject;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.aincraft.container.Rarity;
import org.aincraft.domain.record.RarityRecord;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

final class RarityRecordMapperImpl implements DomainMapper<Rarity, RarityRecord> {

  private final Plugin plugin;

  @Inject
  RarityRecordMapperImpl(Plugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public @NotNull Rarity toDomain(@NotNull RarityRecord record) throws IllegalArgumentException {
    TextColor color = TextColor.fromHexString(record.color());
    Key key = new NamespacedKey(plugin, record.label().toLowerCase(Locale.ENGLISH));
    return new RarityImpl(record.baseWeight(), Component.text(record.label(), color), color, key);
  }
}
