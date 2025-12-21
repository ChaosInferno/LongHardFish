package org.aincraft.commands;

import org.aincraft.ItemService;
import org.aincraft.i18n.MessageKey;
import org.aincraft.i18n.MessageService;
import org.aincraft.i18n.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.aincraft.items.CustomFishItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GiveToolCommand implements TabExecutor {

    // Tools that are custom items
    private static final List<String> CUSTOM_ITEM_TOOLS = List.of(
        "fishdex", "sextant", "weather_radio", "watch", "fish_finder", "biome_radar", "omega_fish_finder"
    );

    // Tools that require special handling (block-based tools)
    private static final List<String> SPECIAL_TOOLS = List.of(
        "tacklebox", "gutting_station"
    );

    private final MessageService messageService;

    public GiveToolCommand(MessageService messageService) {
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
            sender.sendMessage(messageService.getMessage(MessageKey.Command.GIVE_TOOL_USAGE, Placeholders.label(label)));
            return true;
        }

        String toolId = args[0].toLowerCase(Locale.ENGLISH);
        List<String> allTools = new ArrayList<>(CUSTOM_ITEM_TOOLS);
        allTools.addAll(SPECIAL_TOOLS);

        if (!allTools.contains(toolId)) {
            sender.sendMessage(messageService.getMessage(MessageKey.Command.GIVE_TOOL_UNKNOWN, Placeholders.item(toolId)));
            sender.sendMessage(messageService.getMessage(MessageKey.Command.GIVE_TOOL_AVAILABLE, Placeholders.value(String.join(", ", allTools))));
            return true;
        }

        // Special handling for gutting_station (admin only, single item)
        if (toolId.equals("gutting_station")) {
            return handleGuttingStation(sender, label, args);
        }

        // Handle tacklebox (single item, no stacking)
        if (toolId.equals("tacklebox")) {
            return handleTacklebox(sender, label, args);
        }

        // Handle custom item tools
        return handleCustomItemTool(sender, label, toolId, args);
    }

    private boolean handleGuttingStation(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("lhf.admin")) {
            sender.sendMessage(messageService.getMessage(MessageKey.Error.PERMISSION_DENIED));
            return true;
        }

        ItemStack item = new ItemStack(Material.STONE);
        ItemService.itemService().write(item, "longhardfish:block/gutting_station");

        ItemMeta meta = item.getItemMeta();
        NamespacedKey modelKey = NamespacedKey.fromString("longhardfish:block/gutting_station");
        if (modelKey != null) meta.setItemModel(modelKey);
        meta.displayName(
                Component.text("Gutting Station")
                        .decoration(TextDecoration.ITALIC, false)
        );
        item.setItemMeta(meta);

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(messageService.getMessage(MessageKey.Error.PLAYER_NOT_FOUND));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(messageService.getMessage(MessageKey.Error.GENERIC_ERROR));
            return true;
        }

        target.getInventory().addItem(item);
        sender.sendMessage(messageService.getMessage(MessageKey.Command.GIVE_SUCCESS));
        if (sender != target) target.sendMessage(messageService.getMessage(MessageKey.Command.GIVE_SUCCESS));
        return true;
    }

    private boolean handleTacklebox(CommandSender sender, String label, String[] args) {
        ItemStack item = CustomFishItems.create("tacklebox");
        if (item == null) {
            sender.sendMessage(messageService.getMessage(MessageKey.Error.ITEM_NOT_FOUND));
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(messageService.getMessage(MessageKey.Error.PLAYER_NOT_FOUND));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(messageService.getMessage(MessageKey.Error.GENERIC_ERROR));
            return true;
        }

        target.getInventory().addItem(item);
        sender.sendMessage(messageService.getMessage(MessageKey.Command.GIVE_SUCCESS));
        if (sender != target) target.sendMessage(messageService.getMessage(MessageKey.Command.GIVE_SUCCESS));
        return true;
    }

    private boolean handleCustomItemTool(CommandSender sender, String label, String toolId, String[] args) {
        Player target = null;
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
                    sender.sendMessage(messageService.getMessage(MessageKey.Error.GENERIC_ERROR));
                    return true;
                }
            }
        } else {
            if (sender instanceof Player p) {
                target = p;
            } else {
                sender.sendMessage(messageService.getMessage(MessageKey.Error.GENERIC_ERROR));
                return true;
            }
        }

        ItemStack toolItem = CustomFishItems.create(toolId);
        if (toolItem == null) {
            sender.sendMessage(messageService.getMessage(MessageKey.Error.ITEM_NOT_FOUND));
            return true;
        }

        // Give to player
        toolItem.setAmount(Math.max(1, Math.min(64, amount)));
        var overflow = target.getInventory().addItem(toolItem);
        for (ItemStack it : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), it);
        }

        sender.sendMessage(messageService.getMessage(MessageKey.Command.GIVE_SUCCESS));
        if (sender != target) target.sendMessage(messageService.getMessage(MessageKey.Command.GIVE_SUCCESS));
        return true;
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
            List<String> allTools = new ArrayList<>(CUSTOM_ITEM_TOOLS);

            // Only suggest gutting_station to admins
            if (sender.hasPermission("lhf.admin")) {
                allTools.addAll(SPECIAL_TOOLS);
            } else {
                allTools.add("tacklebox");
            }

            for (String toolId : allTools) {
                if (toolId.startsWith(prefix)) out.add(toolId);
            }
            return out;
        }

        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ENGLISH);
            // For gutting_station, only suggest players
            if (args[0].equalsIgnoreCase("gutting_station")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ENGLISH).startsWith(prefix)) out.add(p.getName());
                }
                return out;
            }

            // For others, suggest both amounts and players
            for (String s : new String[]{"1", "2", "4", "8", "16", "32", "64"}) {
                if (s.startsWith(prefix)) out.add(s);
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ENGLISH).startsWith(prefix)) out.add(p.getName());
            }
            return out;
        }

        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ENGLISH);
            // Suggest players or amounts
            for (String s : new String[]{"1", "2", "4", "8", "16", "32", "64"}) {
                if (s.startsWith(prefix)) out.add(s);
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ENGLISH).startsWith(prefix)) out.add(p.getName());
            }
            return out;
        }

        return out;
    }
}
