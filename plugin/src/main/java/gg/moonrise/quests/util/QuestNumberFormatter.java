package gg.moonrise.quests.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class QuestNumberFormatter {

    private static final ThreadLocal<DecimalFormat> FORMATTER = ThreadLocal.withInitial(() -> {
        DecimalFormat formatter = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.US));
        formatter.setParseIntegerOnly(true);
        return formatter;
    });

    private QuestNumberFormatter() {
    }

    public static String format(long value) {
        return FORMATTER.get().format(value);
    }

    public static String format(int value) {
        return format((long) value);
    }
}
