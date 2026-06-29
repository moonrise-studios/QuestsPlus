package gg.moonrise.quests.core.service;

import gg.moonrise.quests.model.GlobalQuestContribution;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestCompletionStats;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.model.QuestMilestone;
import gg.moonrise.quests.model.QuestMilestoneClaim;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestTypes;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExternalSqlRepositoryDatabaseTest {

    private static final UUID GLOBAL_PLAYER_ID = new UUID(0L, 0L);

    @TempDir
    private Path tempDir;

    @TestFactory
    Stream<DynamicTest> questRepositoryFlowsRunAgainstConfiguredNetworkDatabases() {
        List<SqlTestHarness.DatabaseTarget> targets = SqlTestHarness.configuredExternalTargets();
        if (targets.isEmpty()) {
            return Stream.of(DynamicTest.dynamicTest("network database targets are opt-in", () -> assumeTrue(false, SqlTestHarness.externalConfigurationHelp())));
        }
        return targets.stream()
                .map(target -> DynamicTest.dynamicTest(target.displayName() + " quest repository flow", () -> runQuestRepositoryFlow(target)));
    }

    @TestFactory
    Stream<DynamicTest> globalQuestRepositoryFlowsRunAgainstConfiguredNetworkDatabases() {
        List<SqlTestHarness.DatabaseTarget> targets = SqlTestHarness.configuredExternalTargets();
        if (targets.isEmpty()) {
            return Stream.of(DynamicTest.dynamicTest("network database targets are opt-in", () -> assumeTrue(false, SqlTestHarness.externalConfigurationHelp())));
        }
        return targets.stream()
                .map(target -> DynamicTest.dynamicTest(target.displayName() + " global quest repository flow", () -> runGlobalQuestRepositoryFlow(target)));
    }

    private void runQuestRepositoryFlow(SqlTestHarness.DatabaseTarget target) throws Exception {
        Files.createDirectories(tempDir.resolve(target.id()));
        SqlProvider provider = SqlTestHarness.open(tempDir.resolve(target.id()), target);
        try {
            assertEquals(target.displayName(), provider.currentDatabase().displayName());
            QuestRepository repository = new QuestRepository(provider);

            UUID playerId = UUID.randomUUID();
            GeneratedQuest slotOne = quest(playerId, "daily", "slot-one", 1, false, 0, false);
            GeneratedQuest slotZero = quest(playerId, "daily", "slot-zero", 0, true, 4, false);

            repository.saveGeneratedQuests(List.of(slotOne, slotZero)).join();

            List<GeneratedQuest> loaded = repository.loadQuests(playerId, "daily").join();
            assertEquals(List.of(slotZero.instanceId(), slotOne.instanceId()), loaded.stream().map(GeneratedQuest::instanceId).toList());
            assertTrue(loaded.getFirst().premium());

            repository.updateProgress(slotZero.withProgress(10, true)).join();

            GeneratedQuest updated = repository.loadQuests(playerId, "daily").join().getFirst();
            assertEquals(10, updated.progress());
            assertTrue(updated.completed());

            GeneratedQuest replacement = quest(playerId, "daily", "replacement", 0, false, 0, false);
            assertEquals(1, repository.replaceQuestAndIncrementRerolls(slotZero.instanceId(), replacement).join());
            assertEquals(1, repository.loadRerollsUsed(playerId, "daily").join());

            QuestMilestone first = milestone("easy", 1, "First Quest");
            QuestMilestone second = milestone("easy", 2, "Second Quest");
            QuestCompletionStats firstStats = repository.incrementCompletionStats(playerId, "easy", List.of(first, second)).join();
            QuestCompletionStats secondStats = repository.incrementCompletionStats(playerId, "easy", List.of(first, second)).join();

            assertEquals(1, firstStats.difficultyCompleted());
            assertEquals(List.of(first), firstStats.newlyExecutedMilestones());
            assertEquals(2, secondStats.difficultyCompleted());
            assertEquals(List.of(second), secondStats.newlyExecutedMilestones());

            QuestDifficulty easy = new QuestDifficulty("easy", "<green>Easy", 10, 10, Map.of(), List.of(), List.of(), List.of(first, second));
            List<QuestMilestoneClaim> claims = repository.claimEligibleMilestones(playerId, Map.of("easy", 2), List.of(easy)).join();

            assertEquals(List.of(), claims);
            assertEquals(
                    List.of(PlayerQuestState.milestoneKey("easy", 1), PlayerQuestState.milestoneKey("easy", 2)),
                    repository.loadExecutedMilestones(playerId).join().stream().sorted().toList()
            );

            assertEquals(1, repository.recordQuestResetPurchaseAndReset(playerId, "daily", 1).join());
            assertEquals(List.of(), repository.loadQuests(playerId, "daily").join());
            assertEquals(-1, repository.recordQuestResetPurchaseAndReset(playerId, "daily", 1).join());
        } finally {
            provider.onDisable();
        }
    }

    private void runGlobalQuestRepositoryFlow(SqlTestHarness.DatabaseTarget target) throws Exception {
        Files.createDirectories(tempDir.resolve(target.id() + "-global"));
        SqlProvider provider = SqlTestHarness.open(tempDir.resolve(target.id() + "-global"), target);
        try {
            assertEquals(target.displayName(), provider.currentDatabase().displayName());
            GlobalQuestRepository repository = new GlobalQuestRepository(provider);

            UUID playerA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
            UUID playerB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
            LocalDateTime now = LocalDateTime.now();
            String periodKey = "week-" + UUID.randomUUID();
            GlobalQuestState state = state(periodKey, now.minusDays(8), now.minusDays(6), 0, false);

            repository.save(state).join();
            assertEquals(state.quest().instanceId(), repository.loadActive(periodKey).join().quest().instanceId());

            repository.updateProgressAndContribution(withProgress(state, 7, false), playerB, 7).join();
            repository.updateProgressAndContribution(withProgress(state, 15, true), playerA, 8).join();

            GlobalQuestState loaded = repository.loadActive(periodKey).join();
            assertEquals(15, loaded.quest().progress());
            assertTrue(loaded.quest().completed());
            assertEquals(8, loaded.contribution(playerA));
            assertEquals(7, loaded.contribution(playerB));

            List<GlobalQuestContribution> ranked = repository.loadRankedContributions(state.quest().instanceId()).join();
            assertEquals(List.of(playerA, playerB), ranked.stream().map(GlobalQuestContribution::playerId).toList());
            assertEquals(List.of(1, 2), ranked.stream().map(GlobalQuestContribution::rank).toList());

            assertEquals(List.of(periodKey), repository.loadExpiredUnrewarded(now).join().stream().map(GlobalQuestState::periodKey).toList());
            assertTrue(repository.markRewardsExecuted(state.quest().instanceId()).join());
            assertFalse(repository.markRewardsExecuted(state.quest().instanceId()).join());
            assertTrue(repository.loadExpiredUnrewarded(now).join().isEmpty());

            assertTrue(repository.insertRewardExecution(state.quest().instanceId(), playerA, 10).join());
            assertFalse(repository.insertRewardExecution(state.quest().instanceId(), playerA, 10).join());

            repository.deletePeriod(periodKey).join();

            assertNull(repository.loadActive(periodKey).join());
            assertTrue(repository.loadRankedContributions(state.quest().instanceId()).join().isEmpty());
        } finally {
            provider.onDisable();
        }
    }

    private static GeneratedQuest quest(UUID playerId, String resetKey, String definitionId, int slotIndex, boolean premium, int progress, boolean completed) {
        return new GeneratedQuest(
                UUID.randomUUID(),
                playerId,
                resetKey,
                definitionId,
                QuestTypes.KILL_MOB,
                "easy",
                "<green>Easy",
                "<green>Mob Hunt",
                List.of("<gray>Kill mobs."),
                Map.of("mob", "zombie"),
                slotIndex,
                premium,
                10,
                progress,
                completed
        );
    }

    private static QuestMilestone milestone(String difficultyId, int completed, String displayName) {
        return new QuestMilestone(difficultyId, completed, displayName, List.of("<gray>" + displayName), List.of("eco give <player> " + completed));
    }

    private static GlobalQuestState withProgress(GlobalQuestState state, int progress, boolean completed) {
        return new GlobalQuestState(
                state.quest().withProgress(progress, completed),
                state.periodKey(),
                state.startsAt(),
                state.endsAt(),
                Map.of(),
                state.rewardsExecuted()
        );
    }

    private static GlobalQuestState state(String periodKey, LocalDateTime startsAt, LocalDateTime endsAt, int progress, boolean completed) {
        GeneratedQuest quest = new GeneratedQuest(
                UUID.randomUUID(),
                GLOBAL_PLAYER_ID,
                periodKey,
                "global-kill-all-mobs",
                QuestTypes.KILL_ALL_MOBS,
                "easy",
                "<green>Easy",
                "<green>Global Hunt",
                List.of("<gray>Kill mobs together."),
                Map.of("mob", "zombie"),
                100,
                progress,
                completed
        );
        return new GlobalQuestState(quest, periodKey, startsAt, endsAt, Map.of(), false);
    }
}
