package gg.moonrise.quests.core.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestCompletionStats;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.model.QuestMilestoneClaim;
import gg.moonrise.quests.model.QuestMilestone;
import gg.moonrise.quests.sdk.model.QuestType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@SpringComponent
@RequiredArgsConstructor
public class QuestRepository {

    private final SqliteProvider sqliteProvider;
    private final Gson gson = new Gson();

    public CompletableFuture<List<GeneratedQuest>> loadQuests(UUID playerId, String resetKey) {
        return sqliteProvider.supplyAsync(() -> {
            List<GeneratedQuest> quests = new ArrayList<>();
            try (Connection connection = sqliteProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT instance_id, definition_id, type, difficulty_id, difficulty_display_name, display_name, description, variables, slot_index, premium, goal_amount, progress, completed
                         FROM player_quests
                         WHERE player_uuid = ? AND reset_key = ?
                         ORDER BY CASE WHEN slot_index IS NULL THEN 2147483647 ELSE slot_index END ASC, rowid ASC
                         """)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, resetKey);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        quests.add(readQuest(playerId, resetKey, resultSet));
                    }
                }
            }
            return quests;
        });
    }

    public CompletableFuture<Void> saveGeneratedQuests(List<GeneratedQuest> quests) {
        if (quests.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return sqliteProvider.runAsync(() -> {
            try (Connection connection = sqliteProvider.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO player_quests (
                            instance_id, player_uuid, reset_key, definition_id, type, difficulty_id, difficulty_display_name, display_name,
                            description, variables, slot_index, premium, goal_amount, progress, completed
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(instance_id)
                        DO UPDATE SET progress = excluded.progress, completed = excluded.completed,
                                      difficulty_id = excluded.difficulty_id,
                                      difficulty_display_name = excluded.difficulty_display_name,
                                      slot_index = excluded.slot_index,
                                      premium = excluded.premium
                        """)) {
                    for (GeneratedQuest quest : quests) {
                        bindQuest(statement, quest);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
                connection.commit();
            }
        });
    }

    public CompletableFuture<Void> updateProgress(GeneratedQuest quest) {
        return sqliteProvider.runAsync(() -> {
            try (Connection connection = sqliteProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         UPDATE player_quests
                         SET progress = ?, completed = ?
                         WHERE instance_id = ?
                         """)) {
                statement.setInt(1, quest.progress());
                statement.setInt(2, quest.completed() ? 1 : 0);
                statement.setString(3, quest.instanceId().toString());
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Integer> loadRerollsUsed(UUID playerId, String resetKey) {
        return sqliteProvider.supplyAsync(() -> {
            try (Connection connection = sqliteProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT used_count
                         FROM player_quest_rerolls
                         WHERE player_uuid = ? AND reset_key = ?
                         """)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, resetKey);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt("used_count") : 0;
                }
            }
        });
    }

    public CompletableFuture<Integer> loadQuestResetsUsed(UUID playerId, String resetKey) {
        return sqliteProvider.supplyAsync(() -> {
            try (Connection connection = sqliteProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT used_count
                         FROM player_quest_resets
                         WHERE player_uuid = ? AND reset_key = ?
                         """)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, resetKey);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt("used_count") : 0;
                }
            }
        });
    }

    public CompletableFuture<Void> resetRerolls(UUID playerId, String resetKey) {
        return sqliteProvider.runAsync(() -> {
            try (Connection connection = sqliteProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         DELETE FROM player_quest_rerolls
                         WHERE player_uuid = ? AND reset_key = ?
                         """)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, resetKey);
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Integer> recordQuestResetPurchaseAndReset(UUID playerId, String resetKey, int limit) {
        if (limit <= 0) {
            return CompletableFuture.completedFuture(-1);
        }
        return sqliteProvider.supplyAsync(() -> {
            try (Connection connection = sqliteProvider.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    int used = readQuestResetsUsed(connection, playerId, resetKey);
                    if (used >= limit) {
                        connection.rollback();
                        return -1;
                    }
                    try (PreparedStatement resetUsage = connection.prepareStatement("""
                            INSERT INTO player_quest_resets (player_uuid, reset_key, used_count)
                            VALUES (?, ?, 1)
                            ON CONFLICT(player_uuid, reset_key)
                            DO UPDATE SET used_count = used_count + 1
                            """)) {
                        resetUsage.setString(1, playerId.toString());
                        resetUsage.setString(2, resetKey);
                        resetUsage.executeUpdate();
                    }
                    try (PreparedStatement deleteQuests = connection.prepareStatement("""
                            DELETE FROM player_quests
                            WHERE player_uuid = ? AND reset_key = ?
                            """)) {
                        deleteQuests.setString(1, playerId.toString());
                        deleteQuests.setString(2, resetKey);
                        deleteQuests.executeUpdate();
                    }
                    int updated = readQuestResetsUsed(connection, playerId, resetKey);
                    connection.commit();
                    return updated;
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
            }
        });
    }

    public CompletableFuture<Integer> replaceQuestAndIncrementRerolls(UUID oldInstanceId, GeneratedQuest replacement) {
        return sqliteProvider.supplyAsync(() -> {
            try (Connection connection = sqliteProvider.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement delete = connection.prepareStatement("""
                            DELETE FROM player_quests
                            WHERE instance_id = ?
                            """)) {
                        delete.setString(1, oldInstanceId.toString());
                        delete.executeUpdate();
                    }
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO player_quests (
                                instance_id, player_uuid, reset_key, definition_id, type, difficulty_id, difficulty_display_name, display_name,
                                description, variables, slot_index, premium, goal_amount, progress, completed
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """)) {
                        bindQuest(insert, replacement);
                        insert.executeUpdate();
                    }
                    try (PreparedStatement reroll = connection.prepareStatement("""
                            INSERT INTO player_quest_rerolls (player_uuid, reset_key, used_count)
                            VALUES (?, ?, 1)
                            ON CONFLICT(player_uuid, reset_key)
                            DO UPDATE SET used_count = used_count + 1
                            """)) {
                        reroll.setString(1, replacement.playerId().toString());
                        reroll.setString(2, replacement.resetKey());
                        reroll.executeUpdate();
                    }
                    int used = readRerollsUsed(connection, replacement.playerId(), replacement.resetKey());
                    connection.commit();
                    return used;
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
            }
        });
    }

    public CompletableFuture<Map<String, Integer>> loadDifficultyCompletedCounts(UUID playerId) {
        return sqliteProvider.supplyAsync(() -> {
            Map<String, Integer> counts = new LinkedHashMap<>();
            try (Connection connection = sqliteProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT difficulty_id, quests_completed
                         FROM player_quest_difficulty_stats
                         WHERE player_uuid = ?
                         """)) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        counts.put(resultSet.getString("difficulty_id"), resultSet.getInt("quests_completed"));
                    }
                }
            }
            return counts;
        });
    }

    public CompletableFuture<Set<String>> loadExecutedMilestones(UUID playerId) {
        return sqliteProvider.supplyAsync(() -> {
            Set<String> milestones = new LinkedHashSet<>();
            try (Connection connection = sqliteProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT difficulty_id, completed
                         FROM player_quest_milestones
                         WHERE player_uuid = ?
                         """)) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        milestones.add(PlayerQuestState.milestoneKey(resultSet.getString("difficulty_id"), resultSet.getInt("completed")));
                    }
                }
            }
            return milestones;
        });
    }

    public CompletableFuture<QuestCompletionStats> incrementCompletionStats(UUID playerId, String difficultyId, List<QuestMilestone> milestones) {
        return sqliteProvider.supplyAsync(() -> {
            try (Connection connection = sqliteProvider.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO player_quest_difficulty_stats (player_uuid, difficulty_id, quests_completed)
                            VALUES (?, ?, 1)
                            ON CONFLICT(player_uuid, difficulty_id)
                            DO UPDATE SET quests_completed = quests_completed + 1
                            """)) {
                        statement.setString(1, playerId.toString());
                        statement.setString(2, difficultyId);
                        statement.executeUpdate();
                    }

                    int difficultyCompletedCount = readDifficultyCompletedCount(connection, playerId, difficultyId);
                    int totalCompletedCount = readTotalDifficultyCompletedCount(connection, playerId);
                    List<QuestMilestone> newlyExecuted = new ArrayList<>();
                    for (QuestMilestone milestone : milestones) {
                        if (milestone.completed() > difficultyCompletedCount) {
                            continue;
                        }
                        if (insertExecutedMilestone(connection, playerId, difficultyId, milestone.completed())) {
                            newlyExecuted.add(milestone);
                        }
                    }
                    connection.commit();
                    return new QuestCompletionStats(totalCompletedCount, difficultyCompletedCount, List.copyOf(newlyExecuted));
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
            }
        });
    }

    public CompletableFuture<List<QuestMilestoneClaim>> claimEligibleMilestones(UUID playerId, Map<String, Integer> difficultyCompletions, List<QuestDifficulty> difficulties) {
        return sqliteProvider.supplyAsync(() -> {
            List<QuestMilestoneClaim> newlyClaimed = new ArrayList<>();
            try (Connection connection = sqliteProvider.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    for (QuestDifficulty difficulty : difficulties) {
                        int completed = difficultyCompletions.getOrDefault(difficulty.id(), 0);
                        if (completed <= 0) {
                            continue;
                        }
                        for (QuestMilestone milestone : difficulty.milestones()) {
                            if (milestone.completed() > completed) {
                                continue;
                            }
                            if (insertExecutedMilestone(connection, playerId, difficulty.id(), milestone.completed())) {
                                newlyClaimed.add(new QuestMilestoneClaim(difficulty.id(), difficulty.displayName(), completed, milestone));
                            }
                        }
                    }
                    connection.commit();
                    return List.copyOf(newlyClaimed);
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
            }
        });
    }

    public CompletableFuture<Void> resetDailyQuests(UUID playerId, String resetKey) {
        return sqliteProvider.runAsync(() -> {
            try (Connection connection = sqliteProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         DELETE FROM player_quests
                         WHERE player_uuid = ? AND reset_key = ?
                         """)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, resetKey);
                statement.executeUpdate();
            }
        });
    }

    private int readDifficultyCompletedCount(Connection connection, UUID playerId, String difficultyId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT quests_completed
                FROM player_quest_difficulty_stats
                WHERE player_uuid = ? AND difficulty_id = ?
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, difficultyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("quests_completed") : 0;
            }
        }
    }

    private int readRerollsUsed(Connection connection, UUID playerId, String resetKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT used_count
                FROM player_quest_rerolls
                WHERE player_uuid = ? AND reset_key = ?
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, resetKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("used_count") : 0;
            }
        }
    }

    private int readQuestResetsUsed(Connection connection, UUID playerId, String resetKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT used_count
                FROM player_quest_resets
                WHERE player_uuid = ? AND reset_key = ?
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, resetKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("used_count") : 0;
            }
        }
    }

    private int readTotalDifficultyCompletedCount(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(SUM(quests_completed), 0) AS quests_completed
                FROM player_quest_difficulty_stats
                WHERE player_uuid = ?
                """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("quests_completed") : 0;
            }
        }
    }

    private boolean insertExecutedMilestone(Connection connection, UUID playerId, String difficultyId, int completed) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO player_quest_milestones (player_uuid, difficulty_id, completed)
                VALUES (?, ?, ?)
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, difficultyId);
            statement.setInt(3, completed);
            return statement.executeUpdate() > 0;
        }
    }

    private GeneratedQuest readQuest(UUID playerId, String resetKey, ResultSet resultSet) throws SQLException {
        return new GeneratedQuest(
                UUID.fromString(resultSet.getString("instance_id")),
                playerId,
                resetKey,
                resultSet.getString("definition_id"),
                QuestType.of(resultSet.getString("type")),
                fallback(resultSet.getString("difficulty_id"), "easy"),
                fallback(resultSet.getString("difficulty_display_name"), "<green><b>EASY"),
                resultSet.getString("display_name"),
                stringList(resultSet.getString("description")),
                stringMap(resultSet.getString("variables")),
                slotIndex(resultSet),
                resultSet.getInt("premium") == 1,
                resultSet.getInt("goal_amount"),
                resultSet.getInt("progress"),
                resultSet.getInt("completed") == 1
        );
    }

    private void bindQuest(PreparedStatement statement, GeneratedQuest quest) throws SQLException {
        statement.setString(1, quest.instanceId().toString());
        statement.setString(2, quest.playerId().toString());
        statement.setString(3, quest.resetKey());
        statement.setString(4, quest.definitionId());
        statement.setString(5, quest.type().key());
        statement.setString(6, quest.difficultyId());
        statement.setString(7, quest.difficultyDisplayName());
        statement.setString(8, quest.displayName());
        statement.setString(9, gson.toJson(quest.description()));
        statement.setString(10, gson.toJson(quest.variables()));
        if (quest.slotIndex() < 0) {
            statement.setNull(11, java.sql.Types.INTEGER);
        } else {
            statement.setInt(11, quest.slotIndex());
        }
        statement.setInt(12, quest.premium() ? 1 : 0);
        statement.setInt(13, quest.goalAmount());
        statement.setInt(14, quest.progress());
        statement.setInt(15, quest.completed() ? 1 : 0);
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

    private int slotIndex(ResultSet resultSet) throws SQLException {
        Object value = resultSet.getObject("slot_index");
        return value == null ? -1 : resultSet.getInt("slot_index");
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
