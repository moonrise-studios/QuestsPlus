package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.util.QuestNames;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SpringComponent
@RequiredArgsConstructor
public class QuestIndicatorPreferenceService {

    public static final String DEFAULT = "DEFAULT";
    public static final String OFF = "OFF";
    public static final String DEFAULT_INDICATOR = "BOSS_BAR";

    private final SqlProvider sqlProvider;
    private final ConcurrentMap<UUID, Preferences> cache = new ConcurrentHashMap<>();
    /**
     * Token of the newest in-flight load per player. Writes and evictions remove it (while holding the cache
     * entry's lock) so a load that started earlier cannot overwrite what they put in the cache.
     */
    private final ConcurrentMap<UUID, Object> pendingLoads = new ConcurrentHashMap<>();

    public Optional<String> cachedPreference(UUID playerId, Scope scope) {
        Preferences preferences = cache.get(playerId);
        if (preferences == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(scope == Scope.GLOBAL ? preferences.global() : preferences.personal());
    }

    public void evict(UUID playerId) {
        cache.compute(playerId, (id, current) -> {
            pendingLoads.remove(id);
            return null;
        });
    }

    public CompletableFuture<Preferences> loadPreference(UUID playerId) {
        Object loadToken = new Object();
        pendingLoads.put(playerId, loadToken);
        return sqlProvider.supplyAsync(playerId, () -> readPreference(playerId))
                .thenApply(loaded -> applyLoaded(playerId, loadToken, loaded))
                .whenComplete((ignored, throwable) -> pendingLoads.remove(playerId, loadToken));
    }

    private Optional<Preferences> readPreference(UUID playerId) throws SQLException {
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
                String legacy = QuestNames.constantKey(resultSet.getString("indicator_type"));
                String personal = normalizeNullable(resultSet.getString("personal_indicator_type"));
                String global = normalizeNullable(resultSet.getString("global_indicator_type"));
                return Optional.of(new Preferences(
                        personal == null ? legacy : personal,
                        global == null ? legacy : global
                ));
            }
        }
    }

    private Preferences applyLoaded(UUID playerId, Object loadToken, Optional<Preferences> loaded) {
        Preferences cached = cache.compute(playerId, (id, current) -> {
            if (!pendingLoads.remove(id, loadToken)) {
                // A newer load, write or eviction happened after this load started; keep its result.
                return current;
            }
            return loaded.orElse(null);
        });
        return cached == null ? Preferences.empty() : cached;
    }

    public CompletableFuture<Void> setPreference(UUID playerId, Scope scope, String indicatorType) {
        String normalized = QuestNames.constantKey(indicatorType);
        if (DEFAULT.equals(normalized)) {
            return clearPreference(playerId, scope);
        }
        Preferences updated = cache.compute(playerId, (id, current) -> {
            pendingLoads.remove(id);
            return (current == null ? Preferences.empty() : current).with(scope, normalized);
        });
        return sqlProvider.runAsync(playerId, () -> {
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sqlProvider.currentDatabase().upsertIndicatorPreferenceSql())) {
                statement.setString(1, playerId.toString());
                statement.setString(2, fallbackLegacy(updated));
                statement.setString(3, updated.personal());
                statement.setString(4, updated.global());
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> clearPreference(UUID playerId, Scope scope) {
        Preferences updated = cache.compute(playerId, (id, current) -> {
            pendingLoads.remove(id);
            Preferences base = current == null ? Preferences.empty() : current;
            Preferences next = base.with(scope, null);
            return next.isEmpty() ? null : next;
        });
        return sqlProvider.runAsync(playerId, () -> {
            try (Connection connection = sqlProvider.getConnection()) {
                if (updated == null) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            DELETE FROM player_quest_indicator_preferences
                            WHERE player_uuid = ?
                            """)) {
                        statement.setString(1, playerId.toString());
                        statement.executeUpdate();
                    }
                    return;
                }
                try (PreparedStatement statement = connection.prepareStatement(sqlProvider.currentDatabase().upsertIndicatorPreferenceSql())) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, fallbackLegacy(updated));
                    statement.setString(3, updated.personal());
                    statement.setString(4, updated.global());
                    statement.executeUpdate();
                }
            }
        });
    }

    private String normalizeNullable(String input) {
        String normalized = QuestNames.constantKey(input);
        return normalized.isBlank() ? null : normalized;
    }

    private String fallbackLegacy(Preferences preferences) {
        if (preferences == null) {
            return DEFAULT_INDICATOR;
        }
        if (preferences.personal() != null) {
            return preferences.personal();
        }
        if (preferences.global() != null) {
            return preferences.global();
        }
        return DEFAULT_INDICATOR;
    }

    public enum Scope {
        PERSONAL,
        GLOBAL
    }

    public record Preferences(String personal, String global) {

        private static Preferences empty() {
            return new Preferences(null, null);
        }

        private Preferences with(Scope scope, String value) {
            return scope == Scope.GLOBAL ? new Preferences(personal, value) : new Preferences(value, global);
        }

        private boolean isEmpty() {
            return personal == null && global == null;
        }
    }
}
