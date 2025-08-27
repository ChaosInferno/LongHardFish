package org.aincraft.commands;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.aincraft.math.WeightedRandom;
import org.aincraft.sfx.FishSounds;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TestCommand implements CommandExecutor {

  private final WeightedRandom<Integer> integers;

  @Inject
  public TestCommand(WeightedRandom<Integer> integers) {
    this.integers = integers;
  }

  @Override
  public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command,
      @NotNull String s, @NotNull String @NotNull [] strings) {
    return false;
  }
}
