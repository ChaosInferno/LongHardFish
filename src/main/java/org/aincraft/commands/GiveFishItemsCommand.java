package org.aincraft.commands;

import org.aincraft.ItemService;
import org.aincraft.LongHardFish;
import org.aincraft.i18n.MessageService;
import org.aincraft.items.CustomFishItems;
import org.aincraft.rods.RodDefinition;
import org.aincraft.rods.RodItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// NEW imports for knives
import org.aincraft.knives.KnifeDefinition;
import org.aincraft.knives.KnifeFactory;
import org.aincraft.knives.KnifeProvider;

// Imports for fish spawning
import org.aincraft.container.FishDistribution;
import org.aincraft.container.FishModel;
import org.aincraft.container.FishRarity;
import org.aincraft.list.FishCreator;
import org.aincraft.provider.FishModelProvider;
import org.aincraft.provider.FishRarityProvider;
import org.bukkit.NamespacedKey;
import java.util.Map;

// Imports for gutting output materials
import org.aincraft.processor.GuttingOutput;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.persistence.PersistentDataType;

public final class GiveFishItemsCommand implements TabExecutor {

    private final LongHardFish plugin;
    private final MessageService messageService;
    private final KnifeProvider knifeProvider; // may be null if not wired
    private final RodItemFactory rodItemFactory;

    // Fish spawning fields
    private final FishModelProvider modelProvider;
    private final FishRarityProvider rarityProvider;
    private final Map<NamespacedKey, FishModel> modelMap;
    private final Map<NamespacedKey, FishDistribution> rarityMap;
    private final FishCreator fishCreator;

    // Tool and Bait command delegates
    private GiveToolCommand giveToolCmd;
    private GiveBaitCommand giveBaitCmd;

    // Gutting output materials
    private final Map<String, GuttingOutput> guttingOutputMap;
    private final NamespacedKey guttingOutputKey;

    // Back-compat: existing constructor still works (no knives, no fish)
    public GiveFishItemsCommand(LongHardFish plugin, MessageService messageService) {
        this(plugin, messageService, null, null, null, null);
    }

    // Back-compat: pass KnifeProvider so /lhfgive knife works (no fish)
    public GiveFishItemsCommand(LongHardFish plugin, MessageService messageService, KnifeProvider knifeProvider) {
        this(plugin, messageService, knifeProvider, null, null, null);
    }

    // Preferred: pass all providers for rod, knife, fish, and materials support
    public GiveFishItemsCommand(
        LongHardFish plugin,
        MessageService messageService,
        KnifeProvider knifeProvider,
        FishModelProvider modelProvider,
        FishRarityProvider rarityProvider,
        Map<String, GuttingOutput> guttingOutputMap
    ) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.knifeProvider = knifeProvider;
        this.rodItemFactory = new RodItemFactory(plugin);
        this.guttingOutputMap = guttingOutputMap;
        this.guttingOutputKey = new NamespacedKey(plugin, "gutting_output_id");

        // Initialize fish components
        this.modelProvider = modelProvider;
        this.rarityProvider = rarityProvider;
        if (modelProvider != null && rarityProvider != null) {
            this.modelMap = modelProvider.parseFishModelObjects();
            this.rarityMap = rarityProvider.parseFishDistributorObjects();
            this.fishCreator = new FishCreator(plugin, modelMap);
        } else {
            this.modelMap = null;
            this.rarityMap = null;
            this.fishCreator = null;
        }
    }

    /**
     * Set the tool and bait command delegates (optional, for /lhf give tool/bait support)
     */
    public void setDelegateCommands(GiveToolCommand toolCmd, GiveBaitCommand baitCmd) {
        this.giveToolCmd = toolCmd;
        this.giveBaitCmd = baitCmd;
    }

    private boolean noPerm(CommandSender sender) {
        if (!sender.hasPermission("longhardfish.give") && !sender.isOp()) {
            sender.sendMessage(messageService.getMessage("error.permission_denied"));
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

        // --- Subcommand: fish ---
        if (args[0].equalsIgnoreCase("fish")) {
            if (fishCreator == null) {
                sender.sendMessage("§cFish spawning is not enabled on this server.");
                return true;
            }
            return handleGiveFish(sender, label, args);
        }

        // --- Subcommand: knife ---
        if (args[0].equalsIgnoreCase("knife")) {
            if (knifeProvider == null) {
                sender.sendMessage("§cKnives are not enabled on this server.");
                return true;
            }
            return handleGiveKnife(sender, label, args);
        }

        // --- Subcommand: tool ---
        if (args[0].equalsIgnoreCase("tool")) {
            if (giveToolCmd == null) {
                sender.sendMessage("§cTools are not enabled on this server.");
                return true;
            }
            // Remove "tool" from args and pass rest to tool command
            String[] toolArgs = new String[args.length - 1];
            System.arraycopy(args, 1, toolArgs, 0, args.length - 1);
            return giveToolCmd.onCommand(sender, command, label, toolArgs);
        }

        // --- Subcommand: bait ---
        if (args[0].equalsIgnoreCase("bait")) {
            if (giveBaitCmd == null) {
                sender.sendMessage("§cBait is not enabled on this server.");
                return true;
            }
            // Remove "bait" from args and pass rest to bait command
            String[] baitArgs = new String[args.length - 1];
            System.arraycopy(args, 1, baitArgs, 0, args.length - 1);
            return giveBaitCmd.onCommand(sender, command, label, baitArgs);
        }

        // --- Subcommand: materials ---
        if (args[0].equalsIgnoreCase("materials") || args[0].equalsIgnoreCase("material")) {
            if (guttingOutputMap == null || guttingOutputMap.isEmpty()) {
                sender.sendMessage("§cGutting materials are not enabled on this server.");
                return true;
            }
            return handleGiveMaterial(sender, label, args);
        }

        // Unknown subcommand
        sendUsage(sender, label);
        return true;
    }

    private boolean handleGiveKnife(CommandSender sender, String label, String[] args) {
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
                sender.sendMessage(messageService.getMessage("error.player_not_found"));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(messageService.getMessage("error.generic"));
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
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §7/" + label + " item <item-id> [player] [amount]");
            sender.sendMessage("§7Available items: §f" + String.join(", ", CustomFishItems.ids()));
            return true;
        }

        String itemId = args[1];
        ItemStack stack = CustomFishItems.create(itemId);
        if (stack == null) {
            sender.sendMessage("§cUnknown item-id: §f" + itemId);
            sender.sendMessage("§7Available: §f" + String.join(", ", CustomFishItems.ids()));
            return true;
        }

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
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cConsole must specify a player.");
                return true;
            }
            target = p;
        }

        stack.setAmount(Math.max(1, Math.min(64, amount)));
        var notFit = target.getInventory().addItem(stack);
        for (ItemStack it : notFit.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), it);
        }

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

    private boolean handleGiveFish(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §7/" + label + " fish <fish-id> [player] [amount]");
            return true;
        }

        // Parse fish ID (support with/without namespace)
        String fishInput = args[1].toLowerCase(Locale.ENGLISH);
        NamespacedKey fishKey = resolveFishKey(fishInput);

        if (fishKey == null || !modelMap.containsKey(fishKey)) {
            sender.sendMessage("§cUnknown fish id: §f" + fishInput);
            sender.sendMessage("§7Use tab completion to browse " + modelMap.size() + " available fish");
            return true;
        }

        // Resolve target player and amount (same pattern as knife)
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

        // Get rarity and create fish item
        FishDistribution dist = rarityMap.get(fishKey);
        FishRarity rarity = (dist != null) ? dist.getRarity() : null;
        ItemStack fishItem = fishCreator.createFishItem(fishKey, rarity);

        if (fishItem == null) {
            sender.sendMessage("§cFailed to create fish item for: §f" + fishKey);
            return true;
        }

        // Give to player
        fishItem.setAmount(Math.max(1, Math.min(64, amount)));
        var overflow = target.getInventory().addItem(fishItem);
        for (ItemStack it : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), it);
        }

        // Success messages
        sender.sendMessage("§aGave §f" + target.getName() + "§a x§f" + amount + "§a fish §b" + fishKey.getKey() + "§a.");
        if (sender != target) {
            target.sendMessage("§aYou received x§f" + amount + "§a fish §b" + fishKey.getKey() + "§a.");
        }
        return true;
    }

    private boolean handleGiveMaterial(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §7/" + label + " materials <material-id> [player] [amount]");
            sender.sendMessage("§7Available materials: §f" + String.join(", ", guttingOutputMap.keySet()));
            return true;
        }

        String materialId = args[1].toLowerCase(Locale.ENGLISH);
        GuttingOutput output = guttingOutputMap.get(materialId);

        if (output == null) {
            sender.sendMessage("§cUnknown material id: §f" + materialId);
            sender.sendMessage("§7Available: §f" + String.join(", ", guttingOutputMap.keySet()));
            return true;
        }

        // Resolve target player and amount (same pattern as fish)
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

        // Create the material item
        ItemStack materialItem = createGuttingOutputItem(output, amount);

        // Give to player
        var overflow = target.getInventory().addItem(materialItem);
        for (ItemStack it : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), it);
        }

        // Success messages
        sender.sendMessage("§aGave §f" + target.getName() + "§a x§f" + amount + "§a material §b" + materialId + "§a.");
        if (sender != target) {
            target.sendMessage("§aYou received x§f" + amount + "§a material §b" + materialId + "§a.");
        }
        return true;
    }

    private ItemStack createGuttingOutputItem(GuttingOutput output, int amount) {
        ItemStack item = new ItemStack(output.getBaseMaterial(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Set display name in white, not italic
            Component name = Component.text(output.getName())
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(name);

            // Set rarity as lore with color based on rarity
            Component rarityLine = Component.text(formatRarity(output.getRarity()))
                    .color(rarityToColor(output.getRarity()))
                    .decoration(TextDecoration.ITALIC, false);
            meta.lore(List.of(rarityLine));

            // Tag with gutting output ID
            meta.getPersistentDataContainer().set(guttingOutputKey, PersistentDataType.STRING, output.getId());

            item.setItemMeta(meta);
        }

        // Apply stacking limit from gutting_output.yml
        if (output.getStackingLimit() > 0) {
            item.setData(io.papermc.paper.datacomponent.DataComponentTypes.MAX_STACK_SIZE, output.getStackingLimit());
        }

        return item;
    }

    private String formatRarity(String rarity) {
        if (rarity == null || rarity.isEmpty()) {
            return "Common";
        }
        String lower = rarity.toLowerCase();
        return lower.substring(0, 1).toUpperCase() + lower.substring(1);
    }

    private NamedTextColor rarityToColor(String rarity) {
        if (rarity == null) {
            return NamedTextColor.GRAY;
        }

        return switch (rarity.toUpperCase()) {
            case "COMMON" -> NamedTextColor.GRAY;
            case "UNCOMMON" -> NamedTextColor.GREEN;
            case "RARE" -> NamedTextColor.AQUA;
            case "LEGENDARY" -> NamedTextColor.GOLD;
            default -> NamedTextColor.GRAY;
        };
    }

    private NamespacedKey resolveFishKey(String input) {
        // Try as-is if namespaced
        if (input.contains(":")) {
            NamespacedKey key = NamespacedKey.fromString(input);
            if (key != null && modelMap.containsKey(key)) return key;
        }

        // Try with longhardfish namespace
        NamespacedKey key = new NamespacedKey(plugin, input);
        return modelMap.containsKey(key) ? key : null;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("§eUsage:");
        sender.sendMessage("§7/" + label + " rod <rod-id> [player]");
        sender.sendMessage("§7/" + label + " fish <fish-id> [player] [amount]");
        if (knifeProvider != null) {
            sender.sendMessage("§7/" + label + " knife <knife-id> [player] [amount]");
        }
        if (giveToolCmd != null) {
            sender.sendMessage("§7/" + label + " tool <tool-id> [player] [amount]");
        }
        if (giveBaitCmd != null) {
            sender.sendMessage("§7/" + label + " bait <bait-id> [amount] [player]");
        }
        if (guttingOutputMap != null && !guttingOutputMap.isEmpty()) {
            sender.sendMessage("§7/" + label + " materials <material-id> [player] [amount]");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ENGLISH);
            if ("rod".startsWith(prefix)) out.add("rod");
            if (fishCreator != null && "fish".startsWith(prefix)) out.add("fish");
            if (knifeProvider != null && "knife".startsWith(prefix)) out.add("knife");
            if (giveToolCmd != null && "tool".startsWith(prefix)) out.add("tool");
            if (giveBaitCmd != null && "bait".startsWith(prefix)) out.add("bait");
            if (guttingOutputMap != null && !guttingOutputMap.isEmpty() && "materials".startsWith(prefix)) out.add("materials");
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

        // fish completion
        if (fishCreator != null && args[0].equalsIgnoreCase("fish")) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ENGLISH);
                for (NamespacedKey fishKey : modelMap.keySet()) {
                    String keyOnly = fishKey.getKey();
                    if (keyOnly.startsWith(prefix)) {
                        out.add(keyOnly);
                        if (out.size() >= 50) break; // Limit to 50 for performance
                    }
                }
                return out;
            }
            if (args.length == 3) {
                String prefix = args[2].toLowerCase(Locale.ENGLISH);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ENGLISH).startsWith(prefix)) {
                        out.add(p.getName());
                    }
                }
                for (String s : new String[]{"1", "2", "4", "8", "16", "32", "64"}) {
                    if (s.startsWith(prefix)) out.add(s);
                }
                return out;
            }
            if (args.length == 4) {
                String prefix = args[3].toLowerCase(Locale.ENGLISH);
                for (String s : new String[]{"1", "2", "4", "8", "16", "32", "64"}) {
                    if (s.startsWith(prefix)) out.add(s);
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
                for (String s : new String[]{"1", "2", "4", "8", "16", "32", "64"}) {
                    if (s.startsWith(prefix)) out.add(s);
                }
                return out;
            }
            if (args.length == 4) {
                String prefix = args[3].toLowerCase(Locale.ENGLISH);
                for (String s : new String[]{"1", "2", "4", "8", "16", "32", "64"}) {
                    if (s.startsWith(prefix)) out.add(s);
                }
                return out;
            }
            return List.of();
        }

        // tool completion - delegate to GiveToolCommand
        if (giveToolCmd != null && args[0].equalsIgnoreCase("tool")) {
            String[] toolArgs = new String[args.length - 1];
            System.arraycopy(args, 1, toolArgs, 0, args.length - 1);
            return giveToolCmd.onTabComplete(sender, command, alias, toolArgs);
        }

        // bait completion - delegate to GiveBaitCommand
        if (giveBaitCmd != null && args[0].equalsIgnoreCase("bait")) {
            String[] baitArgs = new String[args.length - 1];
            System.arraycopy(args, 1, baitArgs, 0, args.length - 1);
            return giveBaitCmd.onTabComplete(sender, command, alias, baitArgs);
        }

        // materials completion
        if (guttingOutputMap != null && (args[0].equalsIgnoreCase("materials") || args[0].equalsIgnoreCase("material"))) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ENGLISH);
                for (String materialId : guttingOutputMap.keySet()) {
                    if (materialId.toLowerCase(Locale.ENGLISH).startsWith(prefix)) {
                        out.add(materialId);
                    }
                }
                return out;
            }
            if (args.length == 3) {
                String prefix = args[2].toLowerCase(Locale.ENGLISH);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ENGLISH).startsWith(prefix)) {
                        out.add(p.getName());
                    }
                }
                // also allow amount here if player will be implied
                for (String s : new String[]{"1", "2", "4", "8", "16", "32", "64"}) {
                    if (s.startsWith(prefix)) out.add(s);
                }
                return out;
            }
            if (args.length == 4) {
                String prefix = args[3].toLowerCase(Locale.ENGLISH);
                for (String s : new String[]{"1", "2", "4", "8", "16", "32", "64"}) {
                    if (s.startsWith(prefix)) out.add(s);
                }
                return out;
            }
            return List.of();
        }

        return out;
    }
}
