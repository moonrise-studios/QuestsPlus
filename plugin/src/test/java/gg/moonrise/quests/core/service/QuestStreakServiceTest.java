package gg.moonrise.quests.core.service;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.paper.scheduler.impl.SyncScheduler;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.model.QuestStreakEvaluation;
import gg.moonrise.quests.model.QuestStreakMilestone;
import gg.moonrise.quests.model.QuestStreakState;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
        when(sqlProvider.supplyAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(state));
        when(sqlProvider.runAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        int updatedBalance = service.adjustCurrency(state.playerId(), QuestStreakService.StreakCurrency.SHIELD, 3).join();

        assertEquals(5, updatedBalance);
    }

    @Test
    public void failedOperationDoesNotBlockLaterOperationsForThePlayer() {
        SqlProvider sqlProvider = mock(SqlProvider.class);
        QuestStreakService service = new QuestStreakService(mock(ConfigProvider.class), sqlProvider);
        QuestStreakState state = state(3, 2);
        when(sqlProvider.supplyAsync(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")))
                .thenReturn(CompletableFuture.completedFuture(state));
        when(sqlProvider.runAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

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
        when(sqlProvider.supplyAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(state));

        QuestStreakState first = service.ensureState(player, "2026-06-20").join();
        QuestStreakState second = service.ensureState(player, "2026-06-20").join();

        assertEquals(state, first);
        assertEquals(state, second);
        verify(sqlProvider, times(1)).supplyAsync(any(), any());
        verify(sqlProvider, never()).runAsync(any(), any());
    }

    @Test
    public void ensureStateReloadsAndSavesWhenANewWindowWasMissed() {
        SqlProvider sqlProvider = mock(SqlProvider.class);
        QuestStreakService service = new QuestStreakService(enabledConfig(), sqlProvider);
        QuestStreakState state = state(3, 2);
        Player player = player(state.playerId());
        when(sqlProvider.supplyAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(state));
        when(sqlProvider.runAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        service.ensureState(player, "2026-06-20").join();
        QuestStreakState updated = service.ensureState(player, "2026-06-22").join();

        assertEquals(3, updated.currentStreak());
        assertEquals(0, updated.shieldBalance());
        assertEquals("2026-06-21", updated.lastEvaluatedResetKey());
        verify(sqlProvider, times(2)).supplyAsync(any(), any());
        verify(sqlProvider, times(1)).runAsync(any(), any());
    }

    @Test
    public void claimedStreakMilestoneCommandsRunOnGlobalSchedulerWhenPlayerIsOffline() {
        SqlProvider sqlProvider = mock(SqlProvider.class);
        QuestStreakService service = new QuestStreakService(enabledConfig(), sqlProvider);
        QuestStreakState lost = new QuestStreakState(UUID.randomUUID(), 0, 5, "2026-06-18", "2026-06-19", 3, "2026-06-19", 0, 1, Set.of());
        Player player = player(lost.playerId());
        when(player.getName()).thenReturn("Steve");
        when(player.isOnline()).thenReturn(false);
        QuestStreakMilestone milestone = new QuestStreakMilestone(3, "3 Day Streak", List.of(), List.of("eco give <player> 500", "notify <uuid> <milestone_streak>"));
        when(sqlProvider.supplyAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(lost));
        when(sqlProvider.runAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        doAnswer(invocation -> CompletableFuture.completedFuture(new QuestStreakEvaluation(lost.withStreak(3), List.of(milestone))))
                .when(sqlProvider).supplyInTransaction(any(), any());
        SyncScheduler syncScheduler = mock(SyncScheduler.class);
        when(syncScheduler.run(any())).thenAnswer(invocation -> {
            Consumer<ScheduledTask> task = invocation.getArgument(0);
            task.accept(mock(ScheduledTask.class));
            return null;
        });
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        try (MockedStatic<Scheduler> scheduler = mockStatic(Scheduler.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            scheduler.when(Scheduler::sync).thenReturn(syncScheduler);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);

            QuestStreakService.StreakRecoveryResult result = service.recoverStreak(player, "2026-06-20").join();

            assertTrue(result.applied());
            bukkit.verify(() -> Bukkit.dispatchCommand(console, "eco give Steve 500"));
            bukkit.verify(() -> Bukkit.dispatchCommand(console, "notify " + lost.playerId() + " 3"));
            scheduler.verify(() -> Scheduler.entity(any()), never());
        }
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
