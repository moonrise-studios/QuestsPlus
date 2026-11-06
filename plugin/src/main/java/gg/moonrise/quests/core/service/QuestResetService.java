package gg.moonrise.quests.core.service;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.state.Reloadable;
import gg.moonrise.moss.spring.Disableable;
import gg.moonrise.moss.spring.Enableable;
import gg.moonrise.moss.spring.SpringComponent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.config.Daily;
import gg.moonrise.quests.config.GlobalSchedule;
import gg.moonrise.quests.util.QuestSchedules;
import org.bukkit.Bukkit;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestResetService implements Enableable, Reloadable, Disableable {

    private final ConfigProvider configProvider;
    private final QuestService questService;
    private final QuestClock clock;

    private volatile boolean weekly;
    private volatile DayOfWeek weeklyDay = QuestSchedules.DEFAULT_DAY;
    private volatile LocalTime resetTime = QuestSchedules.DEFAULT_TIME;
    private volatile ZonedDateTime nextResetAt;
    private volatile ScheduledTask resetTask;

    @Override
    public void onEnable() {
        reload();
    }

    @Override
    public void reload() {
        Daily daily = configProvider.get().getDaily();
        this.weekly = daily.isWeekly();
        if (weekly) {
            GlobalSchedule schedule = daily.getSchedule() == null ? new GlobalSchedule() : daily.getSchedule();
            this.weeklyDay = QuestSchedules.parseDay(schedule.getDayOfWeek(), "weekly quest");
            this.resetTime = QuestSchedules.parseTime(schedule.getTime(), "daily reset");
        } else {
            this.weeklyDay = QuestSchedules.DEFAULT_DAY;
            this.resetTime = QuestSchedules.parseTime(daily.getResetTime(), "daily reset");
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
        return QuestSchedules.formatRemaining("Resets in ", QuestSchedules.until(clock.now(), nextResetZoned()), weekly);
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
}
