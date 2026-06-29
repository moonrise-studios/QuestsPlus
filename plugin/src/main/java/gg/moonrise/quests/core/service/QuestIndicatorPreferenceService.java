package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
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

    public Optional<String> cachedPreference(UUID playerId, Scope scope) {
        Preferences preferences = cache.get(playerId);
        if (preferences == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(scope == Scope.GLOBAL ? preferences.global() : preferences.personal());
    }

    public CompletableFuture<Preferences> loadPreference(UUID playerId) {
        return sqlProvider.supplyAsync(() -> {
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT indicator_type, personal_indicator_type, global_indicator_type
                         FROM player_quest_indicator_preferences
                         WHERE player_uuid = ?
                         """)) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        cache.remove(playerId);
                        return Preferences.empty();
                    }
                    String legacy = normalize(resultSet.getString("indicator_type"));
                    String personal = normalizeNullable(resultSet.getString("personal_indicator_type"));
                    String global = normalizeNullable(resultSet.getString("global_indicator_type"));
                    Preferences preferences = new Preferences(
                            personal == null ? legacy : personal,
                            global == null ? legacy : global
                    );
                    cache.put(playerId, preferences);
                    return preferences;
                }
            }
        });
    }

    public CompletableFuture<Void> setPreference(UUID playerId, Scope scope, String indicatorType) {
        String normalized = normalize(indicatorType);
        if (DEFAULT.equals(normalized)) {
            return clearPreference(playerId, scope);
        }
        Preferences updated = cache.merge(playerId, Preferences.of(scope, normalized), (current, ignored) -> current.with(scope, normalized));
        return sqlProvider.runAsync(() -> {
            try (Connection connection = sqlProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sqlProvider.upsertIndicatorPreferenceSql())) {
                statement.setString(1, playerId.toString());
                statement.setString(2, fallbackLegacy(updated));
                statement.setString(3, updated.personal());
                statement.setString(4, updated.global());
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> clearPreference(UUID playerId, Scope scope) {
        Preferences updated = cache.compute(playerId, (ignored, current) -> {
            Preferences base = current == null ? Preferences.empty() : current;
            Preferences next = base.with(scope, null);
            return next.isEmpty() ? null : next;
        });
        return sqlProvider.runAsync(() -> {
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
                try (PreparedStatement statement = connection.prepareStatement(sqlProvider.upsertIndicatorPreferenceSql())) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, fallbackLegacy(updated));
                    statement.setString(3, updated.personal());
                    statement.setString(4, updated.global());
                    statement.executeUpdate();
                }
            }
        });
    }

    public String normalize(String input) {
        return java.util.Objects.requireNonNullElse(input, "")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("[^A-Z0-9_]", "");
    }

    private String normalizeNullable(String input) {
        String normalized = normalize(input);
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

        private static Preferences of(Scope scope, String value) {
            return scope == Scope.GLOBAL ? new Preferences(null, value) : new Preferences(value, null);
        }

        private Preferences with(Scope scope, String value) {
            return scope == Scope.GLOBAL ? new Preferences(personal, value) : new Preferences(value, global);
        }

        private boolean isEmpty() {
            return personal == null && global == null;
        }
    }
}
