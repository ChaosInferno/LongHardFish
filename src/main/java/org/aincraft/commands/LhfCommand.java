package org.aincraft.commands;

import org.aincraft.LongHardFish;
import org.aincraft.listener.TackleBoxService;
import org.aincraft.processor.FishProcessorUI;
import org.aincraft.service.StatsService;
import org.aincraft.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LhfCommand implements TabExecutor {

    private final LongHardFish plugin;
    private final GiveFishItemsCommand giveCmd;
    private final FishDexAdminCommand adminCmd;
    private final FishStatsCommand statsCmd;
    private final FishProcessorUI fishProcessorUI;
    private final TackleBoxService tackleBoxService;

    public LhfCommand(
        LongHardFish plugin,
        GiveFishItemsCommand giveCmd,
        FishDexAdminCommand adminCmd,
        FishStatsCommand statsCmd,
        FishProcessorUI fishProcessorUI,
        TackleBoxService tackleBoxService
    ) {
        this.plugin = plugin;
        this.giveCmd = giveCmd;
        this.adminCmd = adminCmd;
        this.statsCmd = statsCmd;
        this.fishProcessorUI = fishProcessorUI;
        this.tackleBoxService = tackleBoxService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sendMainUsage(sender, label);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ENGLISH);
        String[] subArgs = extractSubcommandArgs(args);

        switch (subcommand) {
            case "give":
                return giveCmd.onCommand(sender, command, label, subArgs);
            case "admin":
                return handleAdmin(sender, label, subArgs);
            case "stats":
                return statsCmd.onCommand(sender, command, label, subArgs);
            case "gui":
                return handleGui(sender, label);
            case "processor":
                return handleGui(sender, label);
            default:
                sendMainUsage(sender, label);
                return true;
        }
    }

    private boolean handleAdmin(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("lhf.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §7/" + label + " admin <player> <subcommand> ...");
            sender.sendMessage("§7Subcommands: seen, caught, list");
            return true;
        }
        // Create a fake command for compatibility with FishDexAdminCommand
        return adminCmd.onCommand(sender, new FakeCommand(), label, args);
    }

    private boolean handleGui(CommandSender sender, String label) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        fishProcessorUI.open(p);
        return true;
    }

    private String[] extractSubcommandArgs(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] result = new String[args.length - 1];
        System.arraycopy(args, 1, result, 0, args.length - 1);
        return result;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            String prefix = args[0].toLowerCase(Locale.ENGLISH);

            if ("give".startsWith(prefix)) subcommands.add("give");
            if (sender.hasPermission("lhf.admin") && "admin".startsWith(prefix)) subcommands.add("admin");
            if (sender.hasPermission("longhardfish.stats") && "stats".startsWith(prefix)) subcommands.add("stats");
            if ("gui".startsWith(prefix)) subcommands.add("gui");
            if ("processor".startsWith(prefix)) subcommands.add("processor");

            return subcommands;
        }

        // Tab complete for subcommands
        if (args.length > 1) {
            String subcommand = args[0].toLowerCase(Locale.ENGLISH);
            String[] subArgs = extractSubcommandArgs(args);

            switch (subcommand) {
                case "give":
                    return giveCmd.onTabComplete(sender, command, alias, subArgs);
                case "admin":
                    return adminCmd.onTabComplete(sender, command, alias, subArgs);
                case "stats":
                    return statsCmd.onTabComplete(sender, command, alias, subArgs);
                default:
                    return new ArrayList<>();
            }
        }

        return new ArrayList<>();
    }

    private void sendMainUsage(CommandSender sender, String label) {
        sender.sendMessage("§e/§b" + label + "§e - LongHardFish command suite");
        sender.sendMessage("§7Subcommands:");
        sender.sendMessage("§7  /" + label + " give <item-id|rod|tool|knife|fish|gutting_station> ...");
        sender.sendMessage("§7  /" + label + " stats [player] [limit]");
        if (sender.hasPermission("lhf.admin")) {
            sender.sendMessage("§7  /" + label + " admin <player> <seen|caught|list> ...");
        }
        sender.sendMessage("§7  /" + label + " gui  - Open fish processor");
        sender.sendMessage("§7  /" + label + " processor  - Open fish processor");
    }

    private static class FakeCommand extends Command {
        public FakeCommand() {
            super("lhf");
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return false;
        }
    }
}
