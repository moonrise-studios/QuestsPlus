package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.model.QuestStreakState;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Owns the streak SQL. Every operation is keyed by the player's UUID, so work for one player runs in
 * submission order on the SQL worker pool.
 */
@SpringComponent
@RequiredArgsConstructor
public class QuestStreakRepository {

    private final SqlProvider sqlProvider;

    /**
     * Loads the stored streak state with its claimed milestones, or an empty state when the player has none.
     */
    public CompletableFuture<QuestStreakState> load(UUID playerId) {
        return sqlProvider.supplyAsync(playerId, () -> {
            QuestStreakState state = readState(playerId);
            return state.withClaimedMilestones(readClaimedStreakMilestones(playerId));
        });
    }

    public CompletableFuture<Void> save(QuestStreakState state) {
        return sqlProvider.runAsync(state.playerId(), () -> {
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sqlProvider.currentDatabase().upsertStreakStateSql())) {
                bindState(statement, state);
                statement.executeUpdate();
            }
        });
    }

    /**
     * Records each streak milestone as claimed in one transaction and returns, in the given order, the streaks this
     * call claimed. A streak that was already claimed is left out, so its rewards are never handed out twice.
     */
    public CompletableFuture<Set<Integer>> claimMilestones(UUID playerId, Collection<Integer> streaks) {
        return sqlProvider.supplyInTransaction(playerId, connection -> {
            Set<Integer> claimed = new LinkedHashSet<>();
            for (int streak : streaks) {
                if (insertStreakMilestone(connection, playerId, streak)) {
                    claimed.add(streak);
                }
            }
            return claimed;
        });
    }

    private QuestStreakState readState(UUID playerId) throws SQLException {
        try (Connection connection = sqlProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT current_streak, highest_streak, last_completed_reset_key, last_evaluated_reset_key,
                            last_lost_streak, lost_reset_key, shield_balance, recovery_balance
                     FROM player_quest_streaks
                     WHERE player_uuid = ?
                     """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return QuestStreakState.empty(playerId);
                }
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

    private Set<Integer> readClaimedStreakMilestones(UUID playerId) throws SQLException {
        Set<Integer> claimed = new LinkedHashSet<>();
        try (Connection connection = sqlProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT streak
                     FROM player_quest_streak_milestones
                     WHERE player_uuid = ?
                     """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    claimed.add(resultSet.getInt("streak"));
                }
            }
        }
        return claimed;
    }

    private boolean insertStreakMilestone(Connection connection, UUID playerId, int streak) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sqlProvider.currentDatabase().insertStreakMilestoneSql())) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, streak);
            return statement.executeUpdate() > 0;
        }
    }

    private void bindState(PreparedStatement statement, QuestStreakState state) throws SQLException {
        statement.setString(1, state.playerId().toString());
        statement.setInt(2, state.currentStreak());
        statement.setInt(3, state.highestStreak());
        statement.setString(4, safe(state.lastCompletedResetKey()));
        statement.setString(5, safe(state.lastEvaluatedResetKey()));
        statement.setInt(6, state.lastLostStreak());
        statement.setString(7, safe(state.lostResetKey()));
        statement.setInt(8, state.shieldBalance());
        statement.setInt(9, state.recoveryBalance());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
