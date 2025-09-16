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

// NEW imports for knives
import org.aincraft.knives.KnifeDefinition;
import org.aincraft.knives.KnifeFactory;
import org.aincraft.knives.KnifeProvider;

public final class GiveFishItemsCommand implements TabExecutor {

    private final LongHardFish plugin;
    private final KnifeProvider knifeProvider; // may be null if not wired
    private final RodItemFactory rodItemFactory;

    // Back-compat: existing constructor still works (no knives)
    public GiveFishItemsCommand(LongHardFish plugin) {
        this(plugin, null);
    }

    // Preferred: pass KnifeProvider so /lhfgive knife works
    public GiveFishItemsCommand(LongHardFish plugin, KnifeProvider knifeProvider) {
        this.plugin = plugin;
        this.knifeProvider = knifeProvider;
        this.rodItemFactory = new RodItemFactory(plugin);
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
            sendUsage(sender, label);
            return true;
        }

        // --- Subcommand: rod ---
        if (args[0].equalsIgnoreCase("rod")) {
            return handleGiveRod(sender, label, args);
        }

        // --- Subcommand: knife ---
        if (args[0].equalsIgnoreCase("knife")) {
            if (knifeProvider == null) {
                sender.sendMessage("§cKnives are not enabled on this server.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§eUsage: §7/" + label + " knife <knife-id> [player] [amount]");
                return true;
            }

            String knifeId = args[1].toLowerCase(Locale.ENGLISH);

            Player target = null;
            int amount = 1;

            if (args.length >= 3) {
                Player maybe = Bukkit.getPlayerExact(args[2]);
                if (maybe != null) {
                    target = maybe;
                    if (args.length >= 4) {
                        amount = parseAmount(args[3], sender);
                        if (amount == -1) return true;
                    }
                } else {
                    // no player matched; if sender is player, treat arg[2] as amount
                    if (sender instanceof Player p) {
                        target = p;
                        amount = parseAmount(args[2], sender);
                        if (amount == -1) return true;
                    } else {
                        sender.sendMessage("§cConsole must specify a valid player.");
                        return true;
                    }
                }
            } else {
                if (sender instanceof Player p) {
                    target = p;
                } else {
                    sender.sendMessage("§cConsole must specify a player.");
                    return true;
                }
            }

            KnifeDefinition def = knifeProvider.get(knifeId);
            if (def == null) {
                sender.sendMessage("§cUnknown knife id: §f" + knifeId);
                sender.sendMessage("§7Known: §f" + String.join(", ", knifeProvider.ids()));
                return true;
            }

            // Tools are unstackable; give N separate items
            int count = Math.max(1, Math.min(64, amount));
            for (int i = 0; i < count; i++) {
                ItemStack knife = KnifeFactory.create(plugin, def);
                var overflow = target.getInventory().addItem(knife);
                for (ItemStack it : overflow.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), it);
                }
            }

            sender.sendMessage("§aGave §f" + target.getName() + "§a x§f" + count + "§a knife(s) §b" + knifeId + "§a.");
            if (sender != target) target.sendMessage("§aYou received x§f" + count + "§a knife(s) §b" + knifeId + "§a.");
            return true;
        }

        // --- Default path: custom item from CustomFishItems ---
        return handleGiveCustomItem(sender, label, args);
    }

    private boolean handleGiveRod(CommandSender sender, String label, String[] args) {
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

        ItemStack rod = rodItemFactory.create(def);
        var notFit = target.getInventory().addItem(rod);
        for (ItemStack it : notFit.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), it);
        }

        sender.sendMessage("§aGave §f" + target.getName() + "§a the rod §b" + rodId + "§a.");
        if (sender != target) target.sendMessage("§aYou received the rod §b" + rodId + "§a.");
        return true;
    }

    private boolean handleGiveCustomItem(CommandSender sender, String label, String[] args) {
        String itemId = args[0];
        ItemStack stack = CustomFishItems.create(itemId);
        if (stack == null) {
            sender.sendMessage("§cUnknown item-id: §f" + itemId + "§7. Known: §f" + String.join(", ", CustomFishItems.ids()));
            sender.sendMessage("§7Or: §f/" + label + " rod <rod-id> [player]" + (knifeProvider != null ? " §7or §f/" + label + " knife <knife-id> [player] [amount]" : ""));
            return true;
        }

        Player target;
        int amount = 1;

        if (args.length >= 2) {
            Player maybe = Bukkit.getPlayerExact(args[1]);
            if (maybe != null) {
                target = maybe;
                if (args.length >= 3) {
                    amount = parseAmount(args[2], sender);
                    if (amount == -1) return true;
                }
            } else {
                if (sender instanceof Player p) {
                    target = p;
                    amount = parseAmount(args[1], sender);
                    if (amount == -1) return true;
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

        stack.setAmount(Math.max(1, Math.min(64, amount)));
        var notFit = target.getInventory().addItem(stack);
        notFit.values().forEach(it -> target.getWorld().dropItemNaturally(target.getLocation(), it));

        sender.sendMessage("§aGave §f" + stack.getAmount() + "§a of §b" + itemId + "§a to §f" + target.getName() + "§a.");
        if (sender != target) target.sendMessage("§aYou received §f" + stack.getAmount() + "§a of §b" + itemId + "§a.");
        return true;
    }

    private int parseAmount(String raw, CommandSender sender) {
        try {
            int v = Integer.parseInt(raw);
            if (v < 1 || v > 64) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            sender.sendMessage("§cAmount must be a number 1-64.");
            return -1;
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("§eUsage:");
        sender.sendMessage("§7/" + label + " <item-id> [player] [amount]");
        sender.sendMessage("§7/" + label + " rod <rod-id> [player]");
        if (knifeProvider != null) {
            sender.sendMessage("§7/" + label + " knife <knife-id> [player] [amount]");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ENGLISH);
            if ("rod".startsWith(prefix)) out.add("rod");
            if (knifeProvider != null && "knife".startsWith(prefix)) out.add("knife");
            for (String id : CustomFishItems.ids()) {
                if (id.toLowerCase(Locale.ENGLISH).startsWith(prefix)) out.add(id);
            }
            return out;
        }

        // rod completion
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

        // knife completion
        if (knifeProvider != null && args[0].equalsIgnoreCase("knife")) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ENGLISH);
                for (String kid : knifeProvider.ids()) {
                    if (kid.toLowerCase(Locale.ENGLISH).startsWith(prefix)) out.add(kid);
                }
                return out;
            }
            if (args.length == 3) {
                String prefix = args[2].toLowerCase(Locale.ENGLISH);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ENGLISH).startsWith(prefix)) out.add(p.getName());
                }
                // also allow amount here if player will be implied
                for (String s : new String[]{"1","2","4","8","16","32","64"}) {
                    if (s.startsWith(prefix)) out.add(s);
                }
                return out;
            }
            if (args.length == 4) {
                String prefix = args[3].toLowerCase(Locale.ENGLISH);
                for (String s : new String[]{"1","2","4","8","16","32","64"}) {
                    if (s.startsWith(prefix)) out.add(s);
                }
                return out;
            }
            return List.of();
        }

        // items path
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ENGLISH);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ENGLISH).startsWith(prefix)) out.add(p.getName());
            }
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
