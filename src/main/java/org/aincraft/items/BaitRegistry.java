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

        public BaitDefinition(String id, IntFunction<ItemStack> factory, List<BaitForagingService.Rule> rules) {
            this.id = Objects.requireNonNull(id);
            this.factory = Objects.requireNonNull(factory);
            this.rules = List.copyOf(Objects.requireNonNull(rules));
        }
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
}

