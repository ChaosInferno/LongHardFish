package org.aincraft.i18n;

/**
 * String constants for all message keys used throughout the plugin.
 * Organized by category for easy maintenance.
 */
public final class MessageKey {
    private MessageKey() {
        // Utility class
    }

    // Command messages
    public static final class Command {
        public static final String FISHDEX_OPEN = "command.fishdex.open";
        public static final String FISHDEX_NO_PERMISSION = "command.fishdex.no_permission";
        public static final String GIVE_SUCCESS = "command.give.success";
        public static final String GIVE_INVALID_ARGS = "command.give.invalid_args";
        public static final String GIVE_INVALID_PLAYER = "command.give.invalid_player";
        public static final String GIVE_INVALID_ITEM = "command.give.invalid_item";
        public static final String GIVE_INVALID_QUANTITY = "command.give.invalid_quantity";
        public static final String GIVE_TOOL_USAGE = "command.give.tool_usage";
        public static final String GIVE_TOOL_UNKNOWN = "command.give.tool_unknown";
        public static final String GIVE_TOOL_AVAILABLE = "command.give.tool_available";
        public static final String STATS_VIEWED = "command.stats.viewed";
        public static final String STATS_NO_DATA = "command.stats.no_data";
        public static final String LHF_USAGE = "command.lhf.usage";
        public static final String LHF_UNKNOWN_SUBCOMMAND = "command.lhf.unknown_subcommand";
        public static final String LHF_MAIN_USAGE = "command.lhf.main_usage";
        public static final String LHF_SUBCOMMANDS = "command.lhf.subcommands";
        public static final String LHF_GIVE_USAGE = "command.lhf.give_usage";
        public static final String LHF_TOOL_USAGE = "command.lhf.tool_usage";
        public static final String LHF_BAIT_USAGE = "command.lhf.bait_usage";
        public static final String LHF_STATS_USAGE = "command.lhf.stats_usage";
        public static final String LHF_ADMIN_USAGE = "command.lhf.admin_usage";
        public static final String LHF_GUI_USAGE = "command.lhf.gui_usage";
        // Admin command messages
        public static final String ADMIN_USAGE = "command.admin.usage";
        public static final String ADMIN_USAGE_SEEN = "command.admin.usage_seen";
        public static final String ADMIN_USAGE_CAUGHT = "command.admin.usage_caught";
        public static final String ADMIN_USAGE_CAUGHT_SET = "command.admin.usage_caught_set";
        public static final String ADMIN_USAGE_CAUGHT_ADD = "command.admin.usage_caught_add";
        public static final String ADMIN_USAGE_CAUGHT_REMOVE = "command.admin.usage_caught_remove";
        public static final String ADMIN_USAGE_LIST = "command.admin.usage_list";
        public static final String ADMIN_SEEN_ADD_ALL = "command.admin.seen_add_all";
        public static final String ADMIN_SEEN_ADD = "command.admin.seen_add";
        public static final String ADMIN_SEEN_REMOVE_ALL = "command.admin.seen_remove_all";
        public static final String ADMIN_SEEN_REMOVE = "command.admin.seen_remove";
        public static final String ADMIN_LIST_RESULT = "command.admin.list_result";
        public static final String ADMIN_CAUGHT_SET = "command.admin.caught_set";
        public static final String ADMIN_CAUGHT_ADD_ALL = "command.admin.caught_add_all";
        public static final String ADMIN_CAUGHT_REMOVE_ALL = "command.admin.caught_remove_all";
        public static final String ADMIN_EXPECTED_ADD_REMOVE = "command.admin.expected_add_remove";
        public static final String ADMIN_EXPECTED_SET_ADD_REMOVE = "command.admin.expected_set_add_remove";
        public static final String ADMIN_NUMBER_REQUIRED = "command.admin.number_required";
        public static final String ADMIN_ERROR = "command.admin.error";
    }

    // Tool messages
    public static final class Tool {
        public static final String FISH_FINDER_ACTIVE = "tool.fish_finder.active";
        public static final String FISH_FINDER_SEARCHING = "tool.fish_finder.searching";
        public static final String BIOME_RADAR_ACTIVE = "tool.biome_radar.active";
        public static final String BIOME_RADAR_NEARBY = "tool.biome_radar.nearby";
        public static final String WEATHER_RADIO_CHECKING = "tool.weather_radio.checking";
        public static final String WEATHER_RADIO_RESULT = "tool.weather_radio.result";
        public static final String SEXTANT_CHECKING = "tool.sextant.checking";
        public static final String SEXTANT_RESULT = "tool.sextant.result";
        public static final String WATCH_CHECKING = "tool.watch.checking";
        public static final String WATCH_RESULT = "tool.watch.result";
        public static final String TACKLED_BOX_EMPTY = "tool.tackled_box.empty";
        public static final String TACKLED_BOX_OPENED = "tool.tackled_box.opened";
    }

    // Fishing messages
    public static final class Fishing {
        public static final String CATCH_SUCCESS = "fishing.catch.success";
        public static final String CATCH_FAILURE = "fishing.catch.failure";
        public static final String CATCH_TREASURE = "fishing.catch.treasure";
        public static final String CATCH_JUNK = "fishing.catch.junk";
        public static final String CATCH_NO_VALID_FISH = "fishing.catch.no_valid_fish";
        public static final String CATCH_NO_FISH_AVAILABLE = "fishing.catch.no_fish_available";
        public static final String CATCH_PICK_ERROR = "fishing.catch.pick_error";
        public static final String CATCH_CREATE_ERROR = "fishing.catch.create_error";
        public static final String CATCH_ENTITY_ERROR = "fishing.catch.entity_error";
        public static final String FISH_DISCOVERY = "fishing.fish_discovery";
        public static final String BAIT_APPLIED = "fishing.bait.applied";
        public static final String BAIT_MISSING = "fishing.bait.missing";
        public static final String BAIT_INCOMPATIBLE = "fishing.bait.incompatible";
        public static final String HOOK_BREAKING = "fishing.hook.breaking";
        public static final String HOOK_BROKE = "fishing.hook.broke";
        public static final String ROD_BREAKING = "fishing.rod.breaking";
        public static final String ROD_BROKE = "fishing.rod.broke";
    }

    // Processor messages
    public static final class Processor {
        public static final String GUTTING_STARTED = "processor.gutting.started";
        public static final String GUTTING_COMPLETED = "processor.gutting.completed";
        public static final String GUTTING_FAILED = "processor.gutting.failed";
        public static final String GUTTING_INVALID_FISH = "processor.gutting.invalid_fish";
        public static final String PROCESSING_SUCCESS = "processor.processing.success";
        public static final String PROCESSING_FAILED = "processor.processing.failed";
    }

    // Error messages
    public static final class Error {
        public static final String GENERIC_ERROR = "error.generic";
        public static final String DATABASE_ERROR = "error.database";
        public static final String CONFIG_ERROR = "error.config";
        public static final String PERMISSION_DENIED = "error.permission_denied";
        public static final String PLAYER_NOT_FOUND = "error.player_not_found";
        public static final String ITEM_NOT_FOUND = "error.item_not_found";
        public static final String PLAYERS_ONLY = "error.players_only";
    }

    // UI/Info messages
    public static final class UI {
        public static final String PAGE_INDICATOR = "ui.page_indicator";
        public static final String NO_ITEMS = "ui.no_items";
        public static final String LOADING = "ui.loading";
        public static final String CONFIRMED = "ui.confirmed";
        public static final String CANCELLED = "ui.cancelled";
        public static final String PLACEHOLDER_VALUE = "ui.placeholder_value";
    }
}
