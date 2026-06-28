package gg.moonrise.quests.sdk.currency;

import java.util.Locale;
import java.util.Objects;

/**
 * A data-driven currency key.
 *
 * <p>Currency keys are not limited to QuestsPlus' built-in constants. External plugins may
 * create their own keys with {@link #of(String)} and register a matching currency provider
 * through the QuestsPlus API.</p>
 *
 * @param key normalized currency key
 */
public record QuestCurrencyKey(String key) {

    /**
     * Creates a currency key from a raw key.
     *
     * @param key raw key
     * @throws IllegalArgumentException when the key is blank or contains unsupported characters
     */
    public QuestCurrencyKey {
        key = normalize(key);
    }

    /**
     * Creates a currency key from a raw key.
     *
     * @param key raw key
     * @return normalized currency key
     * @throws IllegalArgumentException when the key is blank or contains unsupported characters
     */
    public static QuestCurrencyKey of(String key) {
        return new QuestCurrencyKey(key);
    }

    /**
     * Normalizes a currency key to the SDK/config representation.
     *
     * @param input raw key
     * @return normalized key
     * @throws IllegalArgumentException when the key is blank or contains unsupported characters
     */
    public static String normalize(String input) {
        String normalized = Objects.requireNonNullElse(input, "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-');
        while (normalized.contains("--")) {
            normalized = normalized.replace("--", "-");
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Currency key cannot be blank");
        }
        if (!normalized.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Currency key may only contain a-z, 0-9, and hyphen characters: " + input);
        }
        return normalized;
    }

    @Override
    public String toString() {
        return key;
    }
}
