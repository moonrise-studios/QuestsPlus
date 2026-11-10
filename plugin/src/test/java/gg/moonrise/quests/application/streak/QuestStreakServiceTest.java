package gg.moonrise.quests.application.streak;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.paper.scheduler.impl.SyncScheduler;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.StreakMenu;
import gg.moonrise.quests.config.StreakMilestoneConfig;
import gg.moonrise.quests.config.Streaks;
import gg.moonrise.quests.domain.model.QuestStreakState;
import gg.moonrise.quests.infrastructure.persistence.QuestStreakRepository;
import gg.moonrise.quests.core.service.ConfigProvider;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
        QuestStreakRepository repository = mock(QuestStreakRepository.class);
        QuestStreakService service = new QuestStreakService(mock(ConfigProvider.class), repository);
        QuestStreakState state = state(3, 2);
        when(repository.load(any())).thenReturn(CompletableFuture.completedFuture(state));
        when(repository.save(any())).thenReturn(CompletableFuture.completedFuture(null));

        int updatedBalance = service.adjustCurrency(state.playerId(), QuestStreakService.StreakCurrency.SHIELD, 3).join();

        assertEquals(5, updatedBalance);
    }

    @Test
    public void failedOperationDoesNotBlockLaterOperationsForThePlayer() {
        QuestStreakRepository repository = mock(QuestStreakRepository.class);
        QuestStreakService service = new QuestStreakService(mock(ConfigProvider.class), repository);
        QuestStreakState state = state(3, 2);
        when(repository.load(any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")))
                .thenReturn(CompletableFuture.completedFuture(state));
        when(repository.save(any())).thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<Integer> failed = service.adjustCurrency(state.playerId(), QuestStreakService.StreakCurrency.SHIELD, 1);
        CompletableFuture<Integer> next = service.adjustCurrency(state.playerId(), QuestStreakService.StreakCurrency.SHIELD, 1);

        assertTrue(failed.isCompletedExceptionally());
        assertEquals(3, next.join());
    }

    @Test
    public void ensureStateServesCachedStateAlreadyEvaluatedForCurrentWindow() {
        QuestStreakRepository repository = mock(QuestStreakRepository.class);
        QuestStreakService service = new QuestStreakService(enabledConfig(), repository);
        QuestStreakState state = state(3, 2);
        Player player = player(state.playerId());
        when(repository.load(any())).thenReturn(CompletableFuture.completedFuture(state));

        QuestStreakState first = service.ensureState(player, "2026-06-20").join();
        QuestStreakState second = service.ensureState(player, "2026-06-20").join();

        assertEquals(state, first);
        assertEquals(state, second);
        verify(repository, times(1)).load(state.playerId());
        verify(repository, never()).save(any());
    }

    @Test
    public void ensureStateReloadsAndSavesWhenANewWindowWasMissed() {
        QuestStreakRepository repository = mock(QuestStreakRepository.class);
        QuestStreakService service = new QuestStreakService(enabledConfig(), repository);
        QuestStreakState state = state(3, 2);
        Player player = player(state.playerId());
        when(repository.load(any())).thenReturn(CompletableFuture.completedFuture(state));
        when(repository.save(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.ensureState(player, "2026-06-20").join();
        QuestStreakState updated = service.ensureState(player, "2026-06-22").join();

        assertEquals(3, updated.currentStreak());
        assertEquals(0, updated.shieldBalance());
        assertEquals("2026-06-21", updated.lastEvaluatedResetKey());
        verify(repository, times(2)).load(state.playerId());
        verify(repository, times(1)).save(any());
    }

    @Test
    public void claimedStreakMilestoneCommandsRunOnGlobalSchedulerWhenPlayerIsOffline() {
        QuestStreakRepository repository = mock(QuestStreakRepository.class);
        QuestStreakService service = new QuestStreakService(configWithMilestone(new StreakMilestoneConfig(3, "3 Day Streak", List.of("eco give <player> 500", "notify <uuid> <milestone_streak>"))), repository);
        QuestStreakState lost = new QuestStreakState(UUID.randomUUID(), 0, 5, "2026-06-18", "2026-06-19", 3, "2026-06-19", 0, 1, Set.of());
        Player player = player(lost.playerId());
        when(player.getName()).thenReturn("Steve");
        when(player.isOnline()).thenReturn(false);
        when(repository.load(any())).thenReturn(CompletableFuture.completedFuture(lost));
        when(repository.save(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(repository.claimMilestones(lost.playerId(), List.of(3))).thenReturn(CompletableFuture.completedFuture(Set.of(3)));
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
        return new QuestStreakService(mock(ConfigProvider.class), mock(QuestStreakRepository.class));
    }

    private static ConfigProvider enabledConfig() {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(new Config());
        return configProvider;
    }

    private static ConfigProvider configWithMilestone(StreakMilestoneConfig milestone) {
        Config config = spy(new Config());
        when(config.getStreaks()).thenReturn(new Streaks(true, -1, 3, List.of(milestone), new StreakMenu()));
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);
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
