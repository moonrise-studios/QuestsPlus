package gg.moonrise.quests.presentation.menu;

import gg.moonrise.quests.util.QuestNumberFormatter;
import gg.moonrise.quests.util.QuestTokens;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ordered {@code <token>} replacements applied to menu item names and lore.
 */
public class MenuTokens {
    private final Map<String, String> values = new LinkedHashMap<>();

    public MenuTokens put(String key, String value) {
        values.put(key, value == null ? "" : value);
        return this;
    }

    public MenuTokens putNumber(String key, long value) {
        values.put(key, QuestNumberFormatter.format(value));
        return this;
    }

    public String apply(String template) {
        return QuestTokens.apply(template, values);
    }
}
