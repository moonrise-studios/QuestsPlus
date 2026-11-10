package gg.moonrise.quests.application.quest;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.DailyFile;
import gg.moonrise.quests.domain.model.PlayerQuestState;
import gg.moonrise.quests.domain.model.QuestDifficulty;
import gg.moonrise.quests.domain.model.QuestSelectionResult;
import gg.moonrise.quests.domain.model.QuestSelectionStatus;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestTypes;
import gg.moonrise.quests.infrastructure.persistence.QuestRepository;
import gg.moonrise.quests.application.global.GlobalQuestService;
import gg.moonrise.quests.application.indicator.QuestProgressIndicatorService;
import gg.moonrise.quests.application.streak.QuestStreakService;
import gg.moonrise.quests.core.service.ConfigProvider;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestSelectionServiceTest {

    private static final String RESET_KEY = "daily";

    private final UUID playerId = UUID.randomUUID();
    private final QuestDefinition zombies = definition("zombies", "easy");
    private final QuestDefinition skeletons = definition("skeletons", "easy");
    private QuestDefinitionService definitionService;
    private QuestRepository repository;
    private GoalHandler handler;
    private Player player;
    private QuestServices service;

    @BeforeEach
    public void setUp() {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(Config.compose(null, new DailyFile(3), null, null, null, null, null));
        definitionService = mock(QuestDefinitionService.class);
        repository = mock(QuestRepository.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        service = QuestServices.create(configProvider, definitionService, repository, mock(QuestStreakService.class),
                mock(GlobalQuestService.class), mock(QuestProgressIndicatorService.class));

        handler = mock(GoalHandler.class);
        when(definitionService.handler(QuestTypes.KILL_MOB)).thenReturn(handler);
        when(definitionService.enabledDefinitions()).thenReturn(List.of(zombies, skeletons));
        when(handler.createGeneratedQuest(any(), any(), anyString(), anyMap()))
                .thenAnswer(invocation -> quest(invocation.<QuestDefinition>getArgument(0).id(), 0, 0, false));
        when(repository.saveGeneratedQuests(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    public void rerollAtCachedLimitIsRejectedBeforeGeneratingOrWriting() {
        stubLoad(List.of(quest("zombies", 0, 2, false)));
        when(player.hasPermission("questsplus.reroll.vip")).thenReturn(true);
        when(repository.loadRerollsUsed(playerId, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(1));

        QuestSelectionResult result = service.selection().rerollQuestDifficulty(player, RESET_KEY, 0, "easy").join();

        assertEquals(QuestSelectionStatus.NO_REROLLS, result.status());
        assertEquals(1, result.rerollsUsed());
        assertEquals(1, result.rerollsLimit());
        assertEquals(0, service.usage().rerollsRemaining(player, RESET_KEY));
        verify(handler, never()).createGeneratedQuest(any(), any(), anyString(), anyMap());
        verify(repository, never()).replaceQuestAndIncrementRerolls(any(), any(), anyInt());
    }

    @Test
    public void rerollWithoutAnyRerollPermissionReportsNoRerolls() {
        stubLoad(List.of(quest("zombies", 0, 2, false)));
        when(repository.loadRerollsUsed(playerId, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(0));

        QuestSelectionResult result = service.selection().rerollQuestDifficulty(player, RESET_KEY, 0, "easy").join();

        assertEquals(QuestSelectionStatus.NO_REROLLS, result.status());
        assertEquals(0, result.rerollsLimit());
        verify(repository, never()).replaceQuestAndIncrementRerolls(any(), any(), anyInt());
    }

    @Test
    public void rerollOfCompletedQuestReportsCompletedAndKeepsIt() {
        GeneratedQuest completed = quest("zombies", 0, 10, true);
        stubLoad(List.of(completed));
        allowRerolls(0);

        QuestSelectionResult result = service.selection().rerollQuestDifficulty(player, RESET_KEY, 0, "easy").join();

        assertEquals(QuestSelectionStatus.COMPLETED, result.status());
        assertEquals(completed, result.state().questAtSlot(0).orElseThrow());
        verify(repository, never()).replaceQuestAndIncrementRerolls(any(), any(), anyInt());
    }

    @Test
    public void rerollOfEmptySlotIsInvalid() {
        stubLoad(List.of());
        allowRerolls(0);

        QuestSelectionResult result = service.selection().rerollQuestDifficulty(player, RESET_KEY, 1, "easy").join();

        assertEquals(QuestSelectionStatus.INVALID, result.status());
        verify(repository, never()).replaceQuestAndIncrementRerolls(any(), any(), anyInt());
    }

    @Test
    public void rerollWithUnmetDifficultyRequirementsIsRejected() {
        stubLoad(List.of(quest("zombies", 0, 2, false)));
        allowRerolls(0);
        when(definitionService.difficulty("easy")).thenReturn(Optional.of(difficulty("easy", Map.of("hard", 1))));

        QuestSelectionResult result = service.selection().rerollQuestDifficulty(player, RESET_KEY, 0, "easy").join();

        assertEquals(QuestSelectionStatus.REQUIREMENTS_NOT_MET, result.status());
        verify(repository, never()).replaceQuestAndIncrementRerolls(any(), any(), anyInt());
    }

    @Test
    public void rerollPicksADifferentDefinitionThanTheReplacedQuest() {
        GeneratedQuest existing = quest("zombies", 0, 2, false);
        stubLoad(List.of(existing));
        allowRerolls(0);
        when(repository.replaceQuestAndIncrementRerolls(any(), any(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(new QuestRepository.RerollOutcome.Rerolled(1)));

        QuestSelectionResult result = service.selection().rerollQuestDifficulty(player, RESET_KEY, 0, "easy").join();

        assertEquals(QuestSelectionStatus.REROLLED, result.status());
        verify(handler).createGeneratedQuest(eq(skeletons), eq(playerId), eq(RESET_KEY), anyMap());
        verify(handler, never()).createGeneratedQuest(eq(zombies), any(), anyString(), anyMap());
        verify(repository).replaceQuestAndIncrementRerolls(eq(existing.instanceId()), any(), eq(1));
        assertEquals("skeletons", result.state().questAtSlot(0).orElseThrow().definitionId());
        assertEquals(0, service.usage().rerollsRemaining(player, RESET_KEY));
    }

    @Test
    public void rerollOfQuestDeletedBehindTheCacheReloadsTheState() {
        stubLoad(List.of(quest("zombies", 0, 2, false)));
        allowRerolls(0);
        when(repository.replaceQuestAndIncrementRerolls(any(), any(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(new QuestRepository.RerollOutcome.QuestMissing()));
        PlayerQuestState cached = service.state().ensurePlayerStateAsync(playerId, RESET_KEY).join();

        QuestSelectionResult result = service.selection().rerollQuestDifficulty(player, RESET_KEY, 0, "easy").join();

        assertEquals(QuestSelectionStatus.INVALID, result.status());
        assertNotSame(cached, result.state());
        verify(repository, times(2)).loadQuests(playerId, RESET_KEY);
    }

    @Test
    public void selectionIntoFilledSlotReportsSlotFilledWithoutSaving() {
        stubLoad(List.of(quest("zombies", 0, 0, false)));

        QuestSelectionResult result = service.selection().selectQuestDifficulty(player, RESET_KEY, 0, "easy").join();

        assertEquals(QuestSelectionStatus.SLOT_FILLED, result.status());
        verify(repository, never()).saveGeneratedQuests(any());
    }

    @Test
    public void selectionWithUnmetDifficultyRequirementsIsRejected() {
        stubLoad(List.of());
        when(definitionService.difficulty("hard")).thenReturn(Optional.of(difficulty("hard", Map.of("easy", 5))));

        QuestSelectionResult result = service.selection().selectQuestDifficulty(player, RESET_KEY, 0, "hard").join();

        assertEquals(QuestSelectionStatus.REQUIREMENTS_NOT_MET, result.status());
        verify(repository, never()).saveGeneratedQuests(any());
    }

    @Test
    public void selectionOfDifficultyWithoutEnabledDefinitionsReportsNoAvailableQuest() {
        stubLoad(List.of());

        QuestSelectionResult result = service.selection().selectQuestDifficulty(player, RESET_KEY, 0, "hard").join();

        assertEquals(QuestSelectionStatus.NO_AVAILABLE_QUEST, result.status());
        assertTrue(result.state().quests().isEmpty());
        verify(repository, never()).saveGeneratedQuests(any());
    }

    @Test
    public void selectionWithBlankDifficultyIsInvalid() {
        stubLoad(List.of());

        QuestSelectionResult result = service.selection().selectQuestDifficulty(player, RESET_KEY, 0, " ").join();

        assertEquals(QuestSelectionStatus.INVALID, result.status());
        verify(repository, never()).saveGeneratedQuests(any());
    }

    @Test
    public void selectionIntoUnlockedPremiumSlotStoresAPremiumQuestAtThatSlot() {
        stubLoad(List.of());
        when(player.hasPermission("questsplus.premium.vip")).thenReturn(true);

        QuestSelectionResult result = service.selection().selectQuestDifficulty(player, RESET_KEY, 3, "easy").join();

        assertEquals(QuestSelectionStatus.SELECTED, result.status());
        GeneratedQuest saved = savedQuest();
        assertEquals(3, saved.slotIndex());
        assertTrue(saved.premium());
        assertEquals(saved, result.state().questAtSlot(3).orElseThrow());
    }

    @Test
    public void selectionPrefersDefinitionsThatAreNotAlreadyActive() {
        stubLoad(List.of(quest("zombies", 0, 0, false)));

        QuestSelectionResult result = service.selection().selectQuestDifficulty(player, RESET_KEY, 1, "easy").join();

        assertEquals(QuestSelectionStatus.SELECTED, result.status());
        assertEquals("skeletons", savedQuest().definitionId());
        assertEquals(1, savedQuest().slotIndex());
    }

    @SuppressWarnings("unchecked")
    private GeneratedQuest savedQuest() {
        ArgumentCaptor<List<GeneratedQuest>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveGeneratedQuests(saved.capture());
        return saved.getValue().getFirst();
    }

    private void allowRerolls(int used) {
        when(player.hasPermission("questsplus.reroll.vip")).thenReturn(true);
        when(repository.loadRerollsUsed(playerId, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(used));
    }

    private void stubLoad(List<GeneratedQuest> quests) {
        when(repository.loadQuests(playerId, RESET_KEY)).thenReturn(CompletableFuture.completedFuture(quests));
        when(repository.loadDifficultyCompletedCounts(playerId)).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(repository.loadExecutedMilestones(playerId)).thenReturn(CompletableFuture.completedFuture(Set.of()));
    }

    private static QuestDifficulty difficulty(String id, Map<String, Integer> requirements) {
        return new QuestDifficulty(id, id, 0, 0, requirements, List.of(), List.of(), List.of());
    }

    private static QuestDefinition definition(String id, String difficultyId) {
        return new QuestDefinition(id, QuestTypes.KILL_MOB, true, difficultyId, "Easy", "Kill " + id, List.of(), Map.of(), Map.of(), null, List.of());
    }

    private GeneratedQuest quest(String definitionId, int slotIndex, int progress, boolean completed) {
        return new GeneratedQuest(
                UUID.randomUUID(),
                playerId,
                RESET_KEY,
                definitionId,
                QuestTypes.KILL_MOB,
                "easy",
                "Easy",
                "Kill " + definitionId,
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
