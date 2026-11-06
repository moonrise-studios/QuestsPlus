package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestTypes;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
    private QuestService service;

    @BeforeEach
    public void setUp() {
        configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(Config.compose(null, new Config.DailyFile(3), null, null, null, null, null));
        definitionService = mock(QuestDefinitionService.class);
        repository = mock(QuestRepository.class);
        streakService = mock(QuestStreakService.class);
        globalQuestService = mock(GlobalQuestService.class);
        indicatorService = mock(QuestProgressIndicatorService.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Steve");
        service = new QuestService(configProvider, definitionService, repository, streakService, globalQuestService, indicatorService);
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

        CompletableFuture<?> selection = service.selectQuestDifficulty(player, RESET_KEY, 0, "easy");

        assertThrows(CompletionException.class, selection::join);
        PlayerQuestState state = service.cachedState(playerId, RESET_KEY);
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

        service.selectQuestDifficulty(player, RESET_KEY, 0, "easy").join();

        assertEquals(generated.instanceId(), service.cachedState(playerId, RESET_KEY).questAtSlot(0).instanceId());
    }

    @Test
    public void loadInFlightDuringResetDoesNotCacheStaleQuests() {
        GeneratedQuest beforeReset = quest(0, 4, false);
        CompletableFuture<List<GeneratedQuest>> staleLoad = new CompletableFuture<>();
        when(repository.loadQuests(playerId, RESET_KEY)).thenReturn(staleLoad, CompletableFuture.completedFuture(List.of()));
        when(repository.loadDifficultyCompletedCounts(playerId)).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(repository.loadExecutedMilestones(playerId)).thenReturn(CompletableFuture.completedFuture(Set.of()));
        when(repository.resetDailyQuests(playerId, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<PlayerQuestState> loading = service.ensurePlayerStateAsync(playerId, RESET_KEY);
        service.resetDaily(playerId, RESET_KEY).join();
        staleLoad.complete(List.of(beforeReset));

        PlayerQuestState loaded = loading.join();
        assertTrue(loaded.quests().isEmpty());
        assertTrue(service.cachedState(playerId, RESET_KEY).quests().isEmpty());
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

        service.ensurePlayerStateAsync(playerId, RESET_KEY);
        service.resetDaily(playerId, RESET_KEY).join();
        CompletableFuture<PlayerQuestState> afterReset = service.ensurePlayerStateAsync(playerId, RESET_KEY);

        verify(repository, times(2)).loadQuests(playerId, RESET_KEY);
        GeneratedQuest fresh = quest(1, 0, false);
        freshLoad.complete(List.of(fresh));
        staleLoad.complete(List.of(quest(0, 4, false)));
        assertEquals(List.of(fresh), afterReset.join().quests());
        assertEquals(List.of(fresh), service.cachedState(playerId, RESET_KEY).quests());
    }

    @Test
    public void loadWithoutInvalidationIsSharedAndCached() {
        CompletableFuture<List<GeneratedQuest>> pending = new CompletableFuture<>();
        when(repository.loadQuests(playerId, RESET_KEY)).thenReturn(pending);
        when(repository.loadDifficultyCompletedCounts(playerId)).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(repository.loadExecutedMilestones(playerId)).thenReturn(CompletableFuture.completedFuture(Set.of()));

        CompletableFuture<PlayerQuestState> first = service.ensurePlayerStateAsync(playerId, RESET_KEY);
        CompletableFuture<PlayerQuestState> second = service.ensurePlayerStateAsync(playerId, RESET_KEY);
        GeneratedQuest quest = quest(0, 0, false);
        pending.complete(List.of(quest));

        assertSame(first.join(), second.join());
        assertSame(first.join(), service.cachedState(playerId, RESET_KEY));
        verify(repository, times(1)).loadQuests(playerId, RESET_KEY);
    }

    private void stubLoad(List<GeneratedQuest> quests) {
        when(repository.loadQuests(playerId, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(quests));
        when(repository.loadDifficultyCompletedCounts(playerId)).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(repository.loadExecutedMilestones(playerId)).thenReturn(CompletableFuture.completedFuture(Set.of()));
    }

    private static QuestDefinition definition(String id) {
        return new QuestDefinition(id, QuestTypes.KILL_MOB, true, "easy", "Easy", "Kill zombies", List.of(), Map.of(), Map.of(), null, List.of());
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
