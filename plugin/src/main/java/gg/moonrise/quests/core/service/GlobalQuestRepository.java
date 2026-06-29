package gg.moonrise.quests.core.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.model.GlobalQuestContribution;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.sdk.model.QuestType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@SpringComponent
@RequiredArgsConstructor
public class GlobalQuestRepository {

    private static final UUID GLOBAL_PLAYER_ID = new UUID(0L, 0L);

    private final SqlProvider sqlProvider;
    private final Gson gson = new Gson();

    public CompletableFuture<GlobalQuestState> loadActive(String periodKey) {
        return sqlProvider.supplyAsync(() -> {
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT instance_id, period_key, starts_at, ends_at, definition_id, type, difficulty_id, difficulty_display_name,
                                display_name, description, variables, goal_amount, progress, completed, rewards_executed
                         FROM global_quests
                         WHERE period_key = ?
                         """)) {
                statement.setString(1, periodKey);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return null;
                    }
                    UUID instanceId = UUID.fromString(resultSet.getString("instance_id"));
                    return readState(resultSet, loadContributions(connection, instanceId));
                }
            }
        });
    }

    public CompletableFuture<List<GlobalQuestState>> loadExpiredUnrewarded(LocalDateTime now) {
        return sqlProvider.supplyAsync(() -> {
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

    public CompletableFuture<Void> save(GlobalQuestState state) {
        return sqlProvider.runAsync(() -> {
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sqlProvider.upsertGlobalQuestSql())) {
                bindState(statement, state);
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> deletePeriod(String periodKey) {
        return sqlProvider.runAsync(() -> {
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement select = connection.prepareStatement("""
                         SELECT instance_id
                         FROM global_quests
                         WHERE period_key = ?
                         """)) {
                select.setString(1, periodKey);
                UUID instanceId = null;
                try (ResultSet resultSet = select.executeQuery()) {
                    if (resultSet.next()) {
                        instanceId = UUID.fromString(resultSet.getString("instance_id"));
                    }
                }
                if (instanceId == null) {
                    return;
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
            }
        });
    }

    public CompletableFuture<Void> updateProgressAndContribution(GlobalQuestState state, UUID playerId, int credited) {
        return sqlProvider.runAsync(() -> {
            try (Connection connection = sqlProvider.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement quest = connection.prepareStatement("""
                            UPDATE global_quests
                            SET progress = ?, completed = ?
                            WHERE instance_id = ?
                            """)) {
                        quest.setInt(1, state.quest().progress());
                        quest.setInt(2, state.quest().completed() ? 1 : 0);
                        quest.setString(3, state.quest().instanceId().toString());
                        quest.executeUpdate();
                    }
                    try (PreparedStatement contribution = connection.prepareStatement(sqlProvider.incrementGlobalContributionSql())) {
                        contribution.setString(1, state.quest().instanceId().toString());
                        contribution.setString(2, playerId.toString());
                        contribution.setInt(3, credited);
                        contribution.executeUpdate();
                    }
                    connection.commit();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
            }
        });
    }

    public CompletableFuture<List<GlobalQuestContribution>> loadRankedContributions(UUID instanceId) {
        return sqlProvider.supplyAsync(() -> {
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
        return sqlProvider.supplyAsync(() -> {
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
        return sqlProvider.supplyAsync(() -> {
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sqlProvider.insertRewardExecutionSql())) {
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
                GLOBAL_PLAYER_ID,
                resultSet.getString("period_key"),
                resultSet.getString("definition_id"),
                QuestType.of(resultSet.getString("type")),
                fallback(resultSet.getString("difficulty_id"), "easy"),
                fallback(resultSet.getString("difficulty_display_name"), "<green><b>EASY"),
                resultSet.getString("display_name"),
                stringList(resultSet.getString("description")),
                stringMap(resultSet.getString("variables")),
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
        statement.setString(10, gson.toJson(quest.description()));
        statement.setString(11, gson.toJson(quest.variables()));
        statement.setInt(12, quest.goalAmount());
        statement.setInt(13, quest.progress());
        statement.setInt(14, quest.completed() ? 1 : 0);
        statement.setInt(15, state.rewardsExecuted() ? 1 : 0);
    }

    private List<String> stringList(String json) {
        String[] values = gson.fromJson(json, String[].class);
        return values == null ? List.of() : List.of(values);
    }

    private Map<String, String> stringMap(String json) {
        JsonObject object = gson.fromJson(json, JsonObject.class);
        if (object == null) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            values.put(entry.getKey(), value == null || value.isJsonNull() ? null : value.getAsString());
        }
        return values;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
