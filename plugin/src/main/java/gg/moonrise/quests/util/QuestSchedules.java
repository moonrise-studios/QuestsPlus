package gg.moonrise.quests.util;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Daily and weekly window boundaries in a time zone.
 *
 * <p>Boundaries are wall-clock times (for example 05:00 every day) resolved in the zone of the
 * supplied {@code now}, while comparisons and delays use instants, so daylight saving transitions
 * neither fire a reset an hour early nor flip a window back during a repeated hour.</p>
 */
public final class QuestSchedules {

    private QuestSchedules() {
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
