# I18N (Internationalization) Implementation - LongHardFish

## Overview

Complete translation/internationalization infrastructure for the LongHardFish plugin. Enables localized messages in multiple languages with MiniMessage format support, thread-safe caching, and fallback to English.

## Architecture

### Components

#### 1. MessageService (`src/main/java/org/aincraft/i18n/MessageService.java`)
Core service for loading and retrieving localized messages.

**Key Features:**
- Loads language YAML files from `lang/` folder (resources + data folder)
- Parses MiniMessage format using Kyori Adventure
- Thread-safe HashMap-based message caching with ReentrantReadWriteLock
- Automatic fallback to English if key missing in current language
- Support for placeholders via TagResolver
- `reload()` method to reload messages from disk
- Per-language message counts and language availability queries

**Key Methods:**
- `getMessage(String key)` - Get Component with no replacements
- `getMessage(String key, TagResolver resolver)` - Get Component with single placeholder resolver
- `getMessage(String key, TagResolver... resolvers)` - Get Component with multiple placeholder resolvers
- `getRawMessage(String key)` - Get unparsed string (for custom processing)
- `hasMessage(String key)` - Check if key exists
- `setLanguage(String language)` - Switch active language
- `reload()` - Reload all messages from disk
- `getCurrentLanguage()` - Get active language
- `getAvailableLanguages()` - Get set of loaded languages
- `getMessageCount(String language)` - Get message count for language

**Message Loading Order:**
1. Load bundled resource default messages (fallback)
2. Load configured language from resources
3. Load both from data folder overrides (allows user customization)

#### 2. MessageKey (`src/main/java/org/aincraft/i18n/MessageKey.java`)
Static constants for all message keys in the plugin.

**Categories:**
- `Command.*` - Command feedback messages
- `Tool.*` - Ingame tool messages (FishFinder, Sextant, etc.)
- `Fishing.*` - Fishing mechanic messages (catches, bait, durability)
- `Processor.*` - Gutting/Processing UI messages
- `Error.*` - Generic error messages
- `UI.*` - General UI/info messages

**Usage:**
```java
MessageKey.Fishing.CATCH_SUCCESS
MessageKey.Tool.FISH_FINDER_ACTIVE
MessageKey.Command.GIVE_SUCCESS
```

#### 3. Placeholders (`src/main/java/org/aincraft/i18n/Placeholders.java`)
Factory methods for common placeholder TagResolvers.

**Static Methods:**
- `player(String)` / `player(Player)` - Player name
- `amount(int)` / `amount(long)` - Numeric amounts
- `item(String)` - Item names
- `biome(String)` - Biome names
- `weather(String)` - Weather conditions
- `time(String)` / `time(long ticks)` - Time of day (formatted)
- `moonPhase(String)` / `moonPhase(int)` - Lunar phase
- `fish(String)` - Fish name
- `rarity(String)` - Rarity tier
- `location(int,int,int)` / `location(Location)` - Coordinates
- `reason(String)` - Reason explanation
- `usage(String)` - Command usage hint
- `value(String key, T value)` - Generic values
- `percent(int)` - Percentage values
- `color(String)` - Color names
- `createResolver(TagResolver...)` - Combine multiple resolvers

**Usage Example:**
```java
Component message = messageService.getMessage(
    MessageKey.Fishing.CATCH_SUCCESS,
    Placeholders.fish("Salmon"),
    Placeholders.rarity("Epic"),
    Placeholders.player(player)
);
player.sendMessage(message);
```

### Message Format (MiniMessage)

Messages support Kyori Adventure's MiniMessage format:

```yaml
# Basic formatting
message: "<green>Success!</green>"
message: "<red>Error!</red>"

# Placeholders (injected via TagResolver)
message: "<green>Player <player> caught <fish>!</green>"

# Nested formatting
message: "<gold>Important: <green>Catch successful</green></gold>"

# Named colors and decorators
message: "<aqua><bold>Notice</bold></aqua>"
```

Reference: https://docs.adventure.kyori.net/minimessage.html

## Configuration

### config.yml
```yaml
language: en
```

Determines which language file is loaded. Defaults to "en" if not specified.

## File Structure

```
src/main/java/org/aincraft/i18n/
├── MessageKey.java        # Message key constants
├── MessageService.java    # Core service
└── Placeholders.java      # Placeholder factories

src/main/resources/lang/
└── en.yml                 # English messages (bundled)

plugin-data/lang/          # User override messages (created at runtime)
```

## Usage in Code

### Initialization (in onEnable)
```java
MessageService messageService = new MessageService(this);
```

### Sending Messages
```java
// Simple message
Component msg = messageService.getMessage(MessageKey.Fishing.CATCH_SUCCESS);
player.sendMessage(msg);

// With placeholders
Component msg = messageService.getMessage(
    MessageKey.Fishing.CATCH_SUCCESS,
    Placeholders.fish("Clownfish"),
    Placeholders.rarity("Rare"),
    Placeholders.player(player)
);
player.sendMessage(msg);

// Multiple placeholders
Component msg = messageService.getMessage(
    MessageKey.Command.GIVE_SUCCESS,
    Placeholders.item("Fishing Rod"),
    Placeholders.amount(5),
    Placeholders.player(targetPlayer)
);
player.sendMessage(msg);
```

### Adding New Messages

1. **Add key to MessageKey.java:**
```java
public static final class Fishing {
    public static final String MY_NEW_MESSAGE = "fishing.my_new_message";
}
```

2. **Add message to lang/en.yml:**
```yaml
fishing:
  my_new_message: "<green>Custom message with <player>!</green>"
```

3. **Use in code:**
```java
Component msg = messageService.getMessage(
    MessageKey.Fishing.MY_NEW_MESSAGE,
    Placeholders.player(player)
);
```

### Adding New Language

1. **Create lang/{language}.yml** in data folder:
```yaml
# French example: lang/fr.yml
command:
  fishdex:
    open: "<green>FishDex ouvert!</green>"
fishing:
  catch:
    success: "<green>Vous avez attrapé un <fish><gold> (<rarity>)</gold>!</green>"
```

2. **Update config.yml:**
```yaml
language: fr
```

3. **Restart plugin** - Messages reload automatically

## Thread Safety

MessageService is fully thread-safe:
- Uses ReentrantReadWriteLock for cache access
- Multiple threads can read messages concurrently
- Message writes/reloads are atomic

## Design Patterns

### SOLID Principles Applied

- **Single Responsibility**: Each class has one purpose
  - MessageKey: Define constants
  - MessageService: Load and retrieve messages
  - Placeholders: Create placeholder resolvers

- **Open/Closed**: Easy to extend with new languages and messages
  - Add new message keys without modifying service
  - Add new languages without code changes

- **Liskov Substitution**: TagResolvers are interchangeable

- **Interface Segregation**: Service exposes only necessary methods

- **Dependency Inversion**: Plugin depends on MessageService abstraction

## Future Enhancements

- Per-player language preferences
- Language selection command (`/language set <lang>`)
- Automatic locale detection from client
- Message validation during load
- Hot-reload via command
- Message parameter type safety improvements

## Examples

### Example 1: Fishing Success Message
```java
// In FishCatchListener
Component msg = messageService.getMessage(
    MessageKey.Fishing.CATCH_SUCCESS,
    Placeholders.fish(fishModel.getName()),
    Placeholders.rarity(distribution.getRarity()),
    Placeholders.player(player)
);
player.sendMessage(msg);
```

Message in en.yml:
```yaml
fishing:
  catch:
    success: "<green>You caught a <fish><gold> (<rarity>)</gold>!</green>"
```

Output: "You caught a Salmon (Rare)!"

### Example 2: Tool Usage with Time
```java
Component msg = messageService.getMessage(
    MessageKey.Tool.WATCH_RESULT,
    Placeholders.time(world.getFullTime())
);
player.sendMessage(msg);
```

### Example 3: Error Message with Reason
```java
Component msg = messageService.getMessage(
    MessageKey.Error.DATABASE_ERROR,
    Placeholders.reason(exception.getMessage())
);
player.sendMessage(msg);
```

## Testing

Build and verify:
```bash
./gradlew compileJava    # Verify compilation
./gradlew build          # Full build with JAR
```

Check JAR includes:
```bash
unzip -l LongHardFish.jar | grep lang/
```

## Dependencies

- **Kyori Adventure** (included in Paper API 1.21.7)
  - MiniMessage for message parsing
  - Component API for text handling

- **Bukkit API** (Paper 1.21.7)
  - Configuration file handling
  - Plugin logging

No external dependencies added.
