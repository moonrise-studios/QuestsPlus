package gg.moonrise.quests.core.service;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.paper.scheduler.impl.SyncScheduler;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.model.GlobalQuestContribution;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestDefinitionSchedule;
import gg.moonrise.quests.sdk.model.QuestProgressResult;
import gg.moonrise.quests.sdk.model.QuestTypes;
import gg.moonrise.quests.sdk.event.QuestProgressEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalQuestServiceTest {

    private final List<Consumer<ScheduledTask>> syncTasks = new ArrayList<>();
    private ServerMock server;
    private MockedStatic<Scheduler> scheduler;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        SyncScheduler syncScheduler = mock(SyncScheduler.class);
        when(syncScheduler.run(any())).thenAnswer(invocation -> {
            syncTasks.add(invocation.getArgument(0));
            return mock(ScheduledTask.class);
        });
        when(syncScheduler.runDelayed(any(), any(Duration.class))).thenReturn(mock(ScheduledTask.class));
        scheduler = mockStatic(Scheduler.class);
        scheduler.when(Scheduler::sync).thenReturn(syncScheduler);
    }

    @AfterEach
    public void tearDown() {
        scheduler.close();
        MockBukkit.unmock();
    }

    @Test
    public void loadStartedBeforeRefreshDoesNotOverwriteRefreshedQuest() {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        QuestDefinitionService definitionService = mock(QuestDefinitionService.class);
        GlobalQuestRepository repository = mock(GlobalQuestRepository.class);
        Player player = server.addPlayer("RefreshRace");
        GlobalQuestService service = new GlobalQuestService(configProvider, definitionService, repository, mock(QuestProgressIndicatorService.class));
        GlobalQuestState deletedState = activeGlobalState(player.getUniqueId());
        GlobalQuestState refreshedState = activeGlobalState(player.getUniqueId());
        CompletableFuture<GlobalQuestState> staleLoad = new CompletableFuture<>();
        when(configProvider.get()).thenReturn(new Config());
        when(definitionService.globalDefinition("global-kill-all-mobs")).thenReturn(Optional.of(globalDefinition()));
        when(repository.loadActive(any()))
                .thenReturn(staleLoad)
                .thenReturn(CompletableFuture.completedFuture(refreshedState));
        when(repository.deletePeriod(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(repository.updateProgressAndContribution(any(), any(), anyInt())).thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<GlobalQuestState> menuLoad = service.activeStateAsync();
        assertSame(refreshedState, service.refreshActiveQuest().join());
        staleLoad.complete(deletedState);

        assertSame(refreshedState, service.cachedActiveState());
        assertSame(refreshedState, menuLoad.join());
        service.progressAdminGoal(player, QuestTypes.KILL_ALL_MOBS, 3);
        assertEquals(refreshedState.quest().instanceId(), service.cachedActiveState().quest().instanceId());
        assertEquals(3, service.cachedActiveState().quest().progress());
    }

    @Test
    public void globalAdminProgressCanCreditEventDrivenGoalTypes() throws Exception {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        QuestDefinitionService definitionService = mock(QuestDefinitionService.class);
        GlobalQuestRepository repository = mock(GlobalQuestRepository.class);
        QuestProgressIndicatorService progressIndicatorService = mock(QuestProgressIndicatorService.class);
        Player player = server.addPlayer("AdminTarget");
        Config config = new Config();
        GlobalQuestService service = new GlobalQuestService(configProvider, definitionService, repository, progressIndicatorService);
        GlobalQuestState activeState = activeGlobalState(player.getUniqueId());

        when(configProvider.get()).thenReturn(config);
        when(definitionService.globalDefinition("global-kill-all-mobs")).thenReturn(Optional.of(globalDefinition()));
        when(repository.updateProgressAndContribution(any(), eq(player.getUniqueId()), anyInt())).thenReturn(CompletableFuture.completedFuture(null));
        service.cachedActiveState(activeState);

        List<QuestProgressResult> results = service.progressAdminGoal(player, QuestTypes.KILL_ALL_MOBS, 5);

        assertEquals(1, results.size());
        assertEquals(5, results.getFirst().quest().progress());
        assertFalse(results.getFirst().completedNow());
        GlobalQuestState updatedState = service.cachedActiveState();
        assertEquals(5, updatedState.quest().progress());
        assertEquals(5, updatedState.contribution(player.getUniqueId()));
        verify(progressIndicatorService).showGlobal(eq(player), any(GlobalQuestState.class), eq(0));
        verify(repository).updateProgressAndContribution(any(GlobalQuestState.class), eq(player.getUniqueId()), eq(5));
    }

    @Test
    public void progressEventIsFiredWithoutHoldingTheStateLockAndCreditIsReclamped() {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        QuestDefinitionService definitionService = mock(QuestDefinitionService.class);
        GlobalQuestRepository repository = mock(GlobalQuestRepository.class);
        Player player = server.addPlayer("ReentrantListener");
        UUID otherPlayer = UUID.randomUUID();
        GlobalQuestService service = new GlobalQuestService(configProvider, definitionService, repository, mock(QuestProgressIndicatorService.class));
        GlobalQuestState initial = activeGlobalState(player.getUniqueId());
        GlobalQuestState advancedByOtherThread = initial.withQuest(initial.quest().withProgress(6, false), Map.of(otherPlayer, 6));
        when(configProvider.get()).thenReturn(new Config());
        when(definitionService.globalDefinition("global-kill-all-mobs")).thenReturn(Optional.of(globalDefinition()));
        when(repository.updateProgressAndContribution(any(), any(), anyInt())).thenReturn(CompletableFuture.completedFuture(null));
        service.cachedActiveState(initial);
        AtomicBoolean otherThreadBlocked = new AtomicBoolean();
        registerProgressListener(event -> {
            // Another thread touching the global state while the listener runs must not block.
            Thread writer = new Thread(() -> service.cachedActiveState(advancedByOtherThread));
            writer.start();
            joinQuietly(writer);
            otherThreadBlocked.set(writer.isAlive());
        });

        List<QuestProgressResult> results = service.progressAdminGoal(player, QuestTypes.KILL_ALL_MOBS, 8);

        assertFalse(otherThreadBlocked.get());
        assertEquals(1, results.size());
        assertEquals(10, results.getFirst().quest().progress());
        assertTrue(results.getFirst().completedNow());
        assertEquals(4, service.cachedActiveState().contribution(player.getUniqueId()));
        assertEquals(6, service.cachedActiveState().contribution(otherPlayer));
        verify(repository).updateProgressAndContribution(any(GlobalQuestState.class), eq(player.getUniqueId()), eq(4));
    }

    @Test
    public void progressIsDroppedWhenQuestIsReplacedWhileListenersRun() {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        QuestDefinitionService definitionService = mock(QuestDefinitionService.class);
        GlobalQuestRepository repository = mock(GlobalQuestRepository.class);
        Player player = server.addPlayer("ReplacedQuest");
        GlobalQuestService service = new GlobalQuestService(configProvider, definitionService, repository, mock(QuestProgressIndicatorService.class));
        GlobalQuestState replacement = activeGlobalState(player.getUniqueId());
        when(configProvider.get()).thenReturn(new Config());
        when(definitionService.globalDefinition("global-kill-all-mobs")).thenReturn(Optional.of(globalDefinition()));
        service.cachedActiveState(activeGlobalState(player.getUniqueId()));
        registerProgressListener(event -> service.cachedActiveState(replacement));

        List<QuestProgressResult> results = service.progressAdminGoal(player, QuestTypes.KILL_ALL_MOBS, 5);

        assertTrue(results.isEmpty());
        assertSame(replacement, service.cachedActiveState());
        verify(repository, never()).updateProgressAndContribution(any(), any(), anyInt());
    }

    @Test
    public void missingGlobalQuestIsCachedForThePeriodInsteadOfQueryingOnEveryEvent() {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        QuestDefinitionService definitionService = mock(QuestDefinitionService.class);
        GlobalQuestRepository repository = mock(GlobalQuestRepository.class);
        Player player = server.addPlayer("NoGlobalQuest");
        GlobalQuestService service = new GlobalQuestService(configProvider, definitionService, repository, mock(QuestProgressIndicatorService.class));
        when(configProvider.get()).thenReturn(new Config());
        when(definitionService.globalDefinitions()).thenReturn(List.of());
        when(repository.loadActive(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(repository.deletePeriod(any())).thenReturn(CompletableFuture.completedFuture(null));

        for (int i = 0; i < 5; i++) {
            assertTrue(service.progressAdminGoal(player, QuestTypes.KILL_ALL_MOBS, 1).isEmpty());
        }
        assertNull(service.activeStateAsync().join());
        verify(repository, times(1)).loadActive(any());

        when(definitionService.revision()).thenReturn(1L);
        service.progressAdminGoal(player, QuestTypes.KILL_ALL_MOBS, 1);
        service.progressAdminGoal(player, QuestTypes.KILL_ALL_MOBS, 1);
        verify(repository, times(2)).loadActive(any());

        service.refreshActiveQuest().join();
        service.progressAdminGoal(player, QuestTypes.KILL_ALL_MOBS, 1);
        verify(repository, times(3)).loadActive(any());
    }

    @Test
    public void tierRewardsResolvePlayerNamesAndDispatchOnTheGlobalScheduler() {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        QuestDefinitionService definitionService = mock(QuestDefinitionService.class);
        GlobalQuestRepository repository = mock(GlobalQuestRepository.class);
        GlobalQuestService service = new GlobalQuestService(configProvider, definitionService, repository, mock(QuestProgressIndicatorService.class));
        UUID winnerId = UUID.randomUUID();
        GlobalQuestState expired = activeGlobalState(winnerId);
        expired = expired.withQuest(expired.quest().withProgress(10, true), Map.of(winnerId, 10));
        List<String> dispatched = new ArrayList<>();
        server.getCommandMap().register("questsplustest", new Command("eco") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                dispatched.add(String.join(" ", args));
                return true;
            }
        });
        when(configProvider.get()).thenReturn(configWithRewardTiers());
        when(definitionService.globalDefinitions()).thenReturn(List.of());
        when(repository.loadExpiredUnrewarded(any())).thenReturn(CompletableFuture.completedFuture(List.of(expired)));
        when(repository.loadRankedContributions(expired.quest().instanceId()))
                .thenReturn(CompletableFuture.completedFuture(List.of(new GlobalQuestContribution(winnerId, 10, 1, 1))));
        when(repository.insertRewardExecution(any(), any(), anyInt())).thenReturn(CompletableFuture.completedFuture(true));
        when(repository.markRewardsExecuted(any())).thenReturn(CompletableFuture.completedFuture(true));
        when(repository.loadActive(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.reload();
        assertTrue(dispatched.isEmpty());
        // The winner only becomes known to the server once the global task runs, so a name resolved on the
        // SQL completion thread would fall back to the UUID.
        viewer("Winner", winnerId);
        runSyncTasks();

        assertEquals(List.of("give Winner 100"), dispatched);
    }

    @Test
    public void globalRankOrdersByContributionThenUuid() {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        GlobalQuestService service = serviceWithConfig(configProvider);
        when(configProvider.get()).thenReturn(new Config());
        UUID top = new UUID(0L, 9L);
        UUID tiedFirst = new UUID(0L, 2L);
        UUID tiedSecond = new UUID(0L, 3L);
        UUID idle = new UUID(0L, 1L);
        Map<UUID, Integer> contributions = new LinkedHashMap<>();
        contributions.put(tiedSecond, 5);
        contributions.put(idle, 0);
        contributions.put(top, 10);
        contributions.put(tiedFirst, 5);
        GlobalQuestState base = activeGlobalState(top);
        GlobalQuestState state = base.withQuest(base.quest().withProgress(20, false), contributions);
        String template = "<global_rank>/<global_participants>";

        assertEquals("1/3", service.replaceGlobalTokens(template, state, viewer("Top", top)));
        assertEquals("2/3", service.replaceGlobalTokens(template, state, viewer("TiedFirst", tiedFirst)));
        assertEquals("3/3", service.replaceGlobalTokens(template, state, viewer("TiedSecond", tiedSecond)));
        assertEquals("-/3", service.replaceGlobalTokens(template, state, viewer("Idle", idle)));
        assertEquals("-/3", service.replaceGlobalTokens(template, state, server.addPlayer("Stranger")));
    }

    @Test
    public void incompleteGlobalQuestBelowHalfGetsNoRewards() throws Exception {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        GlobalQuestService service = serviceWithConfig(configProvider);
        Config config = configWithRewardTiers();
        when(configProvider.get()).thenReturn(config);

        List<Config.GlobalRewardTierConfig> tiers = service.rewardTiersForProgress(globalQuest(49, 100, false));

        assertTrue(tiers.isEmpty());
    }

    @Test
    public void incompleteGlobalQuestAtHalfGetsReducedRewards() throws Exception {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        GlobalQuestService service = serviceWithConfig(configProvider);
        Config config = configWithRewardTiers();
        when(configProvider.get()).thenReturn(config);

        List<Config.GlobalRewardTierConfig> tiers = service.rewardTiersForProgress(globalQuest(50, 100, false));

        assertEquals("Reduced Top 10%", tiers.getFirst().getDisplayName());
    }

    @Test
    public void completedGlobalQuestGetsFullRewards() throws Exception {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        GlobalQuestService service = serviceWithConfig(configProvider);
        Config config = configWithRewardTiers();
        when(configProvider.get()).thenReturn(config);

        List<Config.GlobalRewardTierConfig> tiers = service.rewardTiersForProgress(globalQuest(100, 100, true));

        assertEquals("Full Top 10%", tiers.getFirst().getDisplayName());
    }

    @Test
    public void reducedRewardMinimumPercentIsConfigurable() throws Exception {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        GlobalQuestService service = serviceWithConfig(configProvider);
        Config config = configWithRewardTiers();
        config = configWithRewardTiers(75.0D);
        when(configProvider.get()).thenReturn(config);

        assertTrue(service.rewardTiersForProgress(globalQuest(74, 100, false)).isEmpty());
        assertEquals("Reduced Top 10%", service.rewardTiersForProgress(globalQuest(75, 100, false)).getFirst().getDisplayName());
    }

    private static GlobalQuestState activeGlobalState(UUID playerId) {
        LocalDateTime startsAt = currentPeriodStart();
        GeneratedQuest quest = globalQuest(0, 10, false);
        return new GlobalQuestState(quest, startsAt.toLocalDate().toString(), startsAt, startsAt.plusDays(7), Map.of(playerId, 0), false);
    }

    private PlayerMock viewer(String name, UUID id) {
        PlayerMock player = new PlayerMock(server, name, id);
        server.addPlayer(player);
        return player;
    }

    private void runSyncTasks() {
        for (int i = 0; i < syncTasks.size(); i++) {
            syncTasks.get(i).accept(mock(ScheduledTask.class));
        }
        syncTasks.clear();
    }

    private void registerProgressListener(Consumer<QuestProgressEvent> handler) {
        AtomicBoolean handled = new AtomicBoolean();
        server.getPluginManager().registerEvent(
                QuestProgressEvent.class,
                new Listener() {
                },
                EventPriority.NORMAL,
                (listener, event) -> {
                    if (event instanceof QuestProgressEvent progressEvent && handled.compareAndSet(false, true)) {
                        handler.accept(progressEvent);
                    }
                },
                MockBukkit.createMockPlugin()
        );
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(2_000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static QuestDefinition globalDefinition() {
        return new QuestDefinition(
                "global-kill-all-mobs",
                QuestTypes.KILL_ALL_MOBS,
                true,
                "easy",
                "<green><b>EASY",
                "<green>Global Mob Slayer",
                List.of(),
                Map.of(),
                Map.of(),
                QuestDefinitionSchedule.alwaysActive(),
                List.of()
        );
    }

    private static GeneratedQuest globalQuest(int progress, int goalAmount, boolean completed) {
        LocalDateTime startsAt = currentPeriodStart();
        return new GeneratedQuest(
                UUID.randomUUID(),
                new UUID(0L, 0L),
                startsAt.toLocalDate().toString(),
                "global-kill-all-mobs",
                QuestTypes.KILL_ALL_MOBS,
                "easy",
                "<green><b>EASY",
                "<green>Global Mob Slayer",
                List.of("<gray>Kill <white><goal-amount></white> mobs as a server."),
                Map.of("goal-amount", Integer.toString(goalAmount)),
                goalAmount,
                progress,
                completed
        );
    }

    private static LocalDateTime currentPeriodStart() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate date = now.toLocalDate();
        int delta = Math.floorMod(date.getDayOfWeek().getValue() - DayOfWeek.FRIDAY.getValue(), 7);
        LocalDateTime start = date.minusDays(delta).atTime(LocalTime.of(5, 0));
        return now.isBefore(start) ? start.minusDays(7) : start;
    }

    private static GlobalQuestService serviceWithConfig(ConfigProvider configProvider) {
        return new GlobalQuestService(
                configProvider,
                mock(QuestDefinitionService.class),
                mock(GlobalQuestRepository.class),
                mock(QuestProgressIndicatorService.class)
        );
    }

    private static Config configWithRewardTiers() {
        return configWithRewardTiers(50.0D);
    }

    private static Config configWithRewardTiers(double reducedRewardMinimumPercent) {
        Config.GlobalQuestsFile globalQuests = new Config.GlobalQuestsFile(
                List.of(new Config.GlobalRewardTierConfig(10, "Full Top 10%", List.of("eco give <player> 100"))),
                reducedRewardMinimumPercent,
                List.of(new Config.GlobalRewardTierConfig(10, "Reduced Top 10%", List.of("eco give <player> 50")))
        );
        return Config.compose(null, null, null, null, null, globalQuests, null);
    }
}
