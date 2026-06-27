package gg.moonrise.quests.core.service;

import gg.moonrise.quests.model.QuestStreakState;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestStreakServiceTest {

    @Test
    void missedWindowConsumesShieldBalanceAndPreservesStreak() throws Exception {
        QuestStreakService service = service();
        QuestStreakState state = state(5, 2);

        QuestStreakState updated = service.applyMissedWindow(state, "2026-06-20");

        assertEquals(5, updated.currentStreak());
        assertEquals(1, updated.shieldBalance());
        assertEquals(0, updated.lastLostStreak());
        assertEquals("", updated.lostResetKey());
    }

    @Test
    void missedWindowWithoutShieldBreaksStreak() throws Exception {
        QuestStreakService service = service();
        QuestStreakState state = state(5, 0);

        QuestStreakState updated = service.applyMissedWindow(state, "2026-06-20");

        assertEquals(0, updated.currentStreak());
        assertEquals(0, updated.shieldBalance());
        assertEquals(5, updated.lastLostStreak());
        assertEquals("2026-06-20", updated.lostResetKey());
    }

    @Test
    void multipleMissedWindowsConsumeOneShieldEach() throws Exception {
        QuestStreakService service = service();
        QuestStreakState state = state(5, 2);

        QuestStreakState first = service.applyMissedWindow(state, "2026-06-20");
        QuestStreakState second = service.applyMissedWindow(first, "2026-06-21");
        QuestStreakState third = service.applyMissedWindow(second, "2026-06-22");

        assertEquals(5, first.currentStreak());
        assertEquals(1, first.shieldBalance());
        assertEquals(5, second.currentStreak());
        assertEquals(0, second.shieldBalance());
        assertEquals(0, third.currentStreak());
        assertEquals(5, third.lastLostStreak());
        assertEquals("2026-06-22", third.lostResetKey());
    }

    @Test
    void adminShieldAdjustmentStillUpdatesShieldBalance() {
        SqliteProvider sqliteProvider = mock(SqliteProvider.class);
        QuestStreakService service = new QuestStreakService(mock(ConfigProvider.class), sqliteProvider);
        QuestStreakState state = state(3, 2);
        when(sqliteProvider.supplyAsync(any())).thenReturn(CompletableFuture.completedFuture(state));
        when(sqliteProvider.runAsync(any())).thenReturn(CompletableFuture.completedFuture(null));

        int updatedBalance = service.adjustCurrency(state.playerId(), QuestStreakService.StreakCurrency.SHIELD, 3).join();

        assertEquals(5, updatedBalance);
    }

    private static QuestStreakService service() {
        return new QuestStreakService(mock(ConfigProvider.class), mock(SqliteProvider.class));
    }

    private static QuestStreakState state(int currentStreak, int shieldBalance) {
        return new QuestStreakState(
                UUID.randomUUID(),
                currentStreak,
                Math.max(currentStreak, 5),
                "2026-06-19",
                "2026-06-19",
                0,
                "",
                shieldBalance,
                0,
                Set.of()
        );
    }

}
