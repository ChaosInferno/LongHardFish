package org.aincraft.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.entity.Player;

/**
 * Static factory methods for common placeholder replacements used in messages.
 * Provides a convenient API for building context-specific message replacements.
 *
 * Usage example:
 *   Component msg = messageService.getMessage(
 *       MessageKey.Fishing.CATCH_SUCCESS,
 *       Placeholders.fish("Salmon"),
 *       Placeholders.rarity("Epic"),
 *       Placeholders.player(player)
 *   );
 */
public final class Placeholders {
    private Placeholders() {
        // Utility class
    }

    /**
     * Creates a TagResolver with standard formatting tags plus the given placeholders.
     */
    public static TagResolver createResolver(TagResolver... additional) {
        TagResolver[] resolvers = new TagResolver[additional.length + 1];
        resolvers[0] = StandardTags.defaults();
        System.arraycopy(additional, 0, resolvers, 1, additional.length);
        return TagResolver.resolver(resolvers);
    }

    /**
     * Placeholder for player name.
     */
    public static TagResolver player(String name) {
        return TagResolver.resolver("player", Tag.selfClosingInserting(Component.text(name)));
    }

    /**
     * Placeholder for player name from a Player object.
     */
    public static TagResolver player(Player player) {
        return player(player.getName());
    }

    /**
     * Placeholder for numeric amounts (quantity, count, etc).
     */
    public static TagResolver amount(int count) {
        return TagResolver.resolver("amount", Tag.selfClosingInserting(Component.text(count)));
    }

    /**
     * Placeholder for numeric amounts with formatting.
     */
    public static TagResolver amount(long count) {
        return TagResolver.resolver("amount", Tag.selfClosingInserting(Component.text(count)));
    }

    /**
     * Placeholder for item names.
     */
    public static TagResolver item(String itemName) {
        return TagResolver.resolver("item", Tag.selfClosingInserting(Component.text(itemName)));
    }

    /**
     * Placeholder for biome names.
     */
    public static TagResolver biome(String biomeName) {
        return TagResolver.resolver("biome", Tag.selfClosingInserting(Component.text(biomeName)));
    }

    /**
     * Placeholder for weather condition.
     */
    public static TagResolver weather(String condition) {
        return TagResolver.resolver("weather", Tag.selfClosingInserting(Component.text(condition)));
    }

    /**
     * Placeholder for time of day.
     */
    public static TagResolver time(String timeOfDay) {
        return TagResolver.resolver("time", Tag.selfClosingInserting(Component.text(timeOfDay)));
    }

    /**
     * Placeholder for game time (in ticks, converted to display format).
     */
    public static TagResolver time(long ticks) {
        int hours = (int) ((ticks / 1000) % 24);
        int minutes = (int) ((ticks % 1000) / 16.67);
        String formatted = String.format("%02d:%02d", hours, minutes);
        return TagResolver.resolver("time", Tag.selfClosingInserting(Component.text(formatted)));
    }

    /**
     * Placeholder for lunar cycle phase.
     */
    public static TagResolver moonPhase(String phase) {
        return TagResolver.resolver("moon_phase", Tag.selfClosingInserting(Component.text(phase)));
    }

    /**
     * Placeholder for moon phase from a numeric value (0-7).
     */
    public static TagResolver moonPhase(int phase) {
        String[] phases = {
            "New Moon",
            "Waxing Crescent",
            "First Quarter",
            "Waxing Gibbous",
            "Full Moon",
            "Waning Gibbous",
            "Last Quarter",
            "Waning Crescent"
        };
        String phaseName = (phase >= 0 && phase < phases.length) ? phases[phase] : "Unknown";
        return moonPhase(phaseName);
    }

    /**
     * Placeholder for a generic value.
     */
    public static TagResolver value(String key, String value) {
        return TagResolver.resolver(key, Tag.selfClosingInserting(Component.text(value)));
    }

    /**
     * Placeholder for a numeric value.
     */
    public static TagResolver value(String key, int value) {
        return TagResolver.resolver(key, Tag.selfClosingInserting(Component.text(value)));
    }

    /**
     * Placeholder for a numeric value.
     */
    public static TagResolver value(String key, long value) {
        return TagResolver.resolver(key, Tag.selfClosingInserting(Component.text(value)));
    }

    /**
     * Placeholder for a numeric value.
     */
    public static TagResolver value(String key, double value) {
        return TagResolver.resolver(key, Tag.selfClosingInserting(Component.text(value)));
    }

    /**
     * Placeholder for percent value (0-100).
     */
    public static TagResolver percent(int percentValue) {
        return TagResolver.resolver("percent", Tag.selfClosingInserting(Component.text(percentValue + "%")));
    }

    /**
     * Placeholder for a color name.
     */
    public static TagResolver color(String colorName) {
        return TagResolver.resolver("color", Tag.selfClosingInserting(Component.text(colorName)));
    }

    /**
     * Placeholder for a fish name.
     */
    public static TagResolver fish(String fishName) {
        return TagResolver.resolver("fish", Tag.selfClosingInserting(Component.text(fishName)));
    }

    /**
     * Placeholder for rarity tier.
     */
    public static TagResolver rarity(String rarityTier) {
        return TagResolver.resolver("rarity", Tag.selfClosingInserting(Component.text(rarityTier)));
    }

    /**
     * Placeholder for location coordinates.
     */
    public static TagResolver location(int x, int y, int z) {
        String formatted = String.format("(%d, %d, %d)", x, y, z);
        return TagResolver.resolver("location", Tag.selfClosingInserting(Component.text(formatted)));
    }

    /**
     * Placeholder for location coordinates from a Bukkit Location.
     */
    public static TagResolver location(org.bukkit.Location loc) {
        return location(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Placeholder for reason/message explanation.
     */
    public static TagResolver reason(String reasonText) {
        return TagResolver.resolver("reason", Tag.selfClosingInserting(Component.text(reasonText)));
    }

    /**
     * Placeholder for command usage hint.
     */
    public static TagResolver usage(String usageHint) {
        return TagResolver.resolver("usage", Tag.selfClosingInserting(Component.text(usageHint)));
    }
}
