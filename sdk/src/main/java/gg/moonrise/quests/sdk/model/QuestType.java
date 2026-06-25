package gg.moonrise.quests.sdk.model;

import java.util.Locale;
import java.util.Objects;

/**
 * A data-driven quest type key.
 *
 * <p>Quest types are not limited to QuestsPlus' built-in constants. External plugins may
 * create their own keys with {@link #of(String)} and register a matching goal handler
 * through the QuestsPlus API.</p>
 *
 * @param key the normalized quest type key
 */
public record QuestType(String key) {

    /**
     * Creates a quest type from a raw key.
     *
     * @param key the raw quest type key
     * @throws IllegalArgumentException when the key is blank or contains unsupported characters
     */
    public QuestType {
        key = normalize(key);
    }

    /**
     * Creates a quest type from a raw key.
     *
     * @param key the raw quest type key
     * @return a normalized quest type
     * @throws IllegalArgumentException when the key is blank or contains unsupported characters
     */
    public static QuestType of(String key) {
        return new QuestType(key);
    }

    /**
     * Normalizes a quest type key to the config/storage representation.
     *
     * @param input the raw key
     * @return the normalized key
     * @throws IllegalArgumentException when the key is blank or contains unsupported characters
     */
    public static String normalize(String input) {
        String normalized = Objects.requireNonNullElse(input, "")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Quest type key cannot be blank");
        }
        if (!normalized.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("Quest type key may only contain A-Z, 0-9, and underscore characters: " + input);
        }
        return normalized;
    }

    @Override
    public String toString() {
        return key;
    }
}
