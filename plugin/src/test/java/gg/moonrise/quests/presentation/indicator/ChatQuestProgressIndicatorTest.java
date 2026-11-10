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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatQuestProgressIndicatorTest {

    private ServerMock server;
    private MockedStatic<Scheduler> scheduler;
    private EntityScheduler entityScheduler;
    private ChatQuestProgressIndicator indicator;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        entityScheduler = mock(EntityScheduler.class);
        scheduler = mockStatic(Scheduler.class);
        scheduler.when(() -> Scheduler.entity(any())).thenReturn(entityScheduler);
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(new Config());
        indicator = new ChatQuestProgressIndicator(configProvider);
    }

    @AfterEach
    public void tearDown() {
        scheduler.close();
        MockBukkit.unmock();
    }

    @Test
    public void batchedProgressIsFlushedAsOneSummary() {
        PlayerMock player = server.addPlayer("Batcher");
        when(runDelayed()).thenReturn(mock(ScheduledTask.class));
        GeneratedQuest quest = quest(5);

        indicator.show(context(player, quest, 0));
        indicator.show(context(player, quest(quest.instanceId(), 8), 5));

        verify(entityScheduler, times(1)).runDelayed(ArgumentMatchers.<Consumer<ScheduledTask>>any(), any(Runnable.class), any(Duration.class));
        captureScheduled().run.getValue().accept(mock(ScheduledTask.class));

        assertEquals("Quest Progress", plain(player.nextComponentMessage()));
        assertEquals("- Miner: 0 ➜ 8 / 10 (80.00%)", plain(player.nextComponentMessage()));
        assertNull(player.nextComponentMessage());
    }

    @Test
    public void retiredFlushTaskDropsBatchSoLaterProgressSchedulesAgain() {
        PlayerMock player = server.addPlayer("Rejoiner");
        when(runDelayed()).thenReturn(mock(ScheduledTask.class));

        indicator.show(context(player, quest(3), 0));
        captureScheduled().retired.getValue().run();
        indicator.show(context(player, quest(4), 3));

        verify(entityScheduler, times(2)).runDelayed(ArgumentMatchers.<Consumer<ScheduledTask>>any(), any(Runnable.class), any(Duration.class));
        captureScheduled(2).run.getAllValues().getLast().accept(mock(ScheduledTask.class));
        assertEquals("Quest Progress", plain(player.nextComponentMessage()));
    }

    @Test
    public void unscheduledFlushTaskDoesNotLeaveDeadBatch() {
        PlayerMock player = server.addPlayer("Retired");
        when(runDelayed()).thenReturn(null, mock(ScheduledTask.class));

        indicator.show(context(player, quest(3), 0));
        indicator.show(context(player, quest(4), 3));

        verify(entityScheduler, times(2)).runDelayed(ArgumentMatchers.<Consumer<ScheduledTask>>any(), any(Runnable.class), any(Duration.class));
    }

    @Test
    public void staleRetiredCallbackDoesNotRemoveNewerBatch() {
        PlayerMock player = server.addPlayer("Stale");
        when(runDelayed()).thenReturn(mock(ScheduledTask.class));

        indicator.show(context(player, quest(3), 0));
        Scheduled first = captureScheduled();
        first.run.getValue().accept(mock(ScheduledTask.class));
        player.nextComponentMessage();
        player.nextComponentMessage();

        indicator.show(context(player, quest(4), 3));
        first.retired.getValue().run();
        indicator.show(context(player, quest(5), 4));

        verify(entityScheduler, times(2)).runDelayed(ArgumentMatchers.<Consumer<ScheduledTask>>any(), any(Runnable.class), any(Duration.class));
    }

    @Test
    public void clearCancelsPendingFlushAndForgetsBatch() {
        PlayerMock player = server.addPlayer("Quitter");
        ScheduledTask flushTask = mock(ScheduledTask.class);
        when(runDelayed()).thenReturn(flushTask);

        indicator.show(context(player, quest(3), 0));
        indicator.clear(player.getUniqueId());
        indicator.show(context(player, quest(4), 3));

        verify(flushTask).cancel();
        verify(entityScheduler, times(2)).runDelayed(ArgumentMatchers.<Consumer<ScheduledTask>>any(), any(Runnable.class), any(Duration.class));
    }

    private ScheduledTask runDelayed() {
        return entityScheduler.runDelayed(ArgumentMatchers.<Consumer<ScheduledTask>>any(), any(Runnable.class), any(Duration.class));
    }

    private Scheduled captureScheduled() {
        return captureScheduled(1);
    }

    @SuppressWarnings("unchecked")
    private Scheduled captureScheduled(int expectedCalls) {
        ArgumentCaptor<Consumer<ScheduledTask>> run = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
        verify(entityScheduler, times(expectedCalls)).runDelayed(run.capture(), retired.capture(), any(Duration.class));
        return new Scheduled(run, retired);
    }

    private static QuestProgressIndicatorContext context(PlayerMock player, GeneratedQuest quest, int previousProgress) {
        return new QuestProgressIndicatorContext(
                player,
                QuestProgressIndicatorContext.Scope.PERSONAL,
                quest,
                null,
                previousProgress,
                quest.progress() / (double) quest.goalAmount(),
                Map.of("quest_display_name", "Miner", "percent", String.format(Locale.ROOT, "%.2f%%", quest.progress() * 10.0D)),
                Component.empty()
        );
    }

    private static GeneratedQuest quest(int progress) {
        return quest(UUID.randomUUID(), progress);
    }

    private static GeneratedQuest quest(UUID instanceId, int progress) {
        return new GeneratedQuest(
                instanceId,
                UUID.randomUUID(),
                "reset",
                "mine-stone",
                QuestTypes.KILL_ALL_MOBS,
                "easy",
                "Easy",
                "Miner",
                List.of(),
                Map.of(),
                10,
                progress,
                false
        );
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private record Scheduled(ArgumentCaptor<Consumer<ScheduledTask>> run, ArgumentCaptor<Runnable> retired) {
    }
}
