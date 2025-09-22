// org/aincraft/commands/FishDexAdminCommand.java
package org.aincraft.commands;

import org.aincraft.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class FishDexAdminCommand implements CommandExecutor, TabCompleter {
    private final Database db;

    // default namespace to apply when user omits it
    private static final String DEFAULT_NS = "longhardfish";

    public FishDexAdminCommand(Database db) {
        this.db = db;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("lhf.admin")) {
            sender.sendMessage("§cYou don't have permission."); return true;
        }
        if (args.length < 2) {
            usage(sender, label); return true;
        }

        OfflinePlayer target = resolvePlayer(args[0]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage("§cUnknown player: " + args[0]); return true;
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
            sender.sendMessage("§cError: " + e.getMessage());
            e.printStackTrace();
        }
        return true;
    }

    private void handleSeen(CommandSender s, String label, UUID uuid, String[] a) throws Exception {
        // seen <add|remove> <fishKey|all>
        if (a.length < 2) { s.sendMessage("§eUsage: /" + label + " <player> seen <add|remove> <fishKey|all>"); return; }
        String act = a[0].toLowerCase(Locale.ROOT);
        String keyArg = a[1];

        if ("add".equals(act)) {
            if ("all".equalsIgnoreCase(keyArg)) {
                db.setAllSeen(uuid, true);
                s.sendMessage("§aMarked §eall§a fish as §aseen§a for player.");
            } else {
                String key = ensureNs(keyArg);
                db.setSeenFlag(uuid, key, true, null);
                s.sendMessage("§aMarked §d" + stripNs(key) + "§a as §aseen§a.");
            }
        } else if ("remove".equals(act)) {
            if ("all".equalsIgnoreCase(keyArg)) {
                db.setAllSeen(uuid, false);
                s.sendMessage("§aCleared §eall§a seen flags for player.");
            } else {
                String key = ensureNs(keyArg);
                db.setSeenFlag(uuid, key, false, null);
                db.setCaughtCount(uuid, key, 0, null); // “as if never caught”
                s.sendMessage("§aCleared §d" + stripNs(key) + "§a seen and reset caught to 0.");
            }
        } else {
            s.sendMessage("§cExpected add/remove.");
        }
    }

    private void handleList(CommandSender s, String label, UUID uuid, String[] a) throws Exception {
        // list <fishKey>
        if (a.length < 1) { s.sendMessage("§eUsage: /" + label + " <player> list <fishKey>"); return; }
        String key = ensureNs(a[0]);
        boolean seen   = db.hasDropSeen(uuid, key);
        boolean caught = db.hasCaught(uuid, key);
        int count      = db.caughtCount(uuid, key);
        s.sendMessage("§b" + stripNs(key) + "§7 → seen: " + (seen ? "§atrue" : "§cfalse")
                + "§7, caught: " + (caught ? "§atrue" : "§cfalse")
                + "§7, count: §e" + count);
    }

    private void handleCaught(CommandSender s, String label, UUID uuid, String[] a) throws Exception {
        // caught <set|add|remove> ...
        if (a.length < 1) { s.sendMessage("§eUsage: /" + label + " <player> caught <set|add|remove> ..."); return; }
        String act = a[0].toLowerCase(Locale.ROOT);

        switch (act) {
            case "set" -> {
                // caught set <fishKey> <number>
                if (a.length < 3) { s.sendMessage("§eUsage: /" + label + " <player> caught set <fishKey> <number>"); return; }
                String key = ensureNs(a[1]);
                int n;
                try { n = Integer.parseInt(a[2]); } catch (NumberFormatException e) { s.sendMessage("§cNumber required."); return; }

                if (n <= 0) {
                    db.setCaughtCount(uuid, key, 0, null);
                    s.sendMessage("§aSet caught for §d" + stripNs(key) + "§a to §e0§a.");
                } else {
                    db.setSeenFlag(uuid, key, true, null);  // also mark seen
                    db.setCaughtCount(uuid, key, n, null);
                    s.sendMessage("§aSet caught for §d" + stripNs(key) + "§a to §e" + n + "§a (seen=true).");
                }
            }
            case "add" -> {
                // caught add all
                if (a.length < 2 || !"all".equalsIgnoreCase(a[1])) {
                    s.sendMessage("§eUsage: /" + label + " <player> caught add all");
                    return;
                }
                db.setAllCaughtAtLeastOne(uuid);
                s.sendMessage("§aMarked §eall§a fish as caught(1) where not already caught; preserved existing counts.");
            }
            case "remove" -> {
                // caught remove all
                if (a.length < 2 || !"all".equalsIgnoreCase(a[1])) {
                    s.sendMessage("§eUsage: /" + label + " <player> caught remove all");
                    return;
                }
                db.clearAllCaught(uuid);
                s.sendMessage("§aCleared caught for §eall§a fish (seen unchanged).");
            }
            default -> s.sendMessage("§cExpected set/add/remove.");
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

    private static void usage(CommandSender s, String label) {
        s.sendMessage("§eUsage:");
        s.sendMessage("§7/" + label + " <player> seen add <fishKey|all>");
        s.sendMessage("§7/" + label + " <player> seen remove <fishKey|all>");
        s.sendMessage("§7/" + label + " <player> list <fishKey>");
        s.sendMessage("§7/" + label + " <player> caught set <fishKey> <number>");
        s.sendMessage("§7/" + label + " <player> caught add all");
        s.sendMessage("§7/" + label + " <player> caught remove all");
    }
}
