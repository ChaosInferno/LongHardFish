package org.aincraft.items;

import org.aincraft.listener.BaitForagingService;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.IntFunction;

public final class BaitRegistry {
    private BaitRegistry() {}

    /** One bait’s definition: how to create the item + its foraging rules. */
    public static final class BaitDefinition {
        public final String id;
        public final IntFunction<ItemStack> factory; // amount -> item
        public final List<BaitForagingService.Rule> rules;
        public final String singularName;
        public final String pluralName;

        public BaitDefinition(String id,
                              IntFunction<ItemStack> factory,
                              List<BaitForagingService.Rule> rules,
                              String singularName,
                              String pluralName) {
            this.id = Objects.requireNonNull(id);
            this.factory = Objects.requireNonNull(factory);
            this.rules = List.copyOf(Objects.requireNonNull(rules));
            this.singularName = Objects.requireNonNull(singularName);
            this.pluralName   = Objects.requireNonNull(pluralName);
        }

        public String nameForCount(int n) { return (n == 1) ? singularName : pluralName; }
    }

    private static final Map<String, BaitDefinition> BAITS = new LinkedHashMap<>();

    /** Register a bait once at startup. */
    public static void register(BaitDefinition def) {
        BAITS.put(def.id, def);
    }

    /** Create a bait item by id with a specific amount (uses the bait’s factory). */
    public static ItemStack create(String id, int amount) {
        var def = BAITS.get(id);
        if (def == null) return null;
        ItemStack s = def.factory.apply(Math.max(1, amount));
        s.setAmount(Math.max(1, amount));
        return s;
    }

    /** Install all registered foraging rules onto a single service. */
    public static void installForagingRules(BaitForagingService service) {
        for (var def : BAITS.values()) {
            for (var r : def.rules) service.register(r);
        }
    }

    public static String displayName(String id, int count) {
        var def = BAITS.get(id);
        if (def != null) return def.nameForCount(count);
        // Fallback: TitleCase id and a naïve plural
        String fallback = titleCase(id.replace('_', ' ').replace('-', ' '));
        return (count == 1) ? fallback : naivePlural(fallback);
    }

    private static String titleCase(String s) {
        String[] parts = s.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            out.append(Character.toUpperCase(parts[i].charAt(0)))
                    .append(parts[i].substring(1).toLowerCase());
            if (i + 1 < parts.length) out.append(' ');
        }
        return out.toString();
    }

    private static String naivePlural(String s) {
        if (s.endsWith("y") && s.length() > 1 && !"aeiou".contains(
                String.valueOf(Character.toLowerCase(s.charAt(s.length()-2))))) {
            return s.substring(0, s.length()-1) + "ies";
        }
        return s + "s";
    }
}

