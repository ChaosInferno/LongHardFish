package org.aincraft.commands;

import org.aincraft.LongHardFish;
import org.aincraft.items.CustomFishItems;
import org.aincraft.rods.RodDefinition;
import org.aincraft.rods.RodItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GiveFishItemsCommand implements TabExecutor {

    private final LongHardFish plugin; // <-- use your main class

    public GiveFishItemsCommand(LongHardFish plugin) {
        this.plugin = plugin;
    }

    private static boolean noPerm(CommandSender sender) {
        if (!sender.hasPermission("longhardfish.give") && !sender.isOp()) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (noPerm(sender)) return true;

        if (args.length < 1) {
            sender.sendMessage("§eUsage:");
            sender.sendMessage("§7/" + label + " <item-id> [player] [amount]");
            sender.sendMessage("§7/" + label + " rod <rod-id> [player]");
            return true;
        }

        // Subcommand: rod
        if (args[0].equalsIgnoreCase("rod")) {
            if (args.length < 2) {
                sender.sendMessage("§eUsage: §7/" + label + " rod <rod-id> [player]");
                return true;
            }
            String rodId = args[1].toLowerCase(Locale.ENGLISH);

            Player target = null;
            if (args.length >= 3) {
                target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found: §f" + args[2]);
                    return true;
                }
            } else if (sender instanceof Player p) {
                target = p;
            } else {
                sender.sendMessage("§cConsole must specify a player.");
                return true;
            }

            RodDefinition def = plugin.getRodProvider().get(rodId);
            if (def == null) {
                sender.sendMessage("§cUnknown rod id: §f" + rodId);
                sender.sendMessage("§7Known: §f" + String.join(", ", plugin.getRodProvider().ids()));
                return true;
            }

            ItemStack rod = new RodItemFactory(plugin).create(def);
            var notFit = target.getInventory().addItem(rod);
            for (ItemStack it : notFit.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), it);
            }

            sender.sendMessage("§aGave §f" + target.getName() + "§a the rod §b" + rodId + "§a.");
            if (sender != target) target.sendMessage("§aYou received the rod §b" + rodId + "§a.");
            return true;
        }

        // Default path: custom item from CustomFishItems
        String itemId = args[0];
        ItemStack stack = CustomFishItems.create(itemId);
        if (stack == null) {
            sender.sendMessage("§cUnknown item-id: §f" + itemId + "§7. Known: §f" + String.join(", ", CustomFishItems.ids()));
            sender.sendMessage("§7Or: §f/" + label + " rod <rod-id> [player]");
            return true;
        }

        Player target;
        int amount = 1;

        if (args.length >= 2) {
            // Could be a player name or amount; prefer player match first
            Player maybe = Bukkit.getPlayerExact(args[1]);
            if (maybe != null) {
                target = maybe;
                if (args.length >= 3) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cAmount must be a number 1-64.");
                        return true;
                    }
                }
            } else {
                // No player match — treat as amount (if console, still need player)
                if (sender instanceof Player p) {
                    target = p;
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cPlayer not found and amount invalid.");
                        return true;
                    }
                } else {
                    sender.sendMessage("§cConsole must specify a valid player.");
                    return true;
                }
            }
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cConsole must specify a player.");
                return true;
            }
            target = p;
        }

        stack.setAmount(amount);
        var notFit = target.getInventory().addItem(stack);
        notFit.values().forEach(it -> target.getWorld().dropItemNaturally(target.getLocation(), it));

        sender.sendMessage("§aGave §f" + amount + "§a of §b" + itemId + "§a to §f" + target.getName() + "§a.");
        if (sender != target) target.sendMessage("§aYou received §f" + amount + "§a of §b" + itemId + "§a.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ENGLISH);
            // offer "rod" subcommand and item ids
            if ("rod".startsWith(prefix)) out.add("rod");
            for (String id : CustomFishItems.ids()) {
                if (id.toLowerCase(Locale.ENGLISH).startsWith(prefix)) out.add(id);
            }
            return out;
        }

        if (args[0].equalsIgnoreCase("rod")) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ENGLISH);
                for (String rid : plugin.getRodProvider().ids()) {
                    if (rid.startsWith(prefix)) out.add(rid);
                }
                return out;
            }
            if (args.length == 3) {
                String prefix = args[2].toLowerCase(Locale.ENGLISH);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ENGLISH).startsWith(prefix)) out.add(p.getName());
                }
                return out;
            }
            return List.of();
        }

        // Items path
        if (args.length == 2) {
            // player names or amount
            String prefix = args[1].toLowerCase(Locale.ENGLISH);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ENGLISH).startsWith(prefix)) out.add(p.getName());
            }
            // also suggest amounts
            for (String s : new String[]{"1","2","4","8","16","32","64"}) {
                if (s.startsWith(prefix)) out.add(s);
            }
            return out;
        } else if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ENGLISH);
            for (String s : new String[]{"1","2","4","8","16","32","64"}) {
                if (s.startsWith(prefix)) out.add(s);
            }
            return out;
        }

        return out;
    }
}
