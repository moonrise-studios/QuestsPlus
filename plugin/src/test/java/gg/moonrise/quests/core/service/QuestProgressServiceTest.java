package gg.moonrise.quests.core.service;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.paper.scheduler.impl.EntityScheduler;
import gg.moonrise.engine.paper.scheduler.impl.SyncScheduler;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.DailyFile;
import gg.moonrise.quests.domain.model.PlayerQuestState;
import gg.moonrise.quests.domain.model.QuestCompletionStats;
import gg.moonrise.quests.domain.model.QuestDifficulty;
import gg.moonrise.quests.sdk.event.QuestProgressEvent;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestProgressResult;
import gg.moonrise.quests.sdk.model.QuestTypes;
import gg.moonrise.quests.infrastructure.persistence.QuestRepository;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestProgressServiceTest {

    private static final String RESET_KEY = "daily";

    private final UUID playerId = UUID.randomUUID();
    private QuestDefinitionService definitionService;
    private QuestRepository repository;
    private GlobalQuestService globalQuestService;
    private Player player;
    private QuestServices service;
    private PluginManager pluginManager;
    private EntityScheduler entityScheduler;
    private MockedStatic<Scheduler> scheduler;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    public void setUp() {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(Config.compose(null, new DailyFile(3), null, null, null, null, null));
        definitionService = mock(QuestDefinitionService.class);
        repository = mock(QuestRepository.class);
        globalQuestService = mock(GlobalQuestService.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Steve");
        service = QuestServices.create(configProvider, definitionService, repository, mock(QuestStreakService.class),
                globalQuestService, mock(QuestProgressIndicatorService.class));

        when(definitionService.definition(any(GeneratedQuest.class))).thenReturn(Optional.of(definition()));
        when(definitionService.difficulty("easy")).thenReturn(Optional.of(new QuestDifficulty("easy", "Easy", 0, 0, Map.of(), List.of("give <player> diamond"), List.of(), List.of())));
        when(repository.updateProgress(any())).thenReturn(CompletableFuture.completedFuture(new QuestRepository.ProgressUpdateOutcome.Updated()));
        when(repository.incrementCompletionStats(eq(playerId), eq("easy"), any()))
                .thenReturn(CompletableFuture.completedFuture(new QuestCompletionStats(1, 1, List.of())));

        pluginManager = mock(PluginManager.class);
        entityScheduler = mock(EntityScheduler.class);
        SyncScheduler syncScheduler = mock(SyncScheduler.class);
        when(syncScheduler.run(any())).thenAnswer(invocation -> {
            Consumer<ScheduledTask> task = invocation.getArgument(0);
            task.accept(mock(ScheduledTask.class));
            return mock(ScheduledTask.class);
        });
        scheduler = mockStatic(Scheduler.class);
        scheduler.when(() -> Scheduler.entity(any())).thenReturn(entityScheduler);
        scheduler.when(Scheduler::sync).thenReturn(syncScheduler);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
    }

    @AfterEach
    public void tearDown() {
        bukkit.close();
        scheduler.close();
    }

    @Test
    public void progressPastTheGoalIsClampedAndCompletionRunsRewards() {
        GeneratedQuest quest = quest(0, false, 8, false);
        PlayerQuestState state = load(quest);

        List<QuestProgressResult> results = service.progress().progressMatching(player, QuestTypes.KILL_MOB, RESET_KEY, 5, candidate -> true);

        assertEquals(1, results.size());
        assertTrue(results.getFirst().completedNow());
        assertEquals(10, state.questAtSlot(0).orElseThrow().progress());
        verify(repository).updateProgress(quest.withProgress(10, true));
        verify(repository).incrementCompletionStats(eq(playerId), eq("easy"), any());
        bukkit.verify(() -> Bukkit.dispatchCommand(any(), eq("give Steve diamond")));
    }

    @Test
    public void progressBelowTheGoalIsStoredWithoutRunningRewards() {
        GeneratedQuest quest = quest(0, false, 2, false);
        PlayerQuestState state = load(quest);

        List<QuestProgressResult> results = service.progress().progressMatching(player, QuestTypes.KILL_MOB, RESET_KEY, 3, candidate -> true);

        assertFalse(results.getFirst().completedNow());
        assertEquals(5, state.questAtSlot(0).orElseThrow().progress());
        verify(repository).updateProgress(quest.withProgress(5, false));
        verify(repository, never()).incrementCompletionStats(any(), anyString(), any());
        bukkit.verify(() -> Bukkit.dispatchCommand(any(), anyString()), never());
    }

    @Test
    public void cancelledProgressEventLeavesTheQuestUntouched() {
        PlayerQuestState state = load(quest(0, false, 2, false));
        doAnswer(invocation -> {
            invocation.<QuestProgressEvent>getArgument(0).setCancelled(true);
            return null;
        }).when(pluginManager).callEvent(any(QuestProgressEvent.class));

        List<QuestProgressResult> results = service.progress().progressMatching(player, QuestTypes.KILL_MOB, RESET_KEY, 3, candidate -> true);

        assertTrue(results.isEmpty());
        assertEquals(2, state.questAtSlot(0).orElseThrow().progress());
        verify(repository, never()).updateProgress(any());
    }

    @Test
    public void amountChangedByAListenerIsCreditedInsteadOfTheOriginal() {
        PlayerQuestState state = load(quest(0, false, 2, false));
        doAnswer(invocation -> {
            invocation.<QuestProgressEvent>getArgument(0).setAmount(1);
            return null;
        }).when(pluginManager).callEvent(any(QuestProgressEvent.class));

        service.progress().progressMatching(player, QuestTypes.KILL_MOB, RESET_KEY, 3, candidate -> true);

        assertEquals(3, state.questAtSlot(0).orElseThrow().progress());
    }

    @Test
    public void nonPositiveAmountsAreIgnoredWithoutFiringAnEvent() {
        PlayerQuestState state = load(quest(0, false, 2, false));

        assertTrue(service.progress().progressMatching(player, QuestTypes.KILL_MOB, RESET_KEY, 0, candidate -> true).isEmpty());
        assertTrue(service.progress().progressAdminGoal(player, RESET_KEY, -2, QuestTypes.KILL_MOB).isEmpty());
        assertTrue(service.progress().progressAdminGoal(player, RESET_KEY, -2, "zombies").isEmpty());

        assertEquals(2, state.questAtSlot(0).orElseThrow().progress());
        verify(pluginManager, never()).callEvent(any());
    }

    @Test
    public void completedAndLockedPremiumQuestsReceiveNoProgress() {
        GeneratedQuest completed = quest(0, false, 10, true);
        GeneratedQuest open = quest(1, false, 0, false);
        GeneratedQuest lockedPremium = quest(3, true, 0, false);
        PlayerQuestState state = load(completed, open, lockedPremium);

        List<QuestProgressResult> results = service.progress().progressMatching(player, QuestTypes.KILL_MOB, RESET_KEY, 1, candidate -> true);

        assertEquals(List.of(open.instanceId()), results.stream().map(result -> result.quest().instanceId()).toList());
        assertEquals(0, state.questAtSlot(3).orElseThrow().progress());
        verify(pluginManager, times(1)).callEvent(any(QuestProgressEvent.class));
    }

    @Test
    public void questWhoseDefinitionIsNotLoadedIsSkipped() {
        PlayerQuestState state = load(quest(0, false, 2, false));
        when(definitionService.definition(any(GeneratedQuest.class))).thenReturn(Optional.empty());

        List<QuestProgressResult> results = service.progress().progressMatching(player, QuestTypes.KILL_MOB, RESET_KEY, 1, candidate -> true);

        assertTrue(results.isEmpty());
        assertEquals(2, state.questAtSlot(0).orElseThrow().progress());
        verify(pluginManager, never()).callEvent(any());
    }

    @Test
    public void missingProgressRowRollsTheCacheBackAndSkipsRewards() {
        GeneratedQuest quest = quest(0, false, 9, false);
        PlayerQuestState state = load(quest);
        when(repository.updateProgress(any())).thenReturn(CompletableFuture.completedFuture(new QuestRepository.ProgressUpdateOutcome.QuestMissing()));

        service.progress().progressMatching(player, QuestTypes.KILL_MOB, RESET_KEY, 1, candidate -> true);

        assertEquals(quest, state.questAtSlot(0).orElseThrow());
        verify(repository, never()).incrementCompletionStats(any(), anyString(), any());
        bukkit.verify(() -> Bukkit.dispatchCommand(any(), anyString()), never());
    }

    @Test
    public void failedProgressWriteRollsTheCacheBackAndSkipsRewards() {
        GeneratedQuest quest = quest(0, false, 9, false);
        PlayerQuestState state = load(quest);
        when(repository.updateProgress(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")));

        service.progress().progressMatching(player, QuestTypes.KILL_MOB, RESET_KEY, 1, candidate -> true);

        assertEquals(quest, state.questAtSlot(0).orElseThrow());
        verify(repository, never()).incrementCompletionStats(any(), anyString(), any());
    }

    @Test
    public void progressForPlayerWithoutCachedStateIsAppliedOnceTheStateLoaded() {
        stubLoad(List.of(quest(0, false, 2, false)));
        when(player.isOnline()).thenReturn(true);
        when(entityScheduler.run(any())).thenAnswer(invocation -> {
            Consumer<ScheduledTask> task = invocation.getArgument(0);
            task.accept(mock(ScheduledTask.class));
            return mock(ScheduledTask.class);
        });

        List<QuestProgressResult> immediate = service.progress().progressMatching(player, QuestTypes.KILL_MOB, RESET_KEY, 3, candidate -> true);

        assertTrue(immediate.isEmpty());
        assertEquals(5, service.state().cachedState(playerId, RESET_KEY).orElseThrow().questAtSlot(0).orElseThrow().progress());
        verify(globalQuestService, times(1)).progressMatching(eq(player), eq(QuestTypes.KILL_MOB), eq(3), any(), any());
    }

    @Test
    public void adminCompletionCompletesEveryAccessibleOpenQuest() {
        GeneratedQuest open = quest(0, false, 2, false);
        GeneratedQuest alreadyCompleted = quest(1, false, 10, true);
        GeneratedQuest lockedPremium = quest(3, true, 0, false);
        PlayerQuestState state = load(open, alreadyCompleted, lockedPremium);
        when(entityScheduler.run(any(), any())).thenAnswer(invocation -> {
            Consumer<ScheduledTask> task = invocation.getArgument(0);
            task.accept(mock(ScheduledTask.class));
            return mock(ScheduledTask.class);
        });

        int completed = service.progress().completeDailyQuests(player, RESET_KEY).join();

        assertEquals(1, completed);
        assertTrue(state.questAtSlot(0).orElseThrow().completed());
        assertEquals(10, state.questAtSlot(0).orElseThrow().progress());
        assertFalse(state.questAtSlot(3).orElseThrow().completed());
        verify(repository, times(1)).updateProgress(any());
        verify(repository, times(1)).incrementCompletionStats(eq(playerId), eq("easy"), any());
    }

    private PlayerQuestState load(GeneratedQuest... quests) {
        stubLoad(List.of(quests));
        return service.state().ensurePlayerStateAsync(playerId, RESET_KEY).join();
    }

    private void stubLoad(List<GeneratedQuest> quests) {
        when(repository.loadQuests(playerId, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(quests));
        when(repository.loadDifficultyCompletedCounts(playerId)).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(repository.loadExecutedMilestones(playerId)).thenReturn(CompletableFuture.completedFuture(Set.of()));
    }

    private static QuestDefinition definition() {
        return new QuestDefinition("zombies", QuestTypes.KILL_MOB, true, "easy", "Easy", "Kill zombies", List.of(), Map.of(), Map.of(), null, List.of());
    }

    private GeneratedQuest quest(int slotIndex, boolean premium, int progress, boolean completed) {
        return new GeneratedQuest(
                UUID.randomUUID(),
                playerId,
                RESET_KEY,
                "zombies",
                QuestTypes.KILL_MOB,
                "easy",
                "Easy",
                "Kill zombies",
                List.of(),
                Map.of(),
                slotIndex,
                premium,
                10,
                progress,
                completed
        );
    }
}
