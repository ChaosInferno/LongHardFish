package org.aincraft.commands;

import com.google.inject.Inject;
import org.aincraft.math.WeightedRandom;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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
