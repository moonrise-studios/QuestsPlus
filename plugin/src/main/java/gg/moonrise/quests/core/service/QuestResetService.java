package gg.moonrise.quests.core.service;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.state.Reloadable;
import gg.moonrise.moss.spring.Disableable;
import gg.moonrise.moss.spring.Enableable;
import gg.moonrise.moss.spring.SpringComponent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.util.QuestNumberFormatter;
import gg.moonrise.quests.util.QuestSchedules;
import org.bukkit.Bukkit;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestResetService implements Enableable, Reloadable, Disableable {

    private final ConfigProvider configProvider;
    private final QuestService questService;
    private final QuestClock clock;

    private volatile boolean weekly;
    private volatile DayOfWeek weeklyDay = DayOfWeek.FRIDAY;
    private volatile LocalTime resetTime = LocalTime.of(5, 0);
    private volatile ZonedDateTime nextResetAt;
    private volatile ScheduledTask resetTask;

    @Override
    public void onEnable() {
        reload();
    }

    @Override
    public void reload() {
        Config.Daily daily = configProvider.get().getDaily();
        this.weekly = daily.isWeekly();
        if (weekly) {
            Config.GlobalSchedule schedule = daily.getSchedule() == null ? new Config.GlobalSchedule() : daily.getSchedule();
            this.weeklyDay = parseDay(schedule.getDayOfWeek());
            this.resetTime = parseResetTime(schedule.getTime());
        } else {
            this.weeklyDay = DayOfWeek.FRIDAY;
            this.resetTime = parseResetTime(daily.getResetTime());
        }
        scheduleNextReset();
    }

    @Override
    public void onDisable() {
        ScheduledTask task = resetTask;
        if (task != null) {
            task.cancel();
        }
    }

    public String currentResetKey() {
        return resetKeyAt(clock.now());
    }

    public LocalDateTime nextResetAt() {
        return nextResetZoned().toLocalDateTime();
    }

    public String resetTimerPlaceholder() {
        long remainingMillis = QuestSchedules.until(clock.now(), nextResetZoned()).toMillis();
        long ceilMinutes = remainingMillis <= 0 ? 0 : (remainingMillis + 59_999) / 60_000;
        if (ceilMinutes < 60) {
            return "Resets in " + QuestNumberFormatter.format(ceilMinutes) + "m";
        }
        long ceilHours = (ceilMinutes + 59) / 60;
        if (weekly && ceilHours >= 24) {
            long ceilDays = (ceilHours + 23) / 24;
            return "Resets in " + QuestNumberFormatter.format(ceilDays) + "d";
        }
        return "Resets in " + QuestNumberFormatter.format(ceilHours) + "h";
    }

    private ZonedDateTime nextResetZoned() {
        ZonedDateTime next = nextResetAt;
        return next == null ? nextResetAfter(clock.now()) : next;
    }

    private void scheduleNextReset() {
        ScheduledTask previous = resetTask;
        if (previous != null) {
            previous.cancel();
        }
        ZonedDateTime now = clock.now();
        ZonedDateTime next = nextResetAfter(now);
        String scheduledKey = resetKeyAt(now);
        this.nextResetAt = next;
        Duration delay = QuestSchedules.until(now, next);
        if (delay.isNegative() || delay.isZero()) {
            delay = Duration.ofSeconds(1);
        }
        resetTask = Scheduler.sync().runDelayed(task -> onResetTaskFired(scheduledKey), delay);
        log.info("Scheduled next QuestsPlus {} reset for {}.", weekly ? "weekly" : "daily", next);
    }

    private void onResetTaskFired(String scheduledKey) {
        String resetKey = currentResetKey();
        if (resetKey.equals(scheduledKey)) {
            // Tick timing or a clock adjustment fired the task before the boundary; wait for the real one.
            log.debug("QuestsPlus reset task fired before the {} window ended; rescheduling.", scheduledKey);
            scheduleNextReset();
            return;
        }
        questService.clearOnlineDailyStates();
        Bukkit.getOnlinePlayers().forEach(player -> questService.ensurePlayerStateAsync(player, resetKey));
        Bukkit.broadcast(configProvider.get().getMessages().getQuestsResetBroadcast().asComponent());
        scheduleNextReset();
    }

    private String resetKeyAt(ZonedDateTime now) {
        if (weekly) {
            return QuestSchedules.weeklyStart(now, weeklyDay, resetTime).toLocalDate().toString();
        }
        return QuestSchedules.dailyWindowDate(now, resetTime).toString();
    }

    private ZonedDateTime nextResetAfter(ZonedDateTime now) {
        return weekly ? QuestSchedules.nextWeekly(now, weeklyDay, resetTime) : QuestSchedules.nextDaily(now, resetTime);
    }

    private DayOfWeek parseDay(String raw) {
        if (raw == null || raw.isBlank()) {
            return DayOfWeek.FRIDAY;
        }
        try {
            return DayOfWeek.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            log.warn("Invalid QuestsPlus weekly quest day '{}'. Falling back to FRIDAY.", raw);
            return DayOfWeek.FRIDAY;
        }
    }

    private LocalTime parseResetTime(String raw) {
        try {
            return LocalTime.parse(raw);
        } catch (DateTimeParseException exception) {
            log.warn("Invalid QuestsPlus daily reset time '{}'. Falling back to 05:00.", raw);
            return LocalTime.of(5, 0);
        }
    }
}
