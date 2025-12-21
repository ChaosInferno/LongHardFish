# I18N Quick Start Guide

## Setup (One-Time)

```java
// In LongHardFish.onEnable()
MessageService messageService = new MessageService(this);

// Store as field for access throughout plugin
this.messageService = messageService;

// or use dependency injection
```

## Add a New Message Key

### 1. Add to MessageKey.java
```java
public static final class Fishing {
    public static final String CATCH_SUCCESS = "fishing.catch.success";
    public static final String MY_NEW_KEY = "fishing.my_new_key";  // Add here
}
```

### 2. Add to src/main/resources/lang/en.yml
```yaml
fishing:
  catch:
    success: "<green>You caught...</green>"
  my_new_key: "<green>Your message here</green>"  # Add here
```

## Send Messages to Players

### Simple Message (No Replacements)
```java
Component msg = messageService.getMessage(MessageKey.Fishing.CATCH_SUCCESS);
player.sendMessage(msg);
```

### With One Placeholder
```java
Component msg = messageService.getMessage(
    MessageKey.Fishing.CATCH_SUCCESS,
    Placeholders.fish("Salmon")
);
player.sendMessage(msg);
```

### With Multiple Placeholders
```java
Component msg = messageService.getMessage(
    MessageKey.Fishing.CATCH_SUCCESS,
    Placeholders.fish("Salmon"),
    Placeholders.rarity("Rare"),
    Placeholders.player(player)
);
player.sendMessage(msg);
```

## Available Placeholders

```java
// Player
Placeholders.player("PlayerName")
Placeholders.player(playerObject)

// Numbers
Placeholders.amount(5)
Placeholders.amount(100L)
Placeholders.percent(75)

// Items & Objects
Placeholders.item("Fishing Rod")
Placeholders.fish("Salmon")
Placeholders.biome("Ocean")
Placeholders.rarity("Rare")
Placeholders.color("Blue")

// Location & Coordinates
Placeholders.location(100, 64, -200)
Placeholders.location(location)

// Time
Placeholders.time("12:30")
Placeholders.time(world.getFullTime())
Placeholders.moonPhase("Waxing Crescent")
Placeholders.moonPhase(4)  // 0-7

// Generic
Placeholders.weather("Raining")
Placeholders.reason("Player inventory full")
Placeholders.usage("/fish help")
Placeholders.value("key", "value")
```

## Message Format Examples

### Simple Colored Text
```yaml
message: "<green>Success!</green>"
message: "<red>Error!</red>"
message: "<aqua>Info message</aqua>"
```

### With Placeholders
```yaml
catch_success: "<green>You caught a <fish><gold> (<rarity>)</gold>!</green>"
```

### Nested Formatting
```yaml
important: "<bold><gold>IMPORTANT: <green>Message</green></gold></bold>"
```

### Supported Colors & Styles
- `<black>` `<dark_blue>` `<dark_green>` `<dark_aqua>` `<dark_red>` `<dark_purple>` `<gold>` `<gray>` `<dark_gray>` `<blue>` `<green>` `<aqua>` `<red>` `<light_purple>` `<yellow>` `<white>`
- `<bold>` `<italic>` `<underlined>` `<strikethrough>` `<obfuscated>`

Full MiniMessage docs: https://docs.adventure.kyori.net/minimessage.html

## Real-World Example

### Fishing catch message
```java
// In FishCatchListener.onFish()
Component msg = messageService.getMessage(
    MessageKey.Fishing.CATCH_SUCCESS,
    Placeholders.fish(fishModel.getName()),
    Placeholders.rarity(rarity.name()),
    Placeholders.player(player)
);
player.sendMessage(msg);
```

```yaml
# en.yml
fishing:
  catch:
    success: "<green>You caught a <fish><gold> (<rarity>)</gold>!</green>"
```

Output: "You caught a Clownfish (Rare)!"

## Add New Language

1. Create `lang/{language}.yml` in plugin data folder
2. Update config.yml: `language: {language}`
3. Restart plugin

Example French file:
```yaml
command:
  fishdex:
    open: "<green>FishDex ouvert!</green>"
fishing:
  catch:
    success: "<green>Vous avez attrapé un <fish><gold> (<rarity>)</gold>!</green>"
```

## Troubleshooting

### Message shows key instead of text
- Check key exists in en.yml
- Verify YAML indentation
- Restart server after adding/changing messages

### Colors/formatting not working
- Ensure MiniMessage format syntax
- Use angle brackets: `<red>text</red>` not `&c`
- Check quotes in YAML

### Placeholder not replacing
- Verify placeholder key matches message tag
- Example: message uses `<fish>` → need `Placeholders.fish()`
- Check tag resolver is passed to getMessage()

## Performance Notes

- Messages are cached in-memory after first load
- Thread-safe for concurrent access
- No per-message overhead after load
- Minimal GC impact

## Future Enhancements

- Per-player language preferences
- `/language` command for players
- Locale auto-detection
- Hot-reload without restart
- Message validation on load
