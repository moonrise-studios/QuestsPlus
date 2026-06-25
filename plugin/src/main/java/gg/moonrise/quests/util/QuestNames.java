package gg.moonrise.quests.util;

import java.util.Locale;

public final class QuestNames {

    private QuestNames() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static String placeholderKey(String input) {
        return normalize(input).replace('-', '_');
    }
}
