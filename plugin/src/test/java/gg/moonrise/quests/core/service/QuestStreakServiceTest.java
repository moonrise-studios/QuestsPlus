package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.model.QuestStreakState;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestStreakServiceTest {

    @Test
    public void missedWindowConsumesShieldBalanceAndPreservesStreak() throws Exception {
        QuestStreakService service = service();
        QuestStreakState state = state(5, 2);

        QuestStreakState updated = service.applyMissedWindow(state, "2026-06-20");

        assertEquals(5, updated.currentStreak());
        assertEquals(1, updated.shieldBalance());
        assertEquals(0, updated.lastLostStreak());
        assertEquals("", updated.lostResetKey());
    }

    @Test
    public void missedWindowWithoutShieldBreaksStreak() throws Exception {
        QuestStreakService service = service();
        QuestStreakState state = state(5, 0);

        QuestStreakState updated = service.applyMissedWindow(state, "2026-06-20");

        assertEquals(0, updated.currentStreak());
        assertEquals(0, updated.shieldBalance());
        assertEquals(5, updated.lastLostStreak());
        assertEquals("2026-06-20", updated.lostResetKey());
    }

    @Test
    public void multipleMissedWindowsConsumeOneShieldEach() throws Exception {
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
    public void adminShieldAdjustmentStillUpdatesShieldBalance() {
        SqlProvider sqlProvider = mock(SqlProvider.class);
        QuestStreakService service = new QuestStreakService(mock(ConfigProvider.class), sqlProvider);
        QuestStreakState state = state(3, 2);
        when(sqlProvider.supplyAsync(any())).thenReturn(CompletableFuture.completedFuture(state));
        when(sqlProvider.runAsync(any())).thenReturn(CompletableFuture.completedFuture(null));

        int updatedBalance = service.adjustCurrency(state.playerId(), QuestStreakService.StreakCurrency.SHIELD, 3).join();

        assertEquals(5, updatedBalance);
    }

    @Test
    public void failedOperationDoesNotBlockLaterOperationsForThePlayer() {
        SqlProvider sqlProvider = mock(SqlProvider.class);
        QuestStreakService service = new QuestStreakService(mock(ConfigProvider.class), sqlProvider);
        QuestStreakState state = state(3, 2);
        when(sqlProvider.supplyAsync(any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")))
                .thenReturn(CompletableFuture.completedFuture(state));
        when(sqlProvider.runAsync(any())).thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<Integer> failed = service.adjustCurrency(state.playerId(), QuestStreakService.StreakCurrency.SHIELD, 1);
        CompletableFuture<Integer> next = service.adjustCurrency(state.playerId(), QuestStreakService.StreakCurrency.SHIELD, 1);

        assertTrue(failed.isCompletedExceptionally());
        assertEquals(3, next.join());
    }

    @Test
    public void ensureStateServesCachedStateAlreadyEvaluatedForCurrentWindow() {
        SqlProvider sqlProvider = mock(SqlProvider.class);
        QuestStreakService service = new QuestStreakService(enabledConfig(), sqlProvider);
        QuestStreakState state = state(3, 2);
        Player player = player(state.playerId());
        when(sqlProvider.supplyAsync(any())).thenReturn(CompletableFuture.completedFuture(state));

        QuestStreakState first = service.ensureState(player, "2026-06-20").join();
        QuestStreakState second = service.ensureState(player, "2026-06-20").join();

        assertEquals(state, first);
        assertEquals(state, second);
        verify(sqlProvider, times(1)).supplyAsync(any());
        verify(sqlProvider, never()).runAsync(any());
    }

    @Test
    public void ensureStateReloadsAndSavesWhenANewWindowWasMissed() {
        SqlProvider sqlProvider = mock(SqlProvider.class);
        QuestStreakService service = new QuestStreakService(enabledConfig(), sqlProvider);
        QuestStreakState state = state(3, 2);
        Player player = player(state.playerId());
        when(sqlProvider.supplyAsync(any())).thenReturn(CompletableFuture.completedFuture(state));
        when(sqlProvider.runAsync(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.ensureState(player, "2026-06-20").join();
        QuestStreakState updated = service.ensureState(player, "2026-06-22").join();

        assertEquals(3, updated.currentStreak());
        assertEquals(0, updated.shieldBalance());
        assertEquals("2026-06-21", updated.lastEvaluatedResetKey());
        verify(sqlProvider, times(2)).supplyAsync(any());
        verify(sqlProvider, times(1)).runAsync(any());
    }

    private static QuestStreakService service() {
        return new QuestStreakService(mock(ConfigProvider.class), mock(SqlProvider.class));
    }

    private static ConfigProvider enabledConfig() {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(new Config());
        return configProvider;
    }

    private static Player player(UUID playerId) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        return player;
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
