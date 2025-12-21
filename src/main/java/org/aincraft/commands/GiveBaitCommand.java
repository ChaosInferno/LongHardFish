package org.aincraft.commands;

import org.aincraft.i18n.MessageKey;
import org.aincraft.i18n.MessageService;
import org.aincraft.i18n.Placeholders;
import org.aincraft.items.BaitRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GiveBaitCommand implements TabExecutor {

    private static final List<String> BAIT_IDS = List.of(
        "grubb", "tick", "wasp", "spiderling", "dragonfly", "rhino_beetle", "scarab"
    );

    private final MessageService messageService;

    public GiveBaitCommand(MessageService messageService) {
        this.messageService = messageService;
    }

    private boolean noPerm(CommandSender sender) {
        if (!sender.hasPermission("longhardfish.give") && !sender.isOp()) {
            sender.sendMessage(messageService.getMessage(MessageKey.Error.PERMISSION_DENIED));
            return true;
        }
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (noPerm(sender)) return true;

        if (args.length < 1) {
            sender.sendMessage(messageService.getMessage(MessageKey.Command.LHF_BAIT_USAGE, Placeholders.label(label)));
            return true;
        }

        String baitId = args[0].toLowerCase(Locale.ENGLISH);

        if (!BAIT_IDS.contains(baitId)) {
            sender.sendMessage(messageService.getMessage(MessageKey.Command.GIVE_TOOL_UNKNOWN, Placeholders.item(baitId)));
            sender.sendMessage(messageService.getMessage(MessageKey.Command.GIVE_TOOL_AVAILABLE, Placeholders.value(String.join(", ", BAIT_IDS))));
            return true;
        }

        // Parse amount and target player
        int amount = 1;
        Player target = null;

        if (args.length >= 2) {
            // Try to parse as amount first
            int parsedAmount = tryParseAmount(args[1]);
            if (parsedAmount > 0) {
                // It's an amount
                amount = parsedAmount;
                if (args.length >= 3) {
                    // Amount given, so next arg is player
                    target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) {
                        sender.sendMessage(messageService.getMessage(MessageKey.Error.PLAYER_NOT_FOUND));
                        return true;
                    }
                } else {
                    // Amount given but no player specified
                    if (sender instanceof Player p) {
                        target = p;
                    } else {
                        sender.sendMessage(messageService.getMessage(MessageKey.Error.GENERIC_ERROR));
                        return true;
                    }
                }
            } else {
                // Not an amount, try as player
                Player maybe = Bukkit.getPlayerExact(args[1]);
                if (maybe != null) {
                    target = maybe;
                    if (args.length >= 3) {
                        amount = parseAmount(args[2], sender);
                        if (amount == -1) return true;
                    }
                } else {
                    // Neither amount nor player
                    sender.sendMessage(messageService.getMessage(MessageKey.Error.GENERIC_ERROR));
                    return true;
                }
            }
        } else {
            // No arguments after bait ID
            if (sender instanceof Player p) {
                target = p;
            } else {
                sender.sendMessage(messageService.getMessage(MessageKey.Error.GENERIC_ERROR));
                return true;
            }
        }

        // Create bait item
        ItemStack baitItem = BaitRegistry.create(baitId, amount);
        if (baitItem == null) {
            sender.sendMessage(messageService.getMessage(MessageKey.Error.ITEM_NOT_FOUND));
            return true;
        }

        // Give to player
        baitItem.setAmount(Math.max(1, Math.min(64, amount)));
        var overflow = target.getInventory().addItem(baitItem);
        for (ItemStack it : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), it);
        }

        sender.sendMessage(messageService.getMessage(MessageKey.Command.GIVE_SUCCESS));
        if (sender != target) target.sendMessage(messageService.getMessage(MessageKey.Command.GIVE_SUCCESS));
        return true;
    }

    private int tryParseAmount(String raw) {
        try {
            int v = Integer.parseInt(raw);
            if (v < 1 || v > 64) return -1;
            return v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int parseAmount(String raw, CommandSender sender) {
        try {
            int v = Integer.parseInt(raw);
            if (v < 1 || v > 64) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            sender.sendMessage(messageService.getMessage(MessageKey.Command.GIVE_INVALID_QUANTITY));
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ENGLISH);
            for (String baitId : BAIT_IDS) {
                if (baitId.startsWith(prefix)) out.add(baitId);
            }
            return out;
        }

        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ENGLISH);
            // Suggest amounts
            for (String s : new String[]{"1", "2", "4", "8", "16", "32", "64"}) {
                if (s.startsWith(prefix)) out.add(s);
            }
            // Also suggest players
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ENGLISH).startsWith(prefix)) out.add(p.getName());
            }
            return out;
        }

        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ENGLISH);
            // Suggest players
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ENGLISH).startsWith(prefix)) out.add(p.getName());
            }
            return out;
        }

        return out;
    }
}
