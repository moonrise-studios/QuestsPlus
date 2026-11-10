package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.core.service.QuestIndicatorPreferenceRepository.StoredPreferences;
import gg.moonrise.quests.util.QuestNames;
import lombok.RequiredArgsConstructor;

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

    private final QuestIndicatorPreferenceRepository repository;
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
        return repository.load(playerId)
                .thenApply(stored -> stored.map(this::toPreferences))
                .thenApply(loaded -> applyLoaded(playerId, loadToken, loaded))
                .whenComplete((ignored, throwable) -> pendingLoads.remove(playerId, loadToken));
    }

    private Preferences toPreferences(StoredPreferences stored) {
        String legacy = QuestNames.constantKey(stored.indicatorType());
        String personal = normalizeNullable(stored.personalIndicatorType());
        String global = normalizeNullable(stored.globalIndicatorType());
        return new Preferences(
                personal == null ? legacy : personal,
                global == null ? legacy : global
        );
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
        return repository.save(playerId, toStored(updated));
    }

    public CompletableFuture<Void> clearPreference(UUID playerId, Scope scope) {
        Preferences updated = cache.compute(playerId, (id, current) -> {
            pendingLoads.remove(id);
            Preferences base = current == null ? Preferences.empty() : current;
            Preferences next = base.with(scope, null);
            return next.isEmpty() ? null : next;
        });
        return updated == null ? repository.delete(playerId) : repository.save(playerId, toStored(updated));
    }

    private StoredPreferences toStored(Preferences preferences) {
        return new StoredPreferences(fallbackLegacy(preferences), preferences.personal(), preferences.global());
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
