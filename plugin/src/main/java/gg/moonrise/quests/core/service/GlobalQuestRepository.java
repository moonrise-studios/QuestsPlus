package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.model.GlobalQuestContribution;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.util.QuestRowCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@SpringComponent
@RequiredArgsConstructor
public class GlobalQuestRepository {

    /**
     * Ordering key of all global quest SQL work. Operations address a global quest by period key, by
     * instance id or not at all, so one shared key is what keeps every write in submission order.
     */
    private static final String SQL_KEY = "global-quests";

    private final SqlProvider sqlProvider;

    public CompletableFuture<Optional<GlobalQuestState>> loadActive(String periodKey) {
        return sqlProvider.supplyAsync(SQL_KEY, () -> {
            try (Connection connection = sqlProvider.getConnection()) {
                return loadActive(connection, periodKey);
            }
        });
    }

    public CompletableFuture<List<GlobalQuestState>> loadExpiredUnrewarded(LocalDateTime now) {
        return sqlProvider.supplyAsync(SQL_KEY, () -> {
            List<GlobalQuestState> states = new ArrayList<>();
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT instance_id, period_key, starts_at, ends_at, definition_id, type, difficulty_id, difficulty_display_name,
                                display_name, description, variables, goal_amount, progress, completed, rewards_executed
                         FROM global_quests
                         WHERE ends_at <= ? AND rewards_executed = 0
                         ORDER BY ends_at ASC
                         """)) {
                statement.setString(1, now.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        UUID instanceId = UUID.fromString(resultSet.getString("instance_id"));
                        states.add(readState(resultSet, loadContributions(connection, instanceId)));
                    }
                }
            }
            return states;
        });
    }

    public CompletableFuture<GlobalQuestState> save(GlobalQuestState state) {
        return sqlProvider.supplyInTransaction(SQL_KEY, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sqlProvider.currentDatabase().upsertGlobalQuestSql())) {
                bindState(statement, state);
                statement.executeUpdate();
            }
            // The row was just written in this transaction, so it can only be missing if the database lost it.
            return loadActive(connection, state.periodKey())
                    .orElseThrow(() -> new SQLException("Global quest for period " + state.periodKey() + " was not found after saving it"));
        });
    }

    /**
     * Deletes the period's quest together with its contributions and reward executions in one
     * transaction, so a failure never leaves orphaned rows behind.
     */
    public CompletableFuture<Void> deletePeriod(String periodKey) {
        return sqlProvider.runInTransaction(SQL_KEY, connection -> {
            UUID instanceId;
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT instance_id
                    FROM global_quests
                    WHERE period_key = ?
                    """)) {
                select.setString(1, periodKey);
                try (ResultSet resultSet = select.executeQuery()) {
                    if (!resultSet.next()) {
                        return;
                    }
                    instanceId = UUID.fromString(resultSet.getString("instance_id"));
                }
            }
            try (PreparedStatement contributions = connection.prepareStatement("""
                     DELETE FROM global_quest_contributions
                     WHERE instance_id = ?
                     """);
                 PreparedStatement rewards = connection.prepareStatement("""
                     DELETE FROM global_quest_reward_executions
                     WHERE instance_id = ?
                     """);
                 PreparedStatement quest = connection.prepareStatement("""
                     DELETE FROM global_quests
                     WHERE period_key = ?
                     """)) {
                contributions.setString(1, instanceId.toString());
                contributions.executeUpdate();
                rewards.setString(1, instanceId.toString());
                rewards.executeUpdate();
                quest.setString(1, periodKey);
                quest.executeUpdate();
            }
        });
    }

    /**
     * Adds {@code credited} to the stored progress (capped at the goal) and to the player's
     * contribution. The delta is applied in SQL instead of writing the caller's absolute progress,
     * so servers sharing one database never overwrite each other's progress.
     */
    public CompletableFuture<Void> updateProgressAndContribution(GlobalQuestState state, UUID playerId, int credited) {
        return sqlProvider.runInTransaction(SQL_KEY, connection -> {
            // completed is assigned before progress because MySQL evaluates SET assignments left to
            // right against already-updated columns; this order reads the old progress everywhere.
            try (PreparedStatement quest = connection.prepareStatement("""
                    UPDATE global_quests
                    SET completed = CASE WHEN completed = 1 OR progress + ? >= goal_amount THEN 1 ELSE 0 END,
                        progress = CASE WHEN progress + ? > goal_amount THEN goal_amount ELSE progress + ? END
                    WHERE instance_id = ?
                    """)) {
                quest.setInt(1, credited);
                quest.setInt(2, credited);
                quest.setInt(3, credited);
                quest.setString(4, state.quest().instanceId().toString());
                quest.executeUpdate();
            }
            try (PreparedStatement contribution = connection.prepareStatement(sqlProvider.currentDatabase().incrementGlobalContributionSql())) {
                contribution.setString(1, state.quest().instanceId().toString());
                contribution.setString(2, playerId.toString());
                contribution.setInt(3, credited);
                contribution.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<GlobalQuestContribution>> loadRankedContributions(UUID instanceId) {
        return sqlProvider.supplyAsync(SQL_KEY, () -> {
            List<GlobalQuestContribution> contributions = new ArrayList<>();
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT player_uuid, contribution
                         FROM global_quest_contributions
                         WHERE instance_id = ? AND contribution > 0
                         ORDER BY contribution DESC, player_uuid ASC
                         """)) {
                statement.setString(1, instanceId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Map.Entry<UUID, Integer>> rows = new ArrayList<>();
                    while (resultSet.next()) {
                        rows.add(Map.entry(UUID.fromString(resultSet.getString("player_uuid")), resultSet.getInt("contribution")));
                    }
                    int participants = rows.size();
                    int rank = 1;
                    for (Map.Entry<UUID, Integer> row : rows) {
                        contributions.add(new GlobalQuestContribution(row.getKey(), row.getValue(), rank++, participants));
                    }
                }
            }
            return contributions;
        });
    }

    public CompletableFuture<Boolean> markRewardsExecuted(UUID instanceId) {
        return sqlProvider.supplyAsync(SQL_KEY, () -> {
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         UPDATE global_quests
                         SET rewards_executed = 1
                         WHERE instance_id = ? AND rewards_executed = 0
                         """)) {
                statement.setString(1, instanceId.toString());
                return statement.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<Boolean> insertRewardExecution(UUID instanceId, UUID playerId, int percentile) {
        return sqlProvider.supplyAsync(SQL_KEY, () -> {
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sqlProvider.currentDatabase().insertRewardExecutionSql())) {
                statement.setString(1, instanceId.toString());
                statement.setString(2, playerId.toString());
                statement.setInt(3, percentile);
                return statement.executeUpdate() > 0;
            }
        });
    }

    private GlobalQuestState readState(ResultSet resultSet, Map<UUID, Integer> contributions) throws SQLException {
        GeneratedQuest quest = new GeneratedQuest(
                UUID.fromString(resultSet.getString("instance_id")),
                QuestRowCodec.GLOBAL_PLAYER_ID,
                resultSet.getString("period_key"),
                resultSet.getString("definition_id"),
                QuestType.of(resultSet.getString("type")),
                QuestRowCodec.difficultyId(resultSet.getString("difficulty_id")),
                QuestRowCodec.difficultyDisplayName(resultSet.getString("difficulty_display_name")),
                resultSet.getString("display_name"),
                QuestRowCodec.stringList(resultSet.getString("description")),
                QuestRowCodec.stringMap(resultSet.getString("variables")),
                -1,
                resultSet.getInt("goal_amount"),
                resultSet.getInt("progress"),
                resultSet.getInt("completed") == 1
        );
        return new GlobalQuestState(
                quest,
                resultSet.getString("period_key"),
                LocalDateTime.parse(resultSet.getString("starts_at")),
                LocalDateTime.parse(resultSet.getString("ends_at")),
                contributions,
                resultSet.getInt("rewards_executed") == 1
        );
    }

    private Optional<GlobalQuestState> loadActive(Connection connection, String periodKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT instance_id, period_key, starts_at, ends_at, definition_id, type, difficulty_id, difficulty_display_name,
                       display_name, description, variables, goal_amount, progress, completed, rewards_executed
                FROM global_quests
                WHERE period_key = ?
                """)) {
            statement.setString(1, periodKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                UUID instanceId = UUID.fromString(resultSet.getString("instance_id"));
                return Optional.of(readState(resultSet, loadContributions(connection, instanceId)));
            }
        }
    }

    private Map<UUID, Integer> loadContributions(Connection connection, UUID instanceId) throws SQLException {
        Map<UUID, Integer> contributions = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_uuid, contribution
                FROM global_quest_contributions
                WHERE instance_id = ?
                """)) {
            statement.setString(1, instanceId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    contributions.put(UUID.fromString(resultSet.getString("player_uuid")), resultSet.getInt("contribution"));
                }
            }
        }
        return contributions;
    }

    private void bindState(PreparedStatement statement, GlobalQuestState state) throws SQLException {
        GeneratedQuest quest = state.quest();
        statement.setString(1, quest.instanceId().toString());
        statement.setString(2, state.periodKey());
        statement.setString(3, state.startsAt().toString());
        statement.setString(4, state.endsAt().toString());
        statement.setString(5, quest.definitionId());
        statement.setString(6, quest.type().key());
        statement.setString(7, quest.difficultyId());
        statement.setString(8, quest.difficultyDisplayName());
        statement.setString(9, quest.displayName());
        statement.setString(10, QuestRowCodec.toJson(quest.description()));
        statement.setString(11, QuestRowCodec.toJson(quest.variables()));
        statement.setInt(12, quest.goalAmount());
        statement.setInt(13, quest.progress());
        statement.setInt(14, quest.completed() ? 1 : 0);
        statement.setInt(15, state.rewardsExecuted() ? 1 : 0);
    }
}
