package gg.moonrise.quests.application.reset;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.paper.scheduler.impl.SyncScheduler;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.application.quest.QuestService;
import gg.moonrise.quests.config.ConfigProvider;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestResetServiceTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final List<Consumer<ScheduledTask>> delayedTasks = new ArrayList<>();
    private final List<Duration> delays = new ArrayList<>();
    private final AtomicReference<Clock> clock = new AtomicReference<>();
    private MockedStatic<Scheduler> scheduler;
    private QuestService questService;
    private QuestResetService resetService;

    @BeforeEach
    public void setUp() {
        MockBukkit.mock();
        SyncScheduler syncScheduler = mock(SyncScheduler.class);
        when(syncScheduler.runDelayed(any(), any(Duration.class))).thenAnswer(invocation -> {
            delayedTasks.add(invocation.getArgument(0));
            delays.add(invocation.getArgument(1));
            return mock(ScheduledTask.class);
        });
        scheduler = mockStatic(Scheduler.class);
        scheduler.when(Scheduler::sync).thenReturn(syncScheduler);

        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(new Config());
        questService = mock(QuestService.class);
        resetService = new QuestResetService(configProvider, questService, clock::get);
    }

    @AfterEach
    public void tearDown() {
        scheduler.close();
        MockBukkit.unmock();
    }

    @Test
    public void dailyResetDelayAccountsForFallBack() {
        setTime(ZonedDateTime.of(2026, 10, 31, 12, 0, 0, 0, NEW_YORK));

        resetService.reload();

        assertEquals(List.of(Duration.ofHours(18)), delays);
        assertEquals(LocalDateTime.of(2026, 11, 1, 5, 0), resetService.nextResetAt());
        assertEquals("2026-10-31", resetService.currentResetKey());
        assertEquals("Resets in 18h", resetService.resetTimerPlaceholder());
    }

    @Test
    public void resetTaskFiringBeforeTheBoundaryReschedulesWithoutResetting() {
        setTime(ZonedDateTime.of(2026, 10, 31, 12, 0, 0, 0, NEW_YORK));
        resetService.reload();

        setTime(ZonedDateTime.of(2026, 11, 1, 4, 0, 0, 0, NEW_YORK));
        delayedTasks.getLast().accept(mock(ScheduledTask.class));

        verify(questService, never()).clearOnlineDailyStates();
        assertEquals(2, delayedTasks.size());
        assertEquals(Duration.ofHours(1), delays.getLast());

        setTime(ZonedDateTime.of(2026, 11, 1, 5, 0, 1, 0, NEW_YORK));
        delayedTasks.getLast().accept(mock(ScheduledTask.class));

        verify(questService, times(1)).clearOnlineDailyStates();
        assertEquals("2026-11-01", resetService.currentResetKey());
        assertEquals(3, delayedTasks.size());
        assertEquals(Duration.ofHours(24).minusSeconds(1), delays.getLast());
    }

    private void setTime(ZonedDateTime time) {
        clock.set(Clock.fixed(time.toInstant(), NEW_YORK));
    }
}
