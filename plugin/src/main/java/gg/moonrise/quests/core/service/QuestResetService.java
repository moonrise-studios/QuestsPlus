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
import org.bukkit.Bukkit;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestResetService implements Enableable, Reloadable, Disableable {

    private final ConfigProvider configProvider;
    private final QuestService questService;

    private volatile boolean weekly;
    private volatile DayOfWeek weeklyDay = DayOfWeek.FRIDAY;
    private volatile LocalTime resetTime = LocalTime.of(5, 0);
    private volatile LocalDateTime nextResetAt;
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
        LocalDateTime now = LocalDateTime.now();
        if (weekly) {
            return weeklyStart(now).toLocalDate().toString();
        }
        LocalDate date = now.toLocalTime().isBefore(resetTime) ? now.toLocalDate().minusDays(1) : now.toLocalDate();
        return date.toString();
    }

    public LocalDateTime nextResetAt() {
        LocalDateTime next = nextResetAt;
        if (next == null) {
            return nextResetAfter(LocalDateTime.now());
        }
        return next;
    }

    public String resetTimerPlaceholder() {
        long remainingMillis = Duration.between(LocalDateTime.now(), nextResetAt()).toMillis();
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

    private void scheduleNextReset() {
        ScheduledTask previous = resetTask;
        if (previous != null) {
            previous.cancel();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = nextResetAfter(now);
        this.nextResetAt = next;
        Duration delay = Duration.between(now, next);
        if (delay.isNegative() || delay.isZero()) {
            delay = Duration.ofSeconds(1);
        }
        resetTask = Scheduler.sync().runDelayed(task -> {
            questService.clearOnlineDailyStates();
            Bukkit.getOnlinePlayers().forEach(player -> questService.ensurePlayerStateAsync(player, currentResetKey()));
            Bukkit.broadcast(configProvider.get().getMessages().getQuestsResetBroadcast().asComponent());
            scheduleNextReset();
        }, delay);
        log.info("Scheduled next QuestsPlus {} reset for {}.", weekly ? "weekly" : "daily", next);
    }

    private LocalDateTime nextResetAfter(LocalDateTime now) {
        if (weekly) {
            LocalDateTime next = weeklyStart(now);
            if (!next.isAfter(now)) {
                next = next.plusWeeks(1);
            }
            return next;
        }
        LocalDateTime next = now.toLocalDate().atTime(resetTime);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return next;
    }

    private LocalDateTime weeklyStart(LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        int delta = Math.floorMod(date.getDayOfWeek().getValue() - weeklyDay.getValue(), 7);
        LocalDateTime start = date.minusDays(delta).atTime(resetTime);
        if (now.isBefore(start)) {
            start = start.minusWeeks(1);
        }
        return start;
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
