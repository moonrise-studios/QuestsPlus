package gg.moonrise.quests.util;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestSchedulesTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalTime FIVE_AM = LocalTime.of(5, 0);

    @Test
    public void parseDayAndTimeFallBackToDefaults() {
        assertEquals(DayOfWeek.MONDAY, QuestSchedules.parseDay(" monday ", "test"));
        assertEquals(DayOfWeek.FRIDAY, QuestSchedules.parseDay(null, "test"));
        assertEquals(DayOfWeek.FRIDAY, QuestSchedules.parseDay("someday", "test"));
        assertEquals(LocalTime.of(18, 30), QuestSchedules.parseTime("18:30", "test"));
        assertEquals(FIVE_AM, QuestSchedules.parseTime(null, "test"));
        assertEquals(FIVE_AM, QuestSchedules.parseTime("25:00", "test"));
    }

    @Test
    public void formatRemainingRoundsUpToTheLargestUnit() {
        assertEquals("Ends in 0m", QuestSchedules.formatRemaining("Ends in ", Duration.ofSeconds(-5), true));
        assertEquals("Ends in 1m", QuestSchedules.formatRemaining("Ends in ", Duration.ofSeconds(1), true));
        assertEquals("Ends in 59m", QuestSchedules.formatRemaining("Ends in ", Duration.ofMinutes(59), true));
        assertEquals("Ends in 2h", QuestSchedules.formatRemaining("Ends in ", Duration.ofMinutes(61), true));
        assertEquals("Ends in 2d", QuestSchedules.formatRemaining("Ends in ", Duration.ofHours(25), true));
        assertEquals("Resets in 25h", QuestSchedules.formatRemaining("Resets in ", Duration.ofHours(25), false));
    }

    @Test
    public void dailyDelayAcrossFallBackIncludesTheRepeatedHour() {
        // 2026-11-01 02:00 EDT falls back to 01:00 EST.
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 31, 12, 0, 0, 0, NEW_YORK);

        ZonedDateTime next = QuestSchedules.nextDaily(now, FIVE_AM);

        assertEquals(LocalDateTime.of(2026, 11, 1, 5, 0), next.toLocalDateTime());
        assertEquals(ZoneOffset.ofHours(-5), next.getOffset());
        assertEquals(Duration.ofHours(18), QuestSchedules.until(now, next));
    }

    @Test
    public void dailyDelayAcrossSpringForwardSkipsTheMissingHour() {
        // 2026-03-08 02:00 EST springs forward to 03:00 EDT.
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 7, 12, 0, 0, 0, NEW_YORK);

        ZonedDateTime next = QuestSchedules.nextDaily(now, FIVE_AM);

        assertEquals(LocalDateTime.of(2026, 3, 8, 5, 0), next.toLocalDateTime());
        assertEquals(Duration.ofHours(16), QuestSchedules.until(now, next));
    }

    @Test
    public void resetTimeInsideSpringForwardGapResolvesToTheFollowingHour() {
        ZonedDateTime now = ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, NEW_YORK);

        ZonedDateTime next = QuestSchedules.nextDaily(now, LocalTime.of(2, 30));

        assertEquals(LocalDateTime.of(2026, 3, 8, 3, 30), next.toLocalDateTime());
        assertEquals(Duration.ofMinutes(90), QuestSchedules.until(now, next));
    }

    @Test
    public void dailyWindowDoesNotFlipBackDuringTheRepeatedHour() {
        LocalTime resetTime = LocalTime.of(1, 30);
        ZonedDateTime firstPass = ZonedDateTime.ofLocal(LocalDateTime.of(2026, 11, 1, 1, 45), NEW_YORK, ZoneOffset.ofHours(-4));
        ZonedDateTime secondPass = ZonedDateTime.ofLocal(LocalDateTime.of(2026, 11, 1, 1, 0), NEW_YORK, ZoneOffset.ofHours(-5));

        assertEquals(LocalDate.of(2026, 11, 1), QuestSchedules.dailyWindowDate(firstPass, resetTime));
        assertEquals(LocalDate.of(2026, 11, 1), QuestSchedules.dailyWindowDate(secondPass, resetTime));
        assertEquals(LocalDateTime.of(2026, 11, 2, 1, 30), QuestSchedules.nextDaily(secondPass, resetTime).toLocalDateTime());
    }

    @Test
    public void dailyWindowStartsAtTheResetTime() {
        ZonedDateTime beforeReset = ZonedDateTime.of(2026, 6, 10, 4, 59, 0, 0, NEW_YORK);
        ZonedDateTime atReset = ZonedDateTime.of(2026, 6, 10, 5, 0, 0, 0, NEW_YORK);

        assertEquals(LocalDate.of(2026, 6, 9), QuestSchedules.dailyWindowDate(beforeReset, FIVE_AM));
        assertEquals(LocalDate.of(2026, 6, 10), QuestSchedules.dailyWindowDate(atReset, FIVE_AM));
        assertEquals(LocalDateTime.of(2026, 6, 10, 5, 0), QuestSchedules.nextDaily(beforeReset, FIVE_AM).toLocalDateTime());
        assertEquals(LocalDateTime.of(2026, 6, 11, 5, 0), QuestSchedules.nextDaily(atReset, FIVE_AM).toLocalDateTime());
    }

    @Test
    public void weeklyWindowAcrossFallBack() {
        // Friday 2026-10-30 12:00 EDT; the next Friday 05:00 is after the fall-back transition.
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 30, 12, 0, 0, 0, NEW_YORK);

        assertEquals(LocalDateTime.of(2026, 10, 30, 5, 0), QuestSchedules.weeklyStart(now, DayOfWeek.FRIDAY, FIVE_AM));
        ZonedDateTime next = QuestSchedules.nextWeekly(now, DayOfWeek.FRIDAY, FIVE_AM);
        assertEquals(LocalDateTime.of(2026, 11, 6, 5, 0), next.toLocalDateTime());
        assertEquals(Duration.ofDays(6).plusHours(18), QuestSchedules.until(now, next));
        assertEquals(Duration.ofDays(6).plusHours(18), QuestSchedules.until(now, LocalDateTime.of(2026, 11, 6, 5, 0)));
    }

    @Test
    public void weeklyWindowBeforeStartBelongsToPreviousWeek() {
        ZonedDateTime fridayBeforeStart = ZonedDateTime.of(2026, 10, 30, 4, 0, 0, 0, NEW_YORK);
        ZonedDateTime wednesday = ZonedDateTime.of(2026, 10, 28, 12, 0, 0, 0, NEW_YORK);

        assertEquals(LocalDateTime.of(2026, 10, 23, 5, 0), QuestSchedules.weeklyStart(fridayBeforeStart, DayOfWeek.FRIDAY, FIVE_AM));
        assertEquals(LocalDateTime.of(2026, 10, 23, 5, 0), QuestSchedules.weeklyStart(wednesday, DayOfWeek.FRIDAY, FIVE_AM));
        assertEquals(LocalDateTime.of(2026, 10, 30, 5, 0), QuestSchedules.nextWeekly(fridayBeforeStart, DayOfWeek.FRIDAY, FIVE_AM).toLocalDateTime());
    }
}
