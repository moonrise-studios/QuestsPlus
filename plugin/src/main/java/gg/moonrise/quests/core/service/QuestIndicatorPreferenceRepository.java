package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Owns the progress indicator preference SQL. Every operation is keyed by the player's UUID, so work for one
 * player runs in submission order on the SQL worker pool.
 */
@SpringComponent
@RequiredArgsConstructor
public class QuestIndicatorPreferenceRepository {

    private final SqlProvider sqlProvider;

    /**
     * Loads the stored columns as written, or an empty result when the player has no preference row.
     */
    public CompletableFuture<Optional<StoredPreferences>> load(UUID playerId) {
        return sqlProvider.supplyAsync(playerId, () -> {
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT indicator_type, personal_indicator_type, global_indicator_type
                         FROM player_quest_indicator_preferences
                         WHERE player_uuid = ?
                         """)) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new StoredPreferences(
                            resultSet.getString("indicator_type"),
                            resultSet.getString("personal_indicator_type"),
                            resultSet.getString("global_indicator_type")
                    ));
                }
            }
        });
    }

    public CompletableFuture<Void> save(UUID playerId, StoredPreferences preferences) {
        return sqlProvider.runAsync(playerId, () -> {
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sqlProvider.currentDatabase().upsertIndicatorPreferenceSql())) {
                statement.setString(1, playerId.toString());
                statement.setString(2, preferences.indicatorType());
                statement.setString(3, preferences.personalIndicatorType());
                statement.setString(4, preferences.globalIndicatorType());
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> delete(UUID playerId) {
        return sqlProvider.runAsync(playerId, () -> {
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         DELETE FROM player_quest_indicator_preferences
                         WHERE player_uuid = ?
                         """)) {
                statement.setString(1, playerId.toString());
                statement.executeUpdate();
            }
        });
    }

    /**
     * One preference row. {@code indicatorType} is the legacy single-preference column kept for older readers;
     * the scoped columns are {@code null} when that scope uses the default.
     */
    public record StoredPreferences(String indicatorType, String personalIndicatorType, String globalIndicatorType) {
    }
}
