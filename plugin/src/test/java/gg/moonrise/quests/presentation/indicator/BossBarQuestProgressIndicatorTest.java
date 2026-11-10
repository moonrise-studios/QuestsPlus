package gg.moonrise.quests.presentation.indicator;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.paper.scheduler.impl.EntityScheduler;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.ConfigProvider;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestTypes;
import gg.moonrise.quests.application.indicator.QuestProgressIndicatorContext;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BossBarQuestProgressIndicatorTest {

    private ServerMock server;
    private MockedStatic<Scheduler> scheduler;
    private EntityScheduler entityScheduler;
    private ScheduledTask hideTask;
    private BossBarQuestProgressIndicator indicator;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        entityScheduler = mock(EntityScheduler.class);
        hideTask = mock(ScheduledTask.class);
        scheduler = mockStatic(Scheduler.class);
        scheduler.when(() -> Scheduler.entity(any())).thenReturn(entityScheduler);
        when(entityScheduler.run(ArgumentMatchers.<Consumer<ScheduledTask>>any())).thenAnswer(invocation -> {
            Consumer<ScheduledTask> task = invocation.getArgument(0);
            task.accept(mock(ScheduledTask.class));
            return mock(ScheduledTask.class);
        });
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(new Config());
        indicator = new BossBarQuestProgressIndicator(configProvider);
    }

    @AfterEach
    public void tearDown() {
        scheduler.close();
        MockBukkit.unmock();
    }

    @Test
    public void clearHidesBarAndCancelsHideTask() {
        PlayerMock player = server.addPlayer("Quitter");
        when(runDelayed()).thenReturn(hideTask);

        indicator.show(context(player));
        assertEquals(1, player.getBossBars().size());

        indicator.clear(player.getUniqueId());

        assertTrue(player.getBossBars().isEmpty());
        verify(hideTask).cancel();
    }

    @Test
    public void retiredHideTaskForgetsBar() {
        PlayerMock player = server.addPlayer("Retired");
        when(runDelayed()).thenReturn(hideTask);

        indicator.show(context(player));
        captureRetired().run();
        indicator.clear(player.getUniqueId());

        verify(hideTask, never()).cancel();
    }

    @Test
    public void unscheduledHideTaskDoesNotKeepBar() {
        PlayerMock player = server.addPlayer("Gone");
        when(runDelayed()).thenReturn(null);

        indicator.show(context(player));

        assertTrue(player.getBossBars().isEmpty());
        indicator.clearAll();
    }

    private ScheduledTask runDelayed() {
        return entityScheduler.runDelayed(ArgumentMatchers.<Consumer<ScheduledTask>>any(), any(Runnable.class), any(Duration.class));
    }

    private Runnable captureRetired() {
        ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
        verify(entityScheduler).runDelayed(ArgumentMatchers.<Consumer<ScheduledTask>>any(), retired.capture(), any(Duration.class));
        return retired.getValue();
    }

    private static QuestProgressIndicatorContext context(PlayerMock player) {
        GeneratedQuest quest = new GeneratedQuest(
                UUID.randomUUID(),
                player.getUniqueId(),
                "reset",
                "kill-mobs",
                QuestTypes.KILL_ALL_MOBS,
                "easy",
                "Easy",
                "Slayer",
                List.of(),
                Map.of(),
                10,
                5,
                false
        );
        return new QuestProgressIndicatorContext(
                player,
                QuestProgressIndicatorContext.Scope.PERSONAL,
                quest,
                null,
                4,
                0.5D,
                Map.of(),
                Component.text("Slayer")
        );
    }
}
