package gg.moonrise.quests.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class QuestDisplayNames {

    private static final Map<String, String> PLURAL_OVERRIDES = Map.ofEntries(
            Map.entry("charcoal", "Charcoal"),
            Map.entry("cod", "Cod"),
            Map.entry("drowned", "Drowned"),
            Map.entry("enderman", "Endermen"),
            Map.entry("fish", "Fish"),
            Map.entry("glass", "Glass"),
            Map.entry("glowstone", "Glowstone"),
            Map.entry("gunpowder", "Gunpowder"),
            Map.entry("leather", "Leather"),
            Map.entry("paper", "Paper"),
            Map.entry("pufferfish", "Pufferfish"),
            Map.entry("quartz", "Quartz"),
            Map.entry("raw_copper", "Raw Copper"),
            Map.entry("raw_gold", "Raw Gold"),
            Map.entry("raw_iron", "Raw Iron"),
            Map.entry("redstone", "Redstone"),
            Map.entry("salmon", "Salmon"),
            Map.entry("sheep", "Sheep"),
            Map.entry("silverfish", "Silverfish"),
            Map.entry("string", "String"),
            Map.entry("squid", "Squid"),
            Map.entry("tropical_fish", "Tropical Fish")
    );

    private QuestDisplayNames() {
    }

    public static String typeName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.trim();
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < normalized.length()) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }

        String[] words = normalized.toLowerCase(Locale.ROOT).split("[_\\-\\s]+");
        List<String> formatted = new ArrayList<>(words.length);
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            formatted.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return String.join(" ", formatted);
    }

    public static String typeName(String value, int amount) {
        String displayName = typeName(value);
        if (amount == 1 || displayName.isBlank()) {
            return displayName;
        }

        String key = normalizedKey(value);
        String keyOverride = PLURAL_OVERRIDES.get(key);
        if (keyOverride != null) {
            return keyOverride;
        }
        String displayOverride = PLURAL_OVERRIDES.get(displayName.toLowerCase(Locale.ROOT).replace(' ', '_'));
        if (displayOverride != null) {
            return displayOverride;
        }

        int separator = displayName.lastIndexOf(' ');
        if (separator < 0) {
            return pluralize(displayName);
        }
        return displayName.substring(0, separator + 1) + pluralize(displayName.substring(separator + 1));
    }

    private static String pluralize(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        String irregular = PLURAL_OVERRIDES.get(lower);
        if (irregular != null) {
            return irregular;
        }
        if (lower.endsWith("ch") || lower.endsWith("sh") || lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z")) {
            return word + "es";
        }
        if (lower.endsWith("y") && word.length() > 1 && !isVowel(lower.charAt(lower.length() - 2))) {
            return word.substring(0, word.length() - 1) + "ies";
        }
        return word + "s";
    }

    private static String normalizedKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < normalized.length()) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }
        return normalized.replace('-', '_').replace(' ', '_');
    }

    private static boolean isVowel(char character) {
        return character == 'a' || character == 'e' || character == 'i' || character == 'o' || character == 'u';
    }
}
