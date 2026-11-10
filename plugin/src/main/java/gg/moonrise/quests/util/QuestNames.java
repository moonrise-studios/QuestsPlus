package gg.moonrise.quests.util;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class QuestNames {

    private static final Pattern NON_CONSTANT_KEY_CHARACTER = Pattern.compile("[^A-Z0-9_]");

    private QuestNames() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Normalizes free-form input such as {@code " boss-bar "} or {@code "action bar"} to an upper-case
     * key like {@code BOSS_BAR}: dashes and spaces become underscores and any other character outside
     * {@code A-Z}, {@code 0-9} and {@code _} is dropped. A null input yields an empty string.
     */
    public static String constantKey(String input) {
        String upper = Objects.requireNonNullElse(input, "")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return NON_CONSTANT_KEY_CHARACTER.matcher(upper).replaceAll("");
    }
}
