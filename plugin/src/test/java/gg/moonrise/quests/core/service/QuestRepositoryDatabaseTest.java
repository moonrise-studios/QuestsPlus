package gg.moonrise.quests.core.service;

import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestCompletionStats;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.model.QuestMilestone;
import gg.moonrise.quests.model.QuestMilestoneClaim;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestRepositoryDatabaseTest {

    @TempDir
    private Path tempDir;

    private SqliteProvider sqliteProvider;
    private QuestRepository repository;

    @BeforeEach
    void setUp() {
        sqliteProvider = SqliteTestHarness.open(tempDir);
        repository = new QuestRepository(sqliteProvider);
    }

    @AfterEach
    void tearDown() {
        if (sqliteProvider != null) {
            sqliteProvider.onDisable();
        }
    }

    @Test
    void providerCreatesStorageDatabaseAndRepositoryRoundTripsQuestsBySlot() {
        assertTrue(Files.exists(tempDir.resolve("storage/quests.db")));

        UUID playerId = UUID.randomUUID();
        GeneratedQuest slotTwo = quest(playerId, "daily", "slot-two", 2, false, 3, false);
        GeneratedQuest noSlot = quest(playerId, "daily", "no-slot", -1, false, 0, false);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("mob", "zombie");
        GeneratedQuest slotZero = new GeneratedQuest(
                UUID.randomUUID(),
                playerId,
                "daily",
                "slot-zero",
                QuestTypes.KILL_MOB,
                "hard",
                "<red><b>HARD",
                "<red>Zombie Hunt",
                List.of("<gray>Kill <white><mob></white>."),
                variables,
                0,
                true,
                10,
                7,
                false
        );

        repository.saveGeneratedQuests(List.of(slotTwo, noSlot, slotZero)).join();

        List<GeneratedQuest> loaded = repository.loadQuests(playerId, "daily").join();
        assertEquals(List.of(slotZero.instanceId(), slotTwo.instanceId(), noSlot.instanceId()), loaded.stream().map(GeneratedQuest::instanceId).toList());
        assertEquals("hard", loaded.getFirst().difficultyId());
        assertEquals("<red><b>HARD", loaded.getFirst().difficultyDisplayName());
        assertEquals(List.of("<gray>Kill <white><mob></white>."), loaded.getFirst().description());
        assertEquals(variables, loaded.getFirst().variables());
        assertTrue(loaded.getFirst().premium());

        repository.updateProgress(slotZero.withProgress(10, true)).join();

        GeneratedQuest updated = repository.loadQuests(playerId, "daily").join().getFirst();
        assertEquals(10, updated.progress());
        assertTrue(updated.completed());
    }

    @Test
    void purchasedQuestResetClearsOnlyTheCurrentPlayerWindowAndHonorsLimit() {
        UUID playerId = UUID.randomUUID();
        UUID otherPlayerId = UUID.randomUUID();
        repository.saveGeneratedQuests(List.of(
                quest(playerId, "daily", "daily-one", 0, false, 10, true),
                quest(playerId, "daily", "daily-two", 1, false, 10, true),
                quest(playerId, "tomorrow", "other-window", 0, false, 2, false),
                quest(otherPlayerId, "daily", "other-player", 0, false, 2, false)
        )).join();

        assertEquals(1, repository.recordQuestResetPurchaseAndReset(playerId, "daily", 1).join());

        assertEquals(List.of(), repository.loadQuests(playerId, "daily").join());
        assertEquals(1, repository.loadQuests(playerId, "tomorrow").join().size());
        assertEquals(1, repository.loadQuests(otherPlayerId, "daily").join().size());
        assertEquals(1, repository.loadQuestResetsUsed(playerId, "daily").join());

        assertEquals(-1, repository.recordQuestResetPurchaseAndReset(playerId, "daily", 1).join());
        assertEquals(-1, repository.recordQuestResetPurchaseAndReset(playerId, "daily", 0).join());
        assertEquals(1, repository.loadQuestResetsUsed(playerId, "daily").join());
    }

    @Test
    void rerollReplacementDeletesOldQuestInsertsReplacementAndTracksUsage() {
        UUID playerId = UUID.randomUUID();
        GeneratedQuest original = quest(playerId, "daily", "original", 0, false, 0, false);
        GeneratedQuest replacement = quest(playerId, "daily", "replacement", 0, false, 0, false);
        GeneratedQuest secondReplacement = quest(playerId, "daily", "second-replacement", 0, false, 0, false);
        repository.saveGeneratedQuests(List.of(original)).join();

        assertEquals(1, repository.replaceQuestAndIncrementRerolls(original.instanceId(), replacement).join());

        List<GeneratedQuest> loaded = repository.loadQuests(playerId, "daily").join();
        assertEquals(List.of(replacement.instanceId()), loaded.stream().map(GeneratedQuest::instanceId).toList());
        assertEquals(1, repository.loadRerollsUsed(playerId, "daily").join());

        assertEquals(2, repository.replaceQuestAndIncrementRerolls(replacement.instanceId(), secondReplacement).join());
        assertEquals(List.of(secondReplacement.instanceId()), repository.loadQuests(playerId, "daily").join().stream().map(GeneratedQuest::instanceId).toList());
        assertEquals(2, repository.loadRerollsUsed(playerId, "daily").join());

        repository.resetRerolls(playerId, "daily").join();

        assertEquals(0, repository.loadRerollsUsed(playerId, "daily").join());
    }

    @Test
    void completionStatsAndMilestoneClaimsArePersistedAndDeduplicated() {
        UUID playerId = UUID.randomUUID();
        QuestMilestone first = milestone("easy", 1, "First Quest");
        QuestMilestone fifth = milestone("easy", 5, "Fifth Quest");

        QuestCompletionStats firstStats = repository.incrementCompletionStats(playerId, "easy", List.of(first, fifth)).join();

        assertEquals(1, firstStats.questsCompleted());
        assertEquals(1, firstStats.difficultyCompleted());
        assertEquals(List.of(first), firstStats.newlyExecutedMilestones());

        QuestCompletionStats secondStats = repository.incrementCompletionStats(playerId, "easy", List.of(first, fifth)).join();

        assertEquals(2, secondStats.questsCompleted());
        assertEquals(2, secondStats.difficultyCompleted());
        assertEquals(List.of(), secondStats.newlyExecutedMilestones());
        assertEquals(Map.of("easy", 2), repository.loadDifficultyCompletedCounts(playerId).join());

        QuestDifficulty easy = new QuestDifficulty("easy", "<green>Easy", 10, 10, Map.of(), List.of(), List.of(), List.of(first, fifth));
        List<QuestMilestoneClaim> claims = repository.claimEligibleMilestones(playerId, Map.of("easy", 5), List.of(easy)).join();

        assertEquals(1, claims.size());
        assertEquals(fifth, claims.getFirst().milestone());
        assertEquals(List.of(), repository.claimEligibleMilestones(playerId, Map.of("easy", 5), List.of(easy)).join());
        assertEquals(
                List.of(PlayerQuestState.milestoneKey("easy", 1), PlayerQuestState.milestoneKey("easy", 5)),
                repository.loadExecutedMilestones(playerId).join().stream().sorted().toList()
        );
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
}
