package org.aincraft.items;

import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Supplier;

public final class CustomFishItems {
    private static final Map<String, Supplier<ItemStack>> REGISTRY = new LinkedHashMap<>();

    private CustomFishItems() {}

    /** Register a new custom item factory under a stable id (lowercase, no spaces). */
    public static void register(String id, Supplier<ItemStack> factory) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(factory, "factory");
        String key = id.toLowerCase(Locale.ROOT);
        if (REGISTRY.putIfAbsent(key, factory) != null) {
            throw new IllegalArgumentException("Duplicate custom item id: " + key);
        }
    }

    /** Create a fresh ItemStack for the id. Returns null if unknown. */
    public static ItemStack create(String id) {
        Supplier<ItemStack> sup = REGISTRY.get(id.toLowerCase(Locale.ROOT));
        return sup == null ? null : sup.get();
    }

    /** All registered ids (for tab completion / help). */
    public static Set<String> ids() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }
}
