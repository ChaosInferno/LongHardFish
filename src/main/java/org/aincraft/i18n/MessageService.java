package org.aincraft.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

/**
 * MessageService handles loading, caching, and retrieving localized messages.
 * Supports MiniMessage format with placeholder replacement and fallback to English.
 * Thread-safe for concurrent message lookups.
 */
public class MessageService {
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;
    private final Map<String, Map<String, String>> messageCache; // language -> (key -> message)
    private final ReentrantReadWriteLock cacheLock;
    private String currentLanguage;
    private static final String LANG_FOLDER = "lang";
    private static final String DEFAULT_LANGUAGE = "en";

    public MessageService(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.messageCache = new HashMap<>();
        this.cacheLock = new ReentrantReadWriteLock();
        this.currentLanguage = DEFAULT_LANGUAGE;

        // Load initial messages
        reload();
    }

    /**
     * Reload all messages from disk.
     * First loads the default language, then the configured language.
     */
    public void reload() {
        cacheLock.writeLock().lock();
        try {
            messageCache.clear();

            // Load configured language from config
            String configLanguage = plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
            this.currentLanguage = configLanguage != null ? configLanguage : DEFAULT_LANGUAGE;

            // Always load default language first (fallback)
            loadLanguage(DEFAULT_LANGUAGE);

            // Load configured language if different
            if (!this.currentLanguage.equals(DEFAULT_LANGUAGE)) {
                loadLanguage(this.currentLanguage);
            }

            plugin.getLogger().info("Messages loaded for language: " + this.currentLanguage);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Load a specific language's messages from both bundled resources and data folder overrides.
     */
    private void loadLanguage(@NotNull String language) {
        Map<String, String> messages = new HashMap<>();

        // 1. Load from bundled resource (default messages)
        String resourcePath = LANG_FOLDER + "/" + language + ".yml";
        InputStream resource = plugin.getResource(resourcePath);
        if (resource != null) {
            try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
                FileConfiguration defaultLang = YamlConfiguration.loadConfiguration(reader);
                flattenMessages(defaultLang, "", messages);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load bundled language file: " + resourcePath, e);
            }
        } else if (language.equals(DEFAULT_LANGUAGE)) {
            plugin.getLogger().warning("Default language file (" + resourcePath + ") not found in resources");
        }

        // 2. Load from data folder (user overrides)
        File dataFolderLang = new File(plugin.getDataFolder(), LANG_FOLDER);
        if (!dataFolderLang.exists()) {
            dataFolderLang.mkdirs();
        }

        File languageFile = new File(dataFolderLang, language + ".yml");
        if (languageFile.exists()) {
            try {
                FileConfiguration fileLang = YamlConfiguration.loadConfiguration(languageFile);
                flattenMessages(fileLang, "", messages);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load language file: " + languageFile.getAbsolutePath(), e);
            }
        }

        messageCache.put(language, messages);
    }

    /**
     * Recursively flatten YAML configuration to dotted keys.
     * E.g., command: { fishdex: { open: "..." } } becomes "command.fishdex.open" = "..."
     */
    private void flattenMessages(FileConfiguration config, String prefix, Map<String, String> messages) {
        flattenMessagesSection(config, prefix, messages);
    }

    /**
     * Helper method to flatten any configuration section (used recursively).
     */
    private void flattenMessagesSection(org.bukkit.configuration.ConfigurationSection section, String prefix, Map<String, String> messages) {
        for (String key : section.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = section.get(key);

            if (section.isConfigurationSection(key)) {
                // Recurse into nested sections
                flattenMessagesSection(section.getConfigurationSection(key), fullKey, messages);
            } else if (value instanceof String) {
                messages.put(fullKey, (String) value);
            }
        }
    }

    /**
     * Get a message by key with no placeholder replacement.
     * Falls back to the message key itself if not found.
     */
    @NotNull
    public Component getMessage(@NotNull String messageKey) {
        return getMessage(messageKey, (TagResolver) null);
    }

    /**
     * Get a message by key with placeholder replacement using a single TagResolver.
     */
    @NotNull
    public Component getMessage(@NotNull String messageKey, @Nullable TagResolver resolver) {
        String rawMessage = getRawMessage(messageKey);
        if (resolver == null) {
            resolver = StandardTags.defaults();
        }
        return miniMessage.deserialize(rawMessage, resolver);
    }

    /**
     * Get a message by key with placeholder replacement using multiple TagResolvers.
     */
    @NotNull
    public Component getMessage(@NotNull String messageKey, @NotNull TagResolver... resolvers) {
        String rawMessage = getRawMessage(messageKey);
        TagResolver[] allResolvers = new TagResolver[resolvers.length + 1];
        allResolvers[0] = StandardTags.defaults();
        System.arraycopy(resolvers, 0, allResolvers, 1, resolvers.length);
        return miniMessage.deserialize(rawMessage, TagResolver.resolver(allResolvers));
    }

    /**
     * Get the raw (unparsed) message string.
     * Falls back to message key if not found.
     */
    @NotNull
    public String getRawMessage(@NotNull String messageKey) {
        cacheLock.readLock().lock();
        try {
            // Try current language first
            String message = messageCache.getOrDefault(currentLanguage, new HashMap<>()).get(messageKey);
            if (message != null) {
                return message;
            }

            // Fallback to default language if different
            if (!currentLanguage.equals(DEFAULT_LANGUAGE)) {
                message = messageCache.getOrDefault(DEFAULT_LANGUAGE, new HashMap<>()).get(messageKey);
                if (message != null) {
                    return message;
                }
            }

            // Final fallback: return the key itself
            return messageKey;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Check if a message key exists in the current language.
     */
    public boolean hasMessage(@NotNull String messageKey) {
        cacheLock.readLock().lock();
        try {
            Map<String, String> messages = messageCache.get(currentLanguage);
            return messages != null && messages.containsKey(messageKey);
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Get the current active language.
     */
    @NotNull
    public String getCurrentLanguage() {
        cacheLock.readLock().lock();
        try {
            return currentLanguage;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Set the active language and reload messages.
     */
    public void setLanguage(@NotNull String language) {
        cacheLock.writeLock().lock();
        try {
            if (!messageCache.containsKey(language)) {
                loadLanguage(language);
            }
            this.currentLanguage = language;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Get message count for a specific language.
     */
    public int getMessageCount(@NotNull String language) {
        cacheLock.readLock().lock();
        try {
            Map<String, String> messages = messageCache.get(language);
            return messages == null ? 0 : messages.size();
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Get all available languages (based on what's been loaded).
     */
    @NotNull
    public java.util.Set<String> getAvailableLanguages() {
        cacheLock.readLock().lock();
        try {
            return new java.util.HashSet<>(messageCache.keySet());
        } finally {
            cacheLock.readLock().unlock();
        }
    }
}
