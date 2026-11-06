package gg.moonrise.quests.core.service;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.paper.scheduler.impl.EntityScheduler;
import gg.moonrise.engine.paper.scheduler.impl.SyncScheduler;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.DailyFile;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestCompletionStats;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.model.QuestMilestone;
import gg.moonrise.quests.model.QuestMilestoneClaim;
import gg.moonrise.quests.model.QuestSelectionResult;
import gg.moonrise.quests.model.QuestSelectionStatus;
import gg.moonrise.quests.model.QuestStreakEvaluation;
import gg.moonrise.quests.model.QuestStreakState;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.event.QuestProgressEvent;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestProgressResult;
import gg.moonrise.quests.sdk.model.QuestTypes;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestServiceTest {

    private static final String RESET_KEY = "daily";

    private final UUID playerId = UUID.randomUUID();
    private ConfigProvider configProvider;
    private QuestDefinitionService definitionService;
    private QuestRepository repository;
    private QuestStreakService streakService;
    private GlobalQuestService globalQuestService;
    private QuestProgressIndicatorService indicatorService;
    private Player player;
    private QuestServices service;
    private PluginManager pluginManager;
    private ConsoleCommandSender console;
    private EntityScheduler entityScheduler;
    private MockedStatic<Scheduler> scheduler;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    public void setUp() {
        configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(Config.compose(null, new DailyFile(3), null, null, null, null, null));
        definitionService = mock(QuestDefinitionService.class);
        repository = mock(QuestRepository.class);
        streakService = mock(QuestStreakService.class);
        globalQuestService = mock(GlobalQuestService.class);
        indicatorService = mock(QuestProgressIndicatorService.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Steve");
        service = QuestServices.create(configProvider, definitionService, repository, streakService, globalQuestService, indicatorService);

        pluginManager = mock(PluginManager.class);
        console = mock(ConsoleCommandSender.class);
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
        bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
    }

    @AfterEach
    public void tearDown() {
        bukkit.close();
        scheduler.close();
    }

    @Test
    public void failedSaveOfSelectedQuestRemovesItFromCachedState() {
        stubLoad(List.of());
        QuestDefinition definition = definition("zombies");
        GeneratedQuest generated = quest(0, 0, false);
        GoalHandler handler = mock(GoalHandler.class);
        when(definitionService.enabledDefinitions()).thenReturn(List.of(definition));
        when(definitionService.handler(QuestTypes.KILL_MOB)).thenReturn(handler);
        when(handler.createGeneratedQuest(any(), any(), anyString(), anyMap())).thenReturn(generated);
        when(repository.saveGeneratedQuests(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")));

        CompletableFuture<?> selection = service.selection().selectQuestDifficulty(player, RESET_KEY, 0, "easy");

        assertThrows(CompletionException.class, selection::join);
        PlayerQuestState state = service.state().cachedState(playerId, RESET_KEY).orElseThrow();
        assertFalse(state.hasQuestAtSlot(0));
    }

    @Test
    public void successfulSaveOfSelectedQuestKeepsItInCachedState() {
        stubLoad(List.of());
        QuestDefinition definition = definition("zombies");
        GeneratedQuest generated = quest(0, 0, false);
        GoalHandler handler = mock(GoalHandler.class);
        when(definitionService.enabledDefinitions()).thenReturn(List.of(definition));
        when(definitionService.handler(QuestTypes.KILL_MOB)).thenReturn(handler);
        when(handler.createGeneratedQuest(any(), any(), anyString(), anyMap())).thenReturn(generated);
        when(repository.saveGeneratedQuests(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.selection().selectQuestDifficulty(player, RESET_KEY, 0, "easy").join();

        assertEquals(generated.instanceId(), service.state().cachedState(playerId, RESET_KEY).orElseThrow().questAtSlot(0).orElseThrow().instanceId());
    }

    @Test
    public void loadInFlightDuringResetDoesNotCacheStaleQuests() {
        GeneratedQuest beforeReset = quest(0, 4, false);
        CompletableFuture<List<GeneratedQuest>> staleLoad = new CompletableFuture<>();
        when(repository.loadQuests(playerId, RESET_KEY)).thenReturn(staleLoad, CompletableFuture.completedFuture(List.of()));
        when(repository.loadDifficultyCompletedCounts(playerId)).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(repository.loadExecutedMilestones(playerId)).thenReturn(CompletableFuture.completedFuture(Set.of()));
        when(repository.resetDailyQuests(playerId, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<PlayerQuestState> loading = service.state().ensurePlayerStateAsync(playerId, RESET_KEY);
        service.state().resetDaily(playerId, RESET_KEY).join();
        staleLoad.complete(List.of(beforeReset));

        PlayerQuestState loaded = loading.join();
        assertTrue(loaded.quests().isEmpty());
        assertTrue(service.state().cachedState(playerId, RESET_KEY).orElseThrow().quests().isEmpty());
        verify(repository, times(2)).loadQuests(playerId, RESET_KEY);
    }

    @Test
    public void invalidatedLoadIsNotHandedToNewCallers() {
        CompletableFuture<List<GeneratedQuest>> staleLoad = new CompletableFuture<>();
        CompletableFuture<List<GeneratedQuest>> freshLoad = new CompletableFuture<>();
        when(repository.loadQuests(playerId, RESET_KEY)).thenReturn(staleLoad, freshLoad);
        when(repository.loadDifficultyCompletedCounts(playerId)).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(repository.loadExecutedMilestones(playerId)).thenReturn(CompletableFuture.completedFuture(Set.of()));
        when(repository.resetDailyQuests(playerId, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(null));

        service.state().ensurePlayerStateAsync(playerId, RESET_KEY);
        service.state().resetDaily(playerId, RESET_KEY).join();
        CompletableFuture<PlayerQuestState> afterReset = service.state().ensurePlayerStateAsync(playerId, RESET_KEY);

        verify(repository, times(2)).loadQuests(playerId, RESET_KEY);
        GeneratedQuest fresh = quest(1, 0, false);
        freshLoad.complete(List.of(fresh));
        staleLoad.complete(List.of(quest(0, 4, false)));
        assertEquals(List.of(fresh), afterReset.join().quests());
        assertEquals(List.of(fresh), service.state().cachedState(playerId, RESET_KEY).orElseThrow().quests());
    }

    @Test
    public void loadWithoutInvalidationIsSharedAndCached() {
        CompletableFuture<List<GeneratedQuest>> pending = new CompletableFuture<>();
        when(repository.loadQuests(playerId, RESET_KEY)).thenReturn(pending);
        when(repository.loadDifficultyCompletedCounts(playerId)).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(repository.loadExecutedMilestones(playerId)).thenReturn(CompletableFuture.completedFuture(Set.of()));

        CompletableFuture<PlayerQuestState> first = service.state().ensurePlayerStateAsync(playerId, RESET_KEY);
        CompletableFuture<PlayerQuestState> second = service.state().ensurePlayerStateAsync(playerId, RESET_KEY);
        GeneratedQuest quest = quest(0, 0, false);
        pending.complete(List.of(quest));

        assertSame(first.join(), second.join());
        assertSame(first.join(), service.state().cachedState(playerId, RESET_KEY).orElseThrow());
        verify(repository, times(1)).loadQuests(playerId, RESET_KEY);
    }

    @Test
    public void rerollRejectedByRepositoryLimitReportsNoRerollsAndKeepsQuest() {
        GeneratedQuest existing = quest(0, 2, false);
        stubLoad(List.of(existing));
        stubReroll(new QuestRepository.RerollOutcome.LimitReached(1));

        QuestSelectionResult result = service.selection().rerollQuestDifficulty(player, RESET_KEY, 0, "easy").join();

        assertEquals(QuestSelectionStatus.NO_REROLLS, result.status());
        assertEquals(1, result.rerollsUsed());
        assertEquals(1, service.usage().cachedRerollsUsed(playerId, RESET_KEY));
        assertEquals(existing.instanceId(), service.state().cachedState(playerId, RESET_KEY).orElseThrow().questAtSlot(0).orElseThrow().instanceId());
    }

    @Test
    public void rerollOfQuestAlreadyReplacedReportsInvalidAndKeepsCurrentQuest() {
        GeneratedQuest existing = quest(0, 2, false);
        stubLoad(List.of(existing));
        GeneratedQuest firstReplacement = quest(0, 0, false);
        PlayerQuestState state = service.state().ensurePlayerStateAsync(playerId, RESET_KEY).join();
        stubReroll(new QuestRepository.RerollOutcome.QuestMissing());
        // The first of two double-submitted rerolls has already replaced the quest by the time the second one fails.
        when(repository.replaceQuestAndIncrementRerolls(any(), any(), anyInt())).thenAnswer(invocation -> {
            state.replaceQuestAtSlot(0, firstReplacement);
            return CompletableFuture.completedFuture(new QuestRepository.RerollOutcome.QuestMissing());
        });

        QuestSelectionResult result = service.selection().rerollQuestDifficulty(player, RESET_KEY, 0, "easy").join();

        assertEquals(QuestSelectionStatus.INVALID, result.status());
        assertEquals(firstReplacement.instanceId(), service.state().cachedState(playerId, RESET_KEY).orElseThrow().questAtSlot(0).orElseThrow().instanceId());
        verify(repository, times(1)).loadQuests(playerId, RESET_KEY);
    }

    @Test
    public void rerollAcceptedByRepositoryReplacesQuestAndRecordsUsage() {
        GeneratedQuest existing = quest(0, 2, false);
        stubLoad(List.of(existing));
        GeneratedQuest replacement = stubReroll(new QuestRepository.RerollOutcome.Rerolled(1));

        QuestSelectionResult result = service.selection().rerollQuestDifficulty(player, RESET_KEY, 0, "easy").join();

        assertEquals(QuestSelectionStatus.REROLLED, result.status());
        assertEquals(1, service.usage().cachedRerollsUsed(playerId, RESET_KEY));
        assertEquals(replacement.instanceId(), service.state().cachedState(playerId, RESET_KEY).orElseThrow().questAtSlot(0).orElseThrow().instanceId());
    }

    @Test
    public void completionWhilePlayerLogsOutStillRecordsStatsAndRunsRewards() {
        GeneratedQuest almostDone = quest(0, 9, false);
        stubCompletion(almostDone);
        // Paper retires the entity's task queue when the player leaves and calls the retired callback instead.
        when(entityScheduler.run(any(), any())).thenAnswer(invocation -> {
            Runnable retired = invocation.getArgument(1);
            retired.run();
            return mock(ScheduledTask.class);
        });

        service.progress().progressAdminGoal(player, RESET_KEY, 1, QuestTypes.KILL_MOB);

        verify(repository).incrementCompletionStats(eq(playerId), eq("easy"), any());
        bukkit.verify(() -> Bukkit.dispatchCommand(console, "give Steve diamond"));
        verify(streakService, never()).evaluateQuestCompletion(any(), any(), any());
        assertEquals(1, service.state().cachedState(playerId, RESET_KEY).orElseThrow().difficultyCompletions("easy"));
    }

    @Test
    public void completionAfterPlayerEntityIsGoneStillRunsRewards() {
        GeneratedQuest almostDone = quest(0, 9, false);
        stubCompletion(almostDone);
        // Scheduling on an already removed entity returns null without calling the retired callback.
        when(entityScheduler.run(any(), any())).thenReturn(null);

        service.progress().progressAdminGoal(player, RESET_KEY, 1, QuestTypes.KILL_MOB);

        verify(repository).incrementCompletionStats(eq(playerId), eq("easy"), any());
        bukkit.verify(() -> Bukkit.dispatchCommand(console, "give Steve diamond"));
    }

    @Test
    public void completionRunsDefinitionRewardCommandsFromTheDefinitionLookup() {
        GeneratedQuest almostDone = quest(0, 9, false);
        stubCompletion(almostDone);
        QuestDefinition withBonus = new QuestDefinition("zombies", QuestTypes.KILL_MOB, true, "easy", "Easy", "Kill zombies", List.of(), Map.of(), Map.of(), null, List.of("bonus <player>"));
        when(definitionService.definition(any(GeneratedQuest.class))).thenReturn(Optional.of(withBonus));
        when(entityScheduler.run(any(), any())).thenReturn(null);

        service.progress().progressAdminGoal(player, RESET_KEY, 1, QuestTypes.KILL_MOB);

        bukkit.verify(() -> Bukkit.dispatchCommand(console, "give Steve diamond"));
        bukkit.verify(() -> Bukkit.dispatchCommand(console, "bonus Steve"));
        verify(definitionService, never()).definitions();
    }

    @Test
    public void completionWhileOnlineRunsRewardsAndStreakOnEntityThread() {
        GeneratedQuest almostDone = quest(0, 9, false);
        stubCompletion(almostDone);
        when(player.isOnline()).thenReturn(true);
        when(streakService.isEnabled()).thenReturn(true);
        when(streakService.evaluateQuestCompletion(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new QuestStreakEvaluation(QuestStreakState.empty(playerId), List.of())));
        when(entityScheduler.run(any(), any())).thenAnswer(invocation -> {
            Consumer<ScheduledTask> task = invocation.getArgument(0);
            task.accept(mock(ScheduledTask.class));
            return mock(ScheduledTask.class);
        });

        service.progress().progressAdminGoal(player, RESET_KEY, 1, QuestTypes.KILL_MOB);

        bukkit.verify(() -> Bukkit.dispatchCommand(console, "give Steve diamond"));
        verify(streakService).evaluateQuestCompletion(eq(player), any(), any());
        scheduler.verify(Scheduler::sync, never());
    }

    @Test
    public void joinEvaluatesStreakThatWasMissedWhileOffline() {
        GeneratedQuest completed = quest(0, 10, true);
        stubLoad(List.of(completed));
        when(streakService.isEnabled()).thenReturn(true);
        QuestStreakState notYetAwarded = new QuestStreakState(playerId, 2, 2, "yesterday", "yesterday", 0, "", 0, 0, Set.of());
        when(streakService.ensureState(player, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(notYetAwarded));
        when(streakService.evaluateQuestCompletion(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new QuestStreakEvaluation(notYetAwarded, List.of())));

        service.state().ensurePlayerStateAsync(player, RESET_KEY).join();

        verify(streakService).evaluateQuestCompletion(eq(player), any(), eq(List.of(completed)));
    }

    @Test
    public void joinSkipsStreakEvaluationWhenWindowWasAlreadyAwarded() {
        stubLoad(List.of(quest(0, 10, true)));
        when(streakService.isEnabled()).thenReturn(true);
        QuestStreakState awarded = new QuestStreakState(playerId, 3, 3, RESET_KEY, RESET_KEY, 0, "", 0, 0, Set.of());
        when(streakService.ensureState(player, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(awarded));

        service.state().ensurePlayerStateAsync(player, RESET_KEY).join();

        verify(streakService, never()).evaluateQuestCompletion(any(), any(), any());
    }

    @Test
    public void milestoneReachedWhilePlayerLogsOutStillRunsItsCommands() {
        GeneratedQuest almostDone = quest(0, 9, false);
        stubCompletion(almostDone);
        QuestMilestone milestone = new QuestMilestone("easy", 1, "First Quest", List.of(), List.of("eco give <player> 100", "notify <uuid> <milestone_completed>"));
        when(repository.incrementCompletionStats(eq(playerId), eq("easy"), any()))
                .thenReturn(CompletableFuture.completedFuture(new QuestCompletionStats(1, 1, List.of(milestone))));
        when(entityScheduler.run(any(), any())).thenReturn(null);
        // The message task is dropped because the player's entity is gone.
        when(entityScheduler.run(any())).thenReturn(null);

        service.progress().progressAdminGoal(player, RESET_KEY, 1, QuestTypes.KILL_MOB);

        bukkit.verify(() -> Bukkit.dispatchCommand(console, "eco give Steve 100"));
        bukkit.verify(() -> Bukkit.dispatchCommand(console, "notify " + playerId + " 1"));
        assertTrue(service.state().cachedState(playerId, RESET_KEY).orElseThrow().hasExecutedMilestone("easy", 1));
    }

    @Test
    public void retroactiveMilestoneClaimRunsCommandsWhenPlayerIsOffline() {
        stubLoad(List.of());
        QuestMilestone milestone = new QuestMilestone("easy", 5, "Five Quests", List.of(), List.of("eco give <player> 500"));
        QuestDifficulty difficulty = new QuestDifficulty("easy", "Easy", 0, 0, Map.of(), List.of(), List.of(), List.of(milestone));
        when(definitionService.difficulties()).thenReturn(List.of(difficulty));
        when(repository.claimEligibleMilestones(eq(playerId), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(new QuestMilestoneClaim("easy", "Easy", 5, milestone))));
        when(entityScheduler.run(any())).thenReturn(null);

        service.state().ensurePlayerStateAsync(player, RESET_KEY).join();

        bukkit.verify(() -> Bukkit.dispatchCommand(console, "eco give Steve 500"));
        verify(player, never()).isOnline();
    }

    @Test
    public void progressEventIsFiredWithoutHoldingTheStateLock() {
        GeneratedQuest quest = quest(0, 2, false);
        PlayerQuestState state = stubProgress(quest);
        AtomicBoolean otherThreadUpdated = new AtomicBoolean();
        // A listener that hands work to another thread which writes the state must not deadlock.
        doAnswer(invocation -> {
            CompletableFuture.runAsync(() -> state.markMilestoneExecuted("easy", 1)).get(5, TimeUnit.SECONDS);
            otherThreadUpdated.set(true);
            return null;
        }).when(pluginManager).callEvent(any(QuestProgressEvent.class));

        List<QuestProgressResult> results = service.progress().progressAdminGoal(player, RESET_KEY, 3, QuestTypes.KILL_MOB);

        assertTrue(otherThreadUpdated.get());
        assertTrue(state.hasExecutedMilestone("easy", 1));
        assertEquals(1, results.size());
        assertEquals(5, state.questAtSlot(0).orElseThrow().progress());
    }

    @Test
    public void progressApprovedWhileQuestAdvancedIsClampedAgainstCurrentProgress() {
        GeneratedQuest quest = quest(0, 2, false);
        PlayerQuestState state = stubProgress(quest);
        // Another thread credits 6 while the listener runs.
        doAnswer(invocation -> {
            state.replaceQuest(quest.withProgress(8, false));
            return null;
        }).when(pluginManager).callEvent(any(QuestProgressEvent.class));

        List<QuestProgressResult> results = service.progress().progressAdminGoal(player, RESET_KEY, 3, QuestTypes.KILL_MOB);

        assertEquals(10, state.questAtSlot(0).orElseThrow().progress());
        assertTrue(results.getFirst().completedNow());
        verify(repository).updateProgress(quest.withProgress(10, true));
    }

    @Test
    public void progressApprovedAfterQuestWasRerolledIsDropped() {
        GeneratedQuest quest = quest(0, 2, false);
        PlayerQuestState state = stubProgress(quest);
        GeneratedQuest rerolled = quest(0, 0, false);
        doAnswer(invocation -> {
            state.replaceQuestAtSlot(0, rerolled);
            return null;
        }).when(pluginManager).callEvent(any(QuestProgressEvent.class));

        List<QuestProgressResult> results = service.progress().progressAdminGoal(player, RESET_KEY, 3, QuestTypes.KILL_MOB);

        assertTrue(results.isEmpty());
        assertEquals(0, state.questAtSlot(0).orElseThrow().progress());
        verify(repository, never()).updateProgress(any());
    }

    private PlayerQuestState stubProgress(GeneratedQuest quest) {
        stubLoad(List.of(quest));
        PlayerQuestState state = service.state().ensurePlayerStateAsync(playerId, RESET_KEY).join();
        when(definitionService.definition(any(GeneratedQuest.class))).thenReturn(Optional.of(definition("zombies")));
        when(repository.updateProgress(any())).thenReturn(CompletableFuture.completedFuture(new QuestRepository.ProgressUpdateOutcome.Updated()));
        when(repository.incrementCompletionStats(eq(playerId), eq("easy"), any()))
                .thenReturn(CompletableFuture.completedFuture(new QuestCompletionStats(1, 1, List.of())));
        return state;
    }

    private void stubCompletion(GeneratedQuest quest) {
        stubLoad(List.of(quest));
        service.state().ensurePlayerStateAsync(playerId, RESET_KEY).join();
        when(definitionService.definition(any(GeneratedQuest.class))).thenReturn(Optional.of(definition("zombies")));
        when(definitionService.difficulty("easy")).thenReturn(Optional.of(new QuestDifficulty("easy", "Easy", 0, 0, Map.of(), List.of("give <player> diamond"), List.of(), List.of())));
        when(repository.updateProgress(any())).thenReturn(CompletableFuture.completedFuture(new QuestRepository.ProgressUpdateOutcome.Updated()));
        when(repository.incrementCompletionStats(eq(playerId), eq("easy"), any()))
                .thenReturn(CompletableFuture.completedFuture(new QuestCompletionStats(1, 1, List.of())));
    }

    private GeneratedQuest stubReroll(QuestRepository.RerollOutcome outcome) {
        when(player.hasPermission("questsplus.reroll.vip")).thenReturn(true);
        when(repository.loadRerollsUsed(playerId, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(0));
        GeneratedQuest replacement = quest(0, 0, false);
        GoalHandler handler = mock(GoalHandler.class);
        when(definitionService.enabledDefinitions()).thenReturn(List.of(definition("zombies"), definition("skeletons")));
        when(definitionService.handler(QuestTypes.KILL_MOB)).thenReturn(handler);
        when(handler.createGeneratedQuest(any(), any(), anyString(), anyMap())).thenReturn(replacement);
        when(repository.replaceQuestAndIncrementRerolls(any(), any(), anyInt())).thenReturn(CompletableFuture.completedFuture(outcome));
        return replacement;
    }

    private void stubLoad(List<GeneratedQuest> quests) {
        when(repository.loadQuests(playerId, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(quests));
        when(repository.loadDifficultyCompletedCounts(playerId)).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(repository.loadExecutedMilestones(playerId)).thenReturn(CompletableFuture.completedFuture(Set.of()));
    }

    private static QuestDefinition definition(String id) {
        return new QuestDefinition(id, QuestTypes.KILL_MOB, true, "easy", "Easy", "Kill zombies", List.of(), Map.of(), Map.of(), null, List.of());
    }

    @Test
    public void questTokensRenderFormattedNumbersStrippedNameAndVariables() {
        GeneratedQuest quest = new GeneratedQuest(
                UUID.randomUUID(),
                playerId,
                RESET_KEY,
                "zombies",
                QuestTypes.KILL_MOB,
                "easy",
                "<gold>Easy",
                " <green>Kill <b>zombies</b> ",
                List.of(),
                Map.of(),
                0,
                false,
                12_000,
                1_500,
                false
        );
        when(definitionService.variablePlaceholders(quest)).thenReturn(Map.of("entity", "Zombie"));

        String rendered = service.text().replaceQuestTokens(
                "<player>|<uuid>|<quest_id>|<quest_display_name>|<quest_difficulty>|<quest_difficulty_id>|<progress>/<goal_amount>/<goal-amount>|<entity>|<unknown>",
                quest,
                player
        );

        assertEquals("Steve|" + playerId + "|zombies|Kill zombies|<gold>Easy|easy|1,500/12,000/12,000|Zombie|<unknown>", rendered);
        assertEquals("", service.text().replaceQuestTokens(null, quest, player));
    }

    private GeneratedQuest quest(int slotIndex, int progress, boolean completed) {
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
                false,
                10,
                progress,
                completed
        );
    }
}
