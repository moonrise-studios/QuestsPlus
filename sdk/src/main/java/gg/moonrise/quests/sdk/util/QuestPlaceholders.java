package gg.moonrise.quests.sdk.util;

import java.util.Locale;

/**
 * Utility methods for converting quest variable keys into placeholder keys.
 */
public final class QuestPlaceholders {

    private QuestPlaceholders() {
    }

    /**
     * Converts a quest variable key to an underscore placeholder key.
     *
     * @param input the variable key
     * @return a lower-case underscore placeholder key
     */
    public static String placeholderKey(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
