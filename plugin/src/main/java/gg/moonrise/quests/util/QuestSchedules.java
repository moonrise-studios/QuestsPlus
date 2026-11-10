package gg.moonrise.quests.util;

import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Daily and weekly window boundaries in a time zone.
 *
 * <p>Boundaries are wall-clock times (for example 05:00 every day) resolved in the zone of the
 * supplied {@code now}, while comparisons and delays use instants, so daylight saving transitions
 * neither fire a reset an hour early nor flip a window back during a repeated hour.</p>
 */
@Slf4j(topic = "QuestsPlus")
public final class QuestSchedules {

    public static final DayOfWeek DEFAULT_DAY = DayOfWeek.FRIDAY;
    public static final LocalTime DEFAULT_TIME = LocalTime.of(5, 0);

    private QuestSchedules() {
    }

    /**
     * Parses a configured day of week such as {@code FRIDAY}; blank values use {@link #DEFAULT_DAY}
     * and invalid values log a warning naming {@code description} and use it too.
     */
    public static DayOfWeek parseDay(String raw, String description) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_DAY;
        }
        try {
            return DayOfWeek.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            log.warn("Invalid QuestsPlus {} day '{}'. Falling back to {}.", description, raw, DEFAULT_DAY);
            return DEFAULT_DAY;
        }
    }

    /**
     * Parses a configured {@code HH:mm} time; missing or invalid values log a warning naming
     * {@code description} and use {@link #DEFAULT_TIME}.
     */
    public static LocalTime parseTime(String raw, String description) {
        try {
            return LocalTime.parse(raw == null ? "" : raw.trim());
        } catch (DateTimeParseException exception) {
            log.warn("Invalid QuestsPlus {} time '{}'. Falling back to {}.", description, raw, DEFAULT_TIME);
            return DEFAULT_TIME;
        }
    }

    /**
     * Formats a countdown as {@code prefix} plus whole minutes, hours or (when {@code showDays}) days,
     * always rounding up so the countdown never reads zero before the boundary passes.
     */
    public static String formatRemaining(String prefix, Duration remaining, boolean showDays) {
        long remainingMillis = remaining.toMillis();
        long ceilMinutes = remainingMillis <= 0 ? 0 : (remainingMillis + 59_999) / 60_000;
        if (ceilMinutes < 60) {
            return prefix + QuestNumberFormatter.format(ceilMinutes) + "m";
        }
        long ceilHours = (ceilMinutes + 59) / 60;
        if (showDays && ceilHours >= 24) {
            long ceilDays = (ceilHours + 23) / 24;
            return prefix + QuestNumberFormatter.format(ceilDays) + "d";
        }
        return prefix + QuestNumberFormatter.format(ceilHours) + "h";
    }

    /**
     * Returns the date of the daily window containing {@code now}; the window for a date starts at
     * {@code time} on that date.
     */
    public static LocalDate dailyWindowDate(ZonedDateTime now, LocalTime time) {
        LocalDate date = now.toLocalDate();
        return now.isBefore(at(now, date, time)) ? date.minusDays(1) : date;
    }

    /**
     * Returns the next daily boundary strictly after {@code now}.
     */
    public static ZonedDateTime nextDaily(ZonedDateTime now, LocalTime time) {
        return at(now, dailyWindowDate(now, time).plusDays(1), time);
    }

    /**
     * Returns the local start of the weekly window containing {@code now}; windows start on
     * {@code day} at {@code time}.
     */
    public static LocalDateTime weeklyStart(ZonedDateTime now, DayOfWeek day, LocalTime time) {
        LocalDate date = now.toLocalDate();
        LocalDate startDate = date.minusDays(Math.floorMod(date.getDayOfWeek().getValue() - day.getValue(), 7));
        if (now.isBefore(at(now, startDate, time))) {
            startDate = startDate.minusWeeks(1);
        }
        return startDate.atTime(time);
    }

    /**
     * Returns the next weekly boundary strictly after {@code now}.
     */
    public static ZonedDateTime nextWeekly(ZonedDateTime now, DayOfWeek day, LocalTime time) {
        return at(now, weeklyStart(now, day, time).toLocalDate().plusWeeks(1), time);
    }

    /**
     * Returns the real time from {@code now} until the wall-clock {@code target} in the zone of {@code now}.
     */
    public static Duration until(ZonedDateTime now, LocalDateTime target) {
        return until(now, target.atZone(now.getZone()));
    }

    /**
     * Returns the real time from {@code now} until {@code target}.
     */
    public static Duration until(ZonedDateTime now, ZonedDateTime target) {
        return Duration.between(now.toInstant(), target.toInstant());
    }

    private static ZonedDateTime at(ZonedDateTime now, LocalDate date, LocalTime time) {
        return ZonedDateTime.of(date, time, now.getZone());
    }
}
