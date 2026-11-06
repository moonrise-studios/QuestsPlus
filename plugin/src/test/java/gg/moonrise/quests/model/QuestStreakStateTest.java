package gg.moonrise.quests.model;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestStreakStateTest {

    private static final QuestStreakState BASE = new QuestStreakState(
            UUID.randomUUID(), 4, 6, "2026-06-18", "2026-06-19", 2, "2026-06-10", 3, 1, Set.of(3)
    );

    @Test
    public void withersChangeOnlyTheirOwnField() {
        assertEquals(new QuestStreakState(BASE.playerId(), 9, 6, "2026-06-18", "2026-06-19", 2, "2026-06-10", 3, 1, Set.of(3)), BASE.withCurrentStreak(9));
        assertEquals(new QuestStreakState(BASE.playerId(), 4, 9, "2026-06-18", "2026-06-19", 2, "2026-06-10", 3, 1, Set.of(3)), BASE.withHighestStreak(9));
        assertEquals(new QuestStreakState(BASE.playerId(), 4, 6, "x", "2026-06-19", 2, "2026-06-10", 3, 1, Set.of(3)), BASE.withLastCompletedResetKey("x"));
        assertEquals(new QuestStreakState(BASE.playerId(), 4, 6, "2026-06-18", "x", 2, "2026-06-10", 3, 1, Set.of(3)), BASE.withLastEvaluatedResetKey("x"));
        assertEquals(new QuestStreakState(BASE.playerId(), 4, 6, "2026-06-18", "2026-06-19", 0, "", 3, 1, Set.of(3)), BASE.withLostStreak(0, ""));
        assertEquals(new QuestStreakState(BASE.playerId(), 4, 6, "2026-06-18", "2026-06-19", 2, "2026-06-10", 8, 1, Set.of(3)), BASE.withShieldBalance(8));
        assertEquals(new QuestStreakState(BASE.playerId(), 4, 6, "2026-06-18", "2026-06-19", 2, "2026-06-10", 3, 8, Set.of(3)), BASE.withRecoveryBalance(8));
        assertEquals(new QuestStreakState(BASE.playerId(), 4, 6, "2026-06-18", "2026-06-19", 2, "2026-06-10", 3, 1, Set.of(7)), BASE.withClaimedMilestones(Set.of(7)));
    }

    @Test
    public void withStreakRaisesHighestStreakOnlyWhenExceeded() {
        QuestStreakState lower = BASE.withStreak(5);
        QuestStreakState higher = BASE.withStreak(7);

        assertEquals(5, lower.currentStreak());
        assertEquals(6, lower.highestStreak());
        assertEquals(7, higher.currentStreak());
        assertEquals(7, higher.highestStreak());
    }
}
