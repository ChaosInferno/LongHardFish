package org.aincraft.sfx;

import com.google.common.base.Preconditions;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public enum FishSounds {
  FISH_DEX_OPEN("longhardfish:dex.open"),
  FISH_DEX_CLOSE("longhardfish:dex.close"),
  FISH_DEX_NEXT("longhardfish:dex.next"),
  FISH_DEX_PREVIOUS("longhardfish:dex.previous"),
  FISH_DEX_SELECT("longhardfish:dex.select");

  private final String resource;

  private static final float DEFAULT_VOLUME = 1.0f;
  private static final float DEFAULT_PITCH = 1.0f;

  FishSounds(String resource) {
    this.resource = resource;
  }

  public void play(@NotNull Player player) throws IllegalStateException {
    play(player, DEFAULT_PITCH, DEFAULT_VOLUME);
  }

  public void play(@NotNull Player player, float pitch, float volume) throws IllegalStateException {
    Preconditions.checkState(!(resource == null || resource.isEmpty()));
    player.playSound(player.getLocation(), resource, pitch, volume);
  }
}
