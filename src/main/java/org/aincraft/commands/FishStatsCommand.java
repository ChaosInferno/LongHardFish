package org.aincraft.commands;

import org.aincraft.i18n.MessageKey;
import org.aincraft.i18n.MessageService;
import org.aincraft.i18n.Placeholders;
import org.aincraft.service.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class FishStatsCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final StatsService stats;

    public FishStatsCommand(JavaPlugin plugin, MessageService messageService, StatsService stats) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.stats = stats;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("longhardfish.stats")) {
            sender.sendMessage(messageService.getMessage(MessageKey.Error.PERMISSION_DENIED));
            return true;
        }

        // Parse [player] [limit]
        UUID targetUuid;
        String targetName;
        if (args.length >= 1) {
            OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (off == null) off = Bukkit.getOfflinePlayer(args[0]);
            targetUuid = off.getUniqueId();
            targetName = off.getName() != null ? off.getName() : args[0];
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(messageService.getMessage(MessageKey.Error.PLAYERS_ONLY));
                return true;
            }
            targetUuid = p.getUniqueId();
            targetName = p.getName();
        }

        int limit = 10;
        if (args.length >= 2) {
            try {
                limit = Math.max(1, Math.min(50, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {}
        }
        final int flimit = limit;
        final String fTargetName = targetName;

        // Async DB read -> then hop back to main to message
        CompletableFuture<Map<String, Integer>> fut = stats.topFishAsync(targetUuid, flimit);
        fut.whenComplete((map, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                sender.sendMessage(messageService.getMessage(MessageKey.Error.DATABASE_ERROR));
                return;
            }
            sender.sendMessage(messageService.getMessage(MessageKey.Command.STATS_VIEWED, Placeholders.player(fTargetName)));
            if (map == null || map.isEmpty()) {
                sender.sendMessage(messageService.getMessage(MessageKey.Command.STATS_NO_DATA, Placeholders.player(fTargetName)));
                return;
            }
            int rank = 1;
            for (Map.Entry<String, Integer> e : map.entrySet()) {
                String gap = (rank < 10) ? "   " : " "; // extra space for 1–9
                sender.sendMessage(ChatColor.YELLOW + "" + rank + "." + gap
                        + ChatColor.AQUA + e.getKey()
                        + ChatColor.GRAY + " — "
                        + ChatColor.WHITE + e.getValue());
                rank++;
            }
        }));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .toList();
        } else if (args.length == 2) {
            return Arrays.stream(new String[]{"5","10","15","20","25","50"})
                    .filter(s -> s.startsWith(args[1]))
                    .toList();
        }
        return List.of();
    }
}
