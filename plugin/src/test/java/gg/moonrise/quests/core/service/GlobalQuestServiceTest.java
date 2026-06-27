package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.QuestVariableSelector;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestProgressResult;
import gg.moonrise.quests.sdk.model.QuestTypes;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalQuestServiceTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void globalAdminProgressCanCreditEventDrivenGoalTypes() throws Exception {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        QuestDefinitionService definitionService = mock(QuestDefinitionService.class);
        GlobalQuestRepository repository = mock(GlobalQuestRepository.class);
        QuestProgressIndicatorService progressIndicatorService = mock(QuestProgressIndicatorService.class);
        GoalHandler goalHandler = mock(GoalHandler.class);
        QuestVariableSelector selector = mock(QuestVariableSelector.class);
        Player player = server.addPlayer("AdminTarget");
        Config config = new Config();
        GlobalQuestService service = new GlobalQuestService(configProvider, definitionService, repository, progressIndicatorService);
        GlobalQuestState activeState = activeGlobalState(player.getUniqueId());

        when(configProvider.get()).thenReturn(config);
        when(definitionService.difficulty("easy")).thenReturn(new QuestDifficulty("easy", "<green><b>EASY", -1, -1, Map.of(), List.of(), List.of(), List.of()));
        when(definitionService.selector("LIST")).thenReturn(selector);
        when(definitionService.handler(any())).thenReturn(goalHandler);
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
    void incompleteGlobalQuestBelowHalfGetsNoRewards() throws Exception {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        GlobalQuestService service = serviceWithConfig(configProvider);
        Config config = configWithRewardTiers();
        when(configProvider.get()).thenReturn(config);

        List<Config.GlobalRewardTierConfig> tiers = service.rewardTiersForProgress(globalQuest(49, 100, false));

        assertTrue(tiers.isEmpty());
    }

    @Test
    void incompleteGlobalQuestAtHalfGetsReducedRewards() throws Exception {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        GlobalQuestService service = serviceWithConfig(configProvider);
        Config config = configWithRewardTiers();
        when(configProvider.get()).thenReturn(config);

        List<Config.GlobalRewardTierConfig> tiers = service.rewardTiersForProgress(globalQuest(50, 100, false));

        assertEquals("Reduced Top 10%", tiers.getFirst().getDisplayName());
    }

    @Test
    void completedGlobalQuestGetsFullRewards() throws Exception {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        GlobalQuestService service = serviceWithConfig(configProvider);
        Config config = configWithRewardTiers();
        when(configProvider.get()).thenReturn(config);

        List<Config.GlobalRewardTierConfig> tiers = service.rewardTiersForProgress(globalQuest(100, 100, true));

        assertEquals("Full Top 10%", tiers.getFirst().getDisplayName());
    }

    @Test
    void reducedRewardMinimumPercentIsConfigurable() throws Exception {
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
