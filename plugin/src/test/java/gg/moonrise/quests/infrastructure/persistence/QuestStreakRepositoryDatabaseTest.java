package gg.moonrise.quests.infrastructure.persistence;

import gg.moonrise.quests.domain.model.QuestStreakState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestStreakRepositoryDatabaseTest {

    @TempDir
    private Path tempDir;

    private SqlProvider sqlProvider;
    private QuestStreakRepository repository;
    private UUID playerId;

    @BeforeEach
    public void setUp() {
        sqlProvider = SqliteTestHarness.open(tempDir);
        repository = new QuestStreakRepository(sqlProvider);
        playerId = UUID.randomUUID();
    }

    @AfterEach
    public void tearDown() {
        if (sqlProvider != null) {
            sqlProvider.onDisable();
        }
    }

    @Test
    public void loadReturnsEmptyStateWhenNothingIsStored() {
        assertEquals(QuestStreakState.empty(playerId), repository.load(playerId).join());
    }

    @Test
    public void savedStateRoundTripsWithClaimedMilestones() {
        QuestStreakState state = new QuestStreakState(playerId, 4, 9, "2026-06-19", "2026-06-20", 2, "2026-06-15", 1, 3, Set.of());
        repository.save(state).join();
        repository.claimMilestones(playerId, List.of(3)).join();

        QuestStreakState loaded = repository.load(playerId).join();

        assertEquals(state.withClaimedMilestones(Set.of(3)), loaded);
    }

    @Test
    public void saveStoresMissingResetKeysAsEmptyText() {
        repository.save(new QuestStreakState(playerId, 1, 1, null, null, 0, null, 0, 0, Set.of())).join();

        QuestStreakState loaded = repository.load(playerId).join();

        assertEquals("", loaded.lastCompletedResetKey());
        assertEquals("", loaded.lastEvaluatedResetKey());
        assertEquals("", loaded.lostResetKey());
    }

    @Test
    public void claimMilestonesReturnsOnlyStreaksNotClaimedBefore() {
        assertEquals(Set.of(3), repository.claimMilestones(playerId, List.of(3)).join());

        Set<Integer> claimed = repository.claimMilestones(playerId, List.of(3, 7)).join();

        assertEquals(List.of(7), List.copyOf(claimed));
        assertEquals(Set.of(3, 7), repository.load(playerId).join().claimedMilestones());
    }

    @Test
    public void claimMilestonesAreKeptPerPlayer() {
        UUID otherPlayer = UUID.randomUUID();
        repository.claimMilestones(playerId, List.of(3)).join();

        assertEquals(Set.of(3), repository.claimMilestones(otherPlayer, List.of(3)).join());
    }
}
