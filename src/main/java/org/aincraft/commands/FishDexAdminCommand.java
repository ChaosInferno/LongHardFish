// org/aincraft/commands/FishDexAdminCommand.java
package org.aincraft.commands;

import org.aincraft.i18n.MessageKey;
import org.aincraft.i18n.MessageService;
import org.aincraft.i18n.Placeholders;
import org.aincraft.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class FishDexAdminCommand implements CommandExecutor, TabCompleter {
    private final MessageService messageService;
    private final Database db;

    // default namespace to apply when user omits it
    private static final String DEFAULT_NS = "longhardfish";

    public FishDexAdminCommand(MessageService messageService, Database db) {
        this.messageService = messageService;
        this.db = db;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("lhf.admin")) {
            sender.sendMessage(messageService.getMessage(MessageKey.Error.PERMISSION_DENIED));
            return true;
        }
        if (args.length < 2) {
            usage(sender, label);
            return true;
        }

        OfflinePlayer target = resolvePlayer(args[0]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(messageService.getMessage(MessageKey.Error.PLAYER_NOT_FOUND));
            return true;
        }
        UUID uuid = target.getUniqueId();

        String sub = args[1].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "seen"   -> handleSeen(sender, label, uuid, Arrays.copyOfRange(args, 2, args.length));
                case "caught" -> handleCaught(sender, label, uuid, Arrays.copyOfRange(args, 2, args.length));
                case "list"   -> handleList(sender, label, uuid, Arrays.copyOfRange(args, 2, args.length));
                default       -> usage(sender, label);
            }
        } catch (Exception e) {
            sender.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_ERROR, Placeholders.value(e.getMessage())));
            e.printStackTrace();
        }
        return true;
    }

    private void handleSeen(CommandSender s, String label, UUID uuid, String[] a) throws Exception {
        // seen <add|remove> <fishKey|all>
        if (a.length < 2) {
            s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_USAGE_SEEN, Placeholders.label(label)));
            return;
        }
        String act = a[0].toLowerCase(Locale.ROOT);
        String keyArg = a[1];

        if ("add".equals(act)) {
            if ("all".equalsIgnoreCase(keyArg)) {
                db.setAllSeen(uuid, true);
                s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_SEEN_ADD_ALL));
            } else {
                String key = ensureNs(keyArg);
                db.setSeenFlag(uuid, key, true, null);
                s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_SEEN_ADD, Placeholders.fish(stripNs(key))));
            }
        } else if ("remove".equals(act)) {
            if ("all".equalsIgnoreCase(keyArg)) {
                db.setAllSeen(uuid, false);
                s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_SEEN_REMOVE_ALL));
            } else {
                String key = ensureNs(keyArg);
                db.setSeenFlag(uuid, key, false, null);
                db.setCaughtCount(uuid, key, 0, null); // "as if never caught"
                s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_SEEN_REMOVE, Placeholders.fish(stripNs(key))));
            }
        } else {
            s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_EXPECTED_ADD_REMOVE));
        }
    }

    private void handleList(CommandSender s, String label, UUID uuid, String[] a) throws Exception {
        // list <fishKey>
        if (a.length < 1) {
            s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_USAGE_LIST, Placeholders.label(label)));
            return;
        }
        String key = ensureNs(a[0]);
        boolean seen   = db.hasDropSeen(uuid, key);
        boolean caught = db.hasCaught(uuid, key);
        int count      = db.caughtCount(uuid, key);
        s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_LIST_RESULT,
                Placeholders.fish(stripNs(key)),
                Placeholders.seenStatus(seen),
                Placeholders.caughtStatus(caught),
                Placeholders.amount(count)));
    }

    private void handleCaught(CommandSender s, String label, UUID uuid, String[] a) throws Exception {
        // caught <set|add|remove> ...
        if (a.length < 1) {
            s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_USAGE_CAUGHT, Placeholders.label(label)));
            return;
        }
        String act = a[0].toLowerCase(Locale.ROOT);

        switch (act) {
            case "set" -> {
                // caught set <fishKey> <number>
                if (a.length < 3) {
                    s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_USAGE_CAUGHT_SET, Placeholders.label(label)));
                    return;
                }
                String key = ensureNs(a[1]);
                int n;
                try {
                    n = Integer.parseInt(a[2]);
                } catch (NumberFormatException e) {
                    s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_NUMBER_REQUIRED));
                    return;
                }

                if (n <= 0) {
                    db.setCaughtCount(uuid, key, 0, null);
                    s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_CAUGHT_SET,
                            Placeholders.fish(stripNs(key)),
                            Placeholders.amount(0),
                            Placeholders.seenNote(false)));
                } else {
                    db.setSeenFlag(uuid, key, true, null);  // also mark seen
                    db.setCaughtCount(uuid, key, n, null);
                    s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_CAUGHT_SET,
                            Placeholders.fish(stripNs(key)),
                            Placeholders.amount(n),
                            Placeholders.seenNote(true)));
                }
            }
            case "add" -> {
                // caught add all
                if (a.length < 2 || !"all".equalsIgnoreCase(a[1])) {
                    s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_USAGE_CAUGHT_ADD, Placeholders.label(label)));
                    return;
                }
                db.setAllCaughtAtLeastOne(uuid);
                s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_CAUGHT_ADD_ALL));
            }
            case "remove" -> {
                // caught remove all
                if (a.length < 2 || !"all".equalsIgnoreCase(a[1])) {
                    s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_USAGE_CAUGHT_REMOVE, Placeholders.label(label)));
                    return;
                }
                db.clearAllCaught(uuid);
                s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_CAUGHT_REMOVE_ALL));
            }
            default -> s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_EXPECTED_SET_ADD_REMOVE));
        }
    }

    // --- tab completion ---------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        ArrayList<String> out = new ArrayList<>();
        try {
            switch (args.length) {
                case 1 -> { // player
                    for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                }
                case 2 -> out.addAll(List.of("seen","caught","list"));
                case 3 -> {
                    String sub = args[1].toLowerCase(Locale.ROOT);
                    switch (sub) {
                        case "seen"   -> out.addAll(List.of("add","remove"));
                        case "caught" -> out.addAll(List.of("set","add","remove"));
                        case "list"   -> out.addAll(sampleBareFishKeys(db));
                    }
                }
                case 4 -> {
                    String sub = args[1].toLowerCase(Locale.ROOT);
                    String act = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
                    if (sub.equals("seen")) {
                        out.add("all"); out.addAll(sampleBareFishKeys(db));
                    } else if (sub.equals("caught")) {
                        if (act.equals("set")) {
                            out.addAll(sampleBareFishKeys(db));
                        } else if (act.equals("add") || act.equals("remove")) {
                            out.add("all");
                        }
                    }
                }
                case 5 -> {
                    String sub = args[1].toLowerCase(Locale.ROOT);
                    String act = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
                    if (sub.equals("caught") && act.equals("set")) {
                        out.addAll(List.of("0","1","5","10","25","64"));
                    }
                }
            }
        } catch (Exception ignored) {}
        return out.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[args.length-1].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    private static List<String> sampleBareFishKeys(Database db) throws Exception {
        // show only the key part (after ':') for convenience
        List<String> keys = db.allFishKeys();
        return keys.stream()
                .map(FishDexAdminCommand::stripNs)
                .distinct()
                .sorted()
                .limit(100)
                .collect(Collectors.toList());
    }

    // --- helpers ----------------------------------------------------------------

    private static String ensureNs(String key) {
        if (key == null || key.isBlank()) return key;
        if (key.indexOf(':') >= 0) return key; // already namespaced
        return DEFAULT_NS + ":" + key.toLowerCase(Locale.ROOT);
    }

    private static String stripNs(String key) {
        if (key == null) return null;
        int i = key.indexOf(':');
        return (i >= 0 && i + 1 < key.length()) ? key.substring(i + 1) : key;
    }

    private static OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayer(name);
    }

    private void usage(CommandSender s, String label) {
        s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_USAGE));
        s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_USAGE_SEEN, Placeholders.label(label)));
        s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_USAGE_LIST, Placeholders.label(label)));
        s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_USAGE_CAUGHT_SET, Placeholders.label(label)));
        s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_USAGE_CAUGHT_ADD, Placeholders.label(label)));
        s.sendMessage(messageService.getMessage(MessageKey.Command.ADMIN_USAGE_CAUGHT_REMOVE, Placeholders.label(label)));
    }
}
