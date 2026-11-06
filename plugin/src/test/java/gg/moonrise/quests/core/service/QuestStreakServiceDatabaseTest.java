package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.model.QuestStreakState;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestStreakServiceDatabaseTest {

    @TempDir
    private Path tempDir;

    private SqlProvider sqlProvider;
    private QuestStreakService service;
    private UUID playerId;
    private Player player;

    @BeforeEach
    public void setUp() {
        sqlProvider = SqliteTestHarness.open(tempDir);
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(new Config());
        service = new QuestStreakService(configProvider, sqlProvider);
        playerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
    }

    @AfterEach
    public void tearDown() {
        if (sqlProvider != null) {
            sqlProvider.onDisable();
        }
    }

    @Test
    public void concurrentAdjustCurrencyAndEnsureStateKeepBothEffects() throws SQLException {
        insertState(new QuestStreakState(playerId, 5, 5, "2026-06-18", "2026-06-18", 0, "", 0, 0, Set.of()));

        CompletableFuture<QuestStreakState> ensured = service.ensureState(player, "2026-06-21");
        CompletableFuture<Integer> adjusted = service.adjustCurrency(playerId, QuestStreakService.StreakCurrency.SHIELD, 3);
        CompletableFuture.allOf(ensured, adjusted).join();

        QuestStreakState stored = storedState();
        assertEquals(0, stored.currentStreak(), "missed windows must still break the streak");
        assertEquals(5, stored.lastLostStreak());
        assertEquals("2026-06-19", stored.lostResetKey());
        assertEquals("2026-06-20", stored.lastEvaluatedResetKey());
        assertEquals(3, stored.shieldBalance(), "admin shield grant must not be overwritten");
        assertEquals(3, adjusted.join());
    }

    @Test
    public void concurrentCurrencyAdjustmentsFromManyThreadsAreAllApplied() throws Exception {
        int adjustments = 25;
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch startGate = new CountDownLatch(1);
            List<CompletableFuture<Integer>> results = new ArrayList<>();
            for (int i = 0; i < adjustments; i++) {
                CompletableFuture<Integer> result = new CompletableFuture<>();
                results.add(result);
                callers.execute(() -> {
                    try {
                        startGate.await();
                        service.adjustCurrency(playerId, QuestStreakService.StreakCurrency.RECOVERY, 1)
                                .whenComplete((balance, throwable) -> {
                                    if (throwable != null) {
                                        result.completeExceptionally(throwable);
                                    } else {
                                        result.complete(balance);
                                    }
                                });
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        result.completeExceptionally(exception);
                    }
                });
            }
            startGate.countDown();
            CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        } finally {
            callers.shutdownNow();
        }

        assertEquals(adjustments, storedState().recoveryBalance());
    }

    private void insertState(QuestStreakState state) throws SQLException {
        try (Connection connection = sqlProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlProvider.upsertStreakStateSql())) {
            statement.setString(1, state.playerId().toString());
            statement.setInt(2, state.currentStreak());
            statement.setInt(3, state.highestStreak());
            statement.setString(4, state.lastCompletedResetKey());
            statement.setString(5, state.lastEvaluatedResetKey());
            statement.setInt(6, state.lastLostStreak());
            statement.setString(7, state.lostResetKey());
            statement.setInt(8, state.shieldBalance());
            statement.setInt(9, state.recoveryBalance());
            statement.executeUpdate();
        }
    }

    private QuestStreakState storedState() throws SQLException {
        try (Connection connection = sqlProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT current_streak, highest_streak, last_completed_reset_key, last_evaluated_reset_key,
                            last_lost_streak, lost_reset_key, shield_balance, recovery_balance
                     FROM player_quest_streaks
                     WHERE player_uuid = ?
                     """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "streak row should exist");
                return new QuestStreakState(
                        playerId,
                        resultSet.getInt("current_streak"),
                        resultSet.getInt("highest_streak"),
                        resultSet.getString("last_completed_reset_key"),
                        resultSet.getString("last_evaluated_reset_key"),
                        resultSet.getInt("last_lost_streak"),
                        resultSet.getString("lost_reset_key"),
                        resultSet.getInt("shield_balance"),
                        resultSet.getInt("recovery_balance"),
                        Set.of()
                );
            }
        }
    }
}
