package org.aincraft.commands;

import org.aincraft.items.CustomFishItems;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class GiveFishItemsCommand implements TabExecutor {

    private final Plugin plugin;

    public GiveFishItemsCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("longhardfish.give") && !sender.isOp()) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }

        if (args.length < 1 || args.length > 3) {
            sender.sendMessage("§eUsage: /" + label + " <item-id> [player] [amount]");
            return true;
        }

        String itemId = args[0];
        ItemStack stack = CustomFishItems.create(itemId);
        if (stack == null) {
            sender.sendMessage("§cUnknown item-id: §f" + itemId + "§7. Known: §f" + String.join(", ", CustomFishItems.ids()));
            return true;
        }

        Player target;
        int amount = 1;

        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found: §f" + args[1]);
                return true;
            }
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cConsole must specify a player.");
                return true;
            }
            target = p;
        }

        if (args.length == 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                sender.sendMessage("§cAmount must be a number 1-64.");
                return true;
            }
        }

        stack.setAmount(amount);
        var notFit = target.getInventory().addItem(stack);
        if (!notFit.isEmpty()) {
            // Drop extras at feet if inventory full
            notFit.values().forEach(it -> target.getWorld().dropItemNaturally(target.getLocation(), it));
        }

        sender.sendMessage("§aGave §f" + amount + "§a of §b" + itemId + "§a to §f" + target.getName() + "§a.");
        if (sender != target) target.sendMessage("§aYou received §f" + amount + "§a of §b" + itemId + "§a.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String id : CustomFishItems.ids()) {
                if (id.startsWith(prefix)) out.add(id);
            }
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) out.add(p.getName());
            }
        } else if (args.length == 3) {
            // Suggest a few sensible amounts
            for (String s : new String[]{"1","2","4","8","16","32","64"}) {
                if (s.startsWith(args[2])) out.add(s);
            }
        }
        return out;
    }
}
