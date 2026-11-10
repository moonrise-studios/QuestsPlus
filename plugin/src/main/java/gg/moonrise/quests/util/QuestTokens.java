package gg.moonrise.quests.util;

import gg.moonrise.quests.sdk.model.GeneratedQuest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared {@code <key>} placeholder rendering for quest messages, menus, indicators and reward commands.
 */
public final class QuestTokens {

    /**
     * Matches anything between angle brackets, so unknown tags are removed too (unlike
     * {@code MiniMessage.stripTags}, which keeps tags it does not recognise).
     */
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final ThreadLocal<DecimalFormat> PROGRESS_PERCENT_FORMATTER =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US)));

    private QuestTokens() {
    }

    /**
     * Replaces every {@code <key>} in {@code template} with its value, one key at a time in the map's
     * iteration order. A null template renders as an empty string.
     */
    public static @NotNull String apply(@Nullable String template, @NotNull Map<String, String> values) {
        String output = template == null ? "" : template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            output = output.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return output;
    }

    /**
     * Puts {@code goal_amount}, {@code goal-amount} and {@code progress} for {@code quest}.
     */
    public static void putQuestNumbers(@NotNull Map<String, String> values, @NotNull GeneratedQuest quest, boolean formatted) {
        values.put("goal_amount", number(quest.goalAmount(), formatted));
        values.put("goal-amount", number(quest.goalAmount(), formatted));
        values.put("progress", number(quest.progress(), formatted));
    }

    /**
     * Formats with thousands separators for display, or as a plain integer for commands.
     */
    public static @NotNull String number(int value, boolean formatted) {
        return formatted ? QuestNumberFormatter.format(value) : Integer.toString(value);
    }

    /**
     * Whole percent of {@code value} out of {@code max}, rounded down and capped at 100; {@code "0"} when
     * {@code max} is not positive.
     */
    public static @NotNull String percent(int value, int max, boolean formatted) {
        if (max <= 0) {
            return "0";
        }
        return number(Math.min(100, (int) Math.floor((value * 100.0D) / max)), formatted);
    }

    /**
     * Quest completion with two decimals and a percent sign, for example {@code 33.33%}.
     */
    public static @NotNull String progressPercent(@NotNull GeneratedQuest quest) {
        if (quest.goalAmount() <= 0) {
            return "0.00%";
        }
        double percent = Math.clamp((quest.progress() * 100.0D) / quest.goalAmount(), 0.0D, 100.0D);
        return PROGRESS_PERCENT_FORMATTER.get().format(percent) + "%";
    }

    /**
     * Removes every {@code <...>} tag and trims the result, for showing MiniMessage text as plain text.
     */
    public static @NotNull String stripTags(@Nullable String input) {
        return input == null ? "" : TAG.matcher(input).replaceAll("").trim();
    }
}
