package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.sdk.model.QuestDefinitionSchedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class QuestDefinitionSchedules {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private QuestDefinitionSchedules() {
    }

    public static QuestDefinitionSchedule parse(Config.QuestScheduleConfig schedule, String definitionId) {
        if (schedule == null) {
            return QuestDefinitionSchedule.alwaysActive();
        }

        Instant begin = parseBound(schedule.getBegin(), definitionId, "begin");
        Instant end = parseBound(schedule.getEnd(), definitionId, "end");
        if (begin != null && end != null && !end.isAfter(begin)) {
            throw new IllegalArgumentException(definitionId + " schedule end must be after begin");
        }
        return new QuestDefinitionSchedule(begin, end);
    }

    private static Instant parseBound(Config.QuestScheduleBoundConfig bound, String definitionId, String name) {
        if (bound == null || blank(bound.getDate()) && blank(bound.getTime()) && blank(bound.getTimezone())) {
            return null;
        }
        if (blank(bound.getDate()) || blank(bound.getTime()) || blank(bound.getTimezone())) {
            throw new IllegalArgumentException(definitionId + " schedule " + name + " requires date, time, and timezone");
        }
        try {
            LocalDate date = LocalDate.parse(bound.getDate().trim(), DATE_FORMAT);
            LocalTime time = LocalTime.parse(bound.getTime().trim());
            ZoneId zoneId = ZoneId.of(bound.getTimezone().trim());
            return date.atTime(time).atZone(zoneId).toInstant();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(definitionId + " has invalid schedule " + name + " value", exception);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
