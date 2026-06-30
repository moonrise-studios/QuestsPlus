package gg.moonrise.quests.core.service;

import gg.moonrise.quests.model.GlobalQuestContribution;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalQuestRepositoryDatabaseTest {

    private static final UUID GLOBAL_PLAYER_ID = new UUID(0L, 0L);

    @TempDir
    private Path tempDir;

    private SqlProvider sqlProvider;
    private GlobalQuestRepository repository;

    @BeforeEach
    public void setUp() {
        sqlProvider = SqliteTestHarness.open(tempDir);
        repository = new GlobalQuestRepository(sqlProvider);
    }

    @AfterEach
    public void tearDown() {
        if (sqlProvider != null) {
            sqlProvider.onDisable();
        }
    }

    @Test
    public void globalQuestProgressAndContributionsRoundTripAndRankDeterministically() {
        UUID playerA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID playerB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        GlobalQuestState initial = state("week-1", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(6), 0, false);
        repository.save(initial).join();

        GlobalQuestState loaded = repository.loadActive("week-1").join();

        assertEquals(initial.quest().instanceId(), loaded.quest().instanceId());
        assertEquals(0, loaded.quest().progress());
        assertEquals(0, loaded.participants());

        repository.updateProgressAndContribution(withProgress(initial, 7, false), playerB, 7).join();
        repository.updateProgressAndContribution(withProgress(initial, 12, false), playerA, 5).join();
        repository.updateProgressAndContribution(withProgress(initial, 15, true), playerA, 3).join();

        loaded = repository.loadActive("week-1").join();
        assertEquals(15, loaded.quest().progress());
        assertTrue(loaded.quest().completed());
        assertEquals(8, loaded.contribution(playerA));
        assertEquals(7, loaded.contribution(playerB));
        assertEquals(2, loaded.participants());

        List<GlobalQuestContribution> ranked = repository.loadRankedContributions(initial.quest().instanceId()).join();

        assertEquals(List.of(playerA, playerB), ranked.stream().map(GlobalQuestContribution::playerId).toList());
        assertEquals(List.of(1, 2), ranked.stream().map(GlobalQuestContribution::rank).toList());
        assertEquals(List.of(2, 2), ranked.stream().map(GlobalQuestContribution::participants).toList());
    }

    @Test
    public void expiredRewardsExecutionAndPeriodDeletionArePersisted() {
        LocalDateTime now = LocalDateTime.now();
        GlobalQuestState older = state("week-oldest", now.minusDays(8), now.minusDays(6), 100, true);
        GlobalQuestState newer = state("week-newer", now.minusDays(7), now.minusDays(5), 50, false);
        GlobalQuestState future = state("week-future", now, now.plusDays(7), 0, false);
        repository.save(older).join();
        repository.save(newer).join();
        repository.save(future).join();

        assertEquals(List.of("week-oldest", "week-newer"), repository.loadExpiredUnrewarded(now).join().stream().map(GlobalQuestState::periodKey).toList());
        assertTrue(repository.markRewardsExecuted(older.quest().instanceId()).join());
        assertFalse(repository.markRewardsExecuted(older.quest().instanceId()).join());
        assertEquals(List.of("week-newer"), repository.loadExpiredUnrewarded(now).join().stream().map(GlobalQuestState::periodKey).toList());

        UUID playerId = UUID.randomUUID();
        assertTrue(repository.insertRewardExecution(newer.quest().instanceId(), playerId, 10).join());
        assertFalse(repository.insertRewardExecution(newer.quest().instanceId(), playerId, 10).join());

        repository.updateProgressAndContribution(withProgress(newer, 75, false), playerId, 25).join();
        assertEquals(1, repository.loadRankedContributions(newer.quest().instanceId()).join().size());

        repository.deletePeriod("week-newer").join();

        assertNull(repository.loadActive("week-newer").join());
        assertEquals(List.of(), repository.loadRankedContributions(newer.quest().instanceId()).join());
        assertEquals(List.of(), repository.loadExpiredUnrewarded(now).join().stream().map(GlobalQuestState::periodKey).toList());
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
