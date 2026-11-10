package gg.moonrise.quests.core.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.StreakMilestoneConfig;
import gg.moonrise.quests.config.Streaks;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestStreakEvaluation;
import gg.moonrise.quests.model.QuestStreakMilestone;
import gg.moonrise.quests.model.QuestStreakState;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.util.QuestNumberFormatter;
import gg.moonrise.quests.util.QuestTokens;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestStreakService {

    public enum StreakCurrency {
        SHIELD,
        RECOVERY
    }

    public record StreakRecoveryResult(boolean applied, QuestStreakEvaluation evaluation) {
    }

    /**
     * Bounds how long a cached state may serve menu reads before it is reloaded, so writes made by another server
     * sharing the database become visible. Writes on this server always reload from the database first.
     */
    private static final Duration CACHE_FRESHNESS = Duration.ofMinutes(1);

    private final ConfigProvider configProvider;
    private final QuestStreakRepository repository;
    private final Cache<UUID, QuestStreakState> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(CACHE_FRESHNESS)
            .build();
    /**
     * Tail of each player's queue of streak read-modify-write operations; entries are removed once the queue drains.
     */
    private final Map<UUID, CompletableFuture<Void>> playerQueues = new ConcurrentHashMap<>();

    public boolean isEnabled() {
        Config config = configProvider.get();
        if (config == null) {
            return true;
        }
        Streaks streaks = config.getStreaks();
        return streaks != null && streaks.isEnabled();
    }

    public CompletableFuture<QuestStreakState> ensureState(Player player, String currentResetKey) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(cachedOrEmpty(player.getUniqueId()));
        }
        UUID playerId = player.getUniqueId();
        return serialized(playerId, () -> cachedOrRefreshedState(playerId, currentResetKey));
    }

    public CompletableFuture<QuestStreakEvaluation> evaluateQuestCompletion(Player player, PlayerQuestState questState, List<GeneratedQuest> eligibleQuests) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(new QuestStreakEvaluation(cachedOrEmpty(player.getUniqueId()), List.of()));
        }
        int required = dailyRequiredCompletions(questState, eligibleQuests);
        int completed = dailyCompleted(eligibleQuests);
        if (required <= 0 || completed < required) {
            return CompletableFuture.completedFuture(new QuestStreakEvaluation(cachedOrEmpty(player.getUniqueId()), List.of()));
        }

        UUID playerId = player.getUniqueId();
        String resetKey = questState.resetKey();
        return serialized(playerId, () -> refreshState(playerId, resetKey)
                .thenCompose(state -> {
                    if (resetKey.equals(state.lastCompletedResetKey())) {
                        return CompletableFuture.completedFuture(new QuestStreakEvaluation(state, List.of()));
                    }
                    return awardStreak(playerId, state, resetKey);
                })
                .thenApply(evaluation -> {
                    cache.put(playerId, evaluation.state());
                    return evaluation;
                }))
                .thenApply(evaluation -> {
                    handleStreakMilestoneRewards(player, evaluation);
                    return evaluation;
                });
    }

    public CompletableFuture<StreakRecoveryResult> recoverStreak(Player player, String currentResetKey) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(new StreakRecoveryResult(false, new QuestStreakEvaluation(cachedOrEmpty(player.getUniqueId()), List.of())));
        }
        UUID playerId = player.getUniqueId();
        return serialized(playerId, () -> refreshState(playerId, currentResetKey)
                .thenCompose(state -> {
                    if (!canRecover(state, currentResetKey) || state.recoveryBalance() <= 0) {
                        return CompletableFuture.completedFuture(new StreakRecoveryResult(false, new QuestStreakEvaluation(state, List.of())));
                    }
                    int restored = Math.max(state.currentStreak(), state.lastLostStreak());
                    QuestStreakState recovered = state.withStreak(restored)
                            .withLostStreak(0, "")
                            .withRecoveryBalance(state.recoveryBalance() - 1);
                    return saveState(recovered)
                            .thenCompose(unused -> claimStreakMilestones(recovered))
                            .thenApply(evaluation -> new StreakRecoveryResult(true, evaluation));
                })
                .thenApply(result -> {
                    cache.put(playerId, result.evaluation().state());
                    return result;
                }))
                .thenApply(result -> {
                    handleStreakMilestoneRewards(player, result.evaluation());
                    return result;
                });
    }

    public CompletableFuture<Integer> adjustCurrency(UUID playerId, StreakCurrency currency, int delta) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(0);
        }
        return serialized(playerId, () -> loadState(playerId)
                .thenCompose(state -> {
                    QuestStreakState updated = currency == StreakCurrency.SHIELD
                            ? state.withShieldBalance(Math.max(0, state.shieldBalance() + delta))
                            : state.withRecoveryBalance(Math.max(0, state.recoveryBalance() + delta));
                    return saveState(updated).thenApply(unused -> {
                        cache.put(playerId, updated);
                        return currency == StreakCurrency.SHIELD ? updated.shieldBalance() : updated.recoveryBalance();
                    });
                }));
    }

    public int dailyRequiredCompletions(PlayerQuestState state, List<GeneratedQuest> eligibleQuests) {
        if (!isEnabled()) {
            return 0;
        }
        int configured = configProvider.get().getStreaks().getDailyRequiredCompletions();
        if (configured > 0) {
            return configured;
        }
        return eligibleQuests.size();
    }

    public int dailyCompleted(List<GeneratedQuest> eligibleQuests) {
        return (int) eligibleQuests.stream().filter(GeneratedQuest::completed).count();
    }

    public boolean canRecover(QuestStreakState state, String currentResetKey) {
        return state.lastLostStreak() > 0 && recoveryDaysRemaining(state, currentResetKey) > 0;
    }

    public int recoveryDaysRemaining(QuestStreakState state, String currentResetKey) {
        if (!isEnabled()) {
            return 0;
        }
        if (state.lostResetKey() == null || state.lostResetKey().isBlank()) {
            return 0;
        }
        Optional<LocalDate> lostDate = parseDate(state.lostResetKey());
        Optional<LocalDate> currentDate = parseDate(currentResetKey);
        if (lostDate.isEmpty() || currentDate.isEmpty() || currentDate.get().isBefore(lostDate.get())) {
            return 0;
        }
        LocalDate lost = lostDate.get();
        LocalDate current = currentDate.get();
        int window = configProvider.get().getStreaks().getRecoveryWindowDays();
        if (window <= 0) {
            return 0;
        }
        int elapsed = (int) ChronoUnit.DAYS.between(lost, current);
        return Math.max(0, window - elapsed + 1);
    }

    public List<QuestStreakMilestone> milestones() {
        if (!isEnabled()) {
            return List.of();
        }
        List<QuestStreakMilestone> milestones = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        List<StreakMilestoneConfig> configured = configProvider.get().getStreaks().getMilestones();
        if (configured == null) {
            return List.of();
        }
        for (StreakMilestoneConfig milestone : configured) {
            int streak = milestone.getStreak();
            if (streak <= 0 || !seen.add(streak)) {
                continue;
            }
            milestones.add(new QuestStreakMilestone(
                    streak,
                    milestone.getDisplayName() == null ? QuestNumberFormatter.format(streak) : milestone.getDisplayName(),
                    milestone.getLore() == null ? List.of() : List.copyOf(milestone.getLore()),
                    milestone.getCommands() == null ? List.of() : List.copyOf(milestone.getCommands())
            ));
        }
        return List.copyOf(milestones);
    }

    /**
     * Returns the cached state when it is already evaluated for {@code currentResetKey}; otherwise refreshes it from
     * the database. Callers must hold the player's queue.
     */
    private CompletableFuture<QuestStreakState> cachedOrRefreshedState(UUID playerId, String currentResetKey) {
        QuestStreakState cached = cache.getIfPresent(playerId);
        if (cached != null && evaluateMissedWindows(cached, currentResetKey).equals(cached)) {
            return CompletableFuture.completedFuture(cached);
        }
        return refreshState(playerId, currentResetKey);
    }

    /**
     * Loads the stored state, applies missed reset windows, saves it if that changed anything, and caches the result.
     * Callers must hold the player's queue.
     */
    private CompletableFuture<QuestStreakState> refreshState(UUID playerId, String currentResetKey) {
        return loadState(playerId).thenCompose(loaded -> {
            QuestStreakState evaluated = evaluateMissedWindows(loaded, currentResetKey);
            CompletableFuture<Void> saved = evaluated.equals(loaded) ? CompletableFuture.completedFuture(null) : saveState(evaluated);
            return saved.thenApply(unused -> {
                cache.put(playerId, evaluated);
                return evaluated;
            });
        });
    }

    /**
     * Runs {@code operation} after every earlier operation queued for the same player has finished, so two
     * load-modify-save sequences for one player never interleave and overwrite each other.
     */
    private <T> CompletableFuture<T> serialized(UUID playerId, Supplier<CompletableFuture<T>> operation) {
        CompletableFuture<Void> finished = new CompletableFuture<>();
        CompletableFuture<Void> previous = playerQueues.put(playerId, finished);
        CompletableFuture<Void> ready = previous == null ? CompletableFuture.completedFuture(null) : previous;
        CompletableFuture<T> result = ready.thenCompose(unused -> start(operation));
        result.whenComplete((value, throwable) -> {
            playerQueues.remove(playerId, finished);
            finished.complete(null);
        });
        return result;
    }

    private static <T> CompletableFuture<T> start(Supplier<CompletableFuture<T>> operation) {
        try {
            return operation.get();
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private QuestStreakState evaluateMissedWindows(QuestStreakState state, String currentResetKey) {
        Optional<LocalDate> currentDate = parseDate(currentResetKey);
        if (currentDate.isEmpty()) {
            return state;
        }
        LocalDate current = currentDate.get();
        if (state.lastEvaluatedResetKey() == null || state.lastEvaluatedResetKey().isBlank()) {
            return state.withLastEvaluatedResetKey(current.minusDays(resetStepDays()).toString());
        }

        Optional<LocalDate> lastEvaluated = parseDate(state.lastEvaluatedResetKey())
                .filter(date -> date.isBefore(current));
        if (lastEvaluated.isEmpty()) {
            return state;
        }

        QuestStreakState updated = state;
        int stepDays = resetStepDays();
        LocalDate cursor = lastEvaluated.get().plusDays(stepDays);
        while (cursor.isBefore(current)) {
            String window = cursor.toString();
            if (!window.equals(updated.lastCompletedResetKey())) {
                updated = applyMissedWindow(updated, window);
            }
            updated = updated.withLastEvaluatedResetKey(window);
            cursor = cursor.plusDays(stepDays);
        }
        return updated;
    }

    private int resetStepDays() {
        return configProvider.get().getDaily().isWeekly() ? 7 : 1;
    }

    public QuestStreakState applyMissedWindow(QuestStreakState state, String missedResetKey) {
        if (state.currentStreak() <= 0) {
            return state;
        }
        if (state.shieldBalance() > 0) {
            return state.withShieldBalance(state.shieldBalance() - 1);
        }
        return state.withCurrentStreak(0).withLostStreak(state.currentStreak(), missedResetKey);
    }

    private CompletableFuture<QuestStreakEvaluation> awardStreak(UUID playerId, QuestStreakState state, String resetKey) {
        QuestStreakState awarded = state.withStreak(state.currentStreak() + 1)
                .withLastCompletedResetKey(resetKey)
                .withLastEvaluatedResetKey(resetKey);
        return saveState(awarded).thenCompose(unused -> claimStreakMilestones(awarded));
    }

    private CompletableFuture<QuestStreakEvaluation> claimStreakMilestones(QuestStreakState state) {
        List<QuestStreakMilestone> reached = milestones().stream()
                .filter(milestone -> milestone.streak() <= state.currentStreak())
                .toList();
        List<Integer> streaks = reached.stream().map(QuestStreakMilestone::streak).toList();
        return repository.claimMilestones(state.playerId(), streaks).thenApply(newlyClaimed -> {
            List<QuestStreakMilestone> newlyExecuted = reached.stream()
                    .filter(milestone -> newlyClaimed.contains(milestone.streak()))
                    .toList();
            Set<Integer> claimed = new LinkedHashSet<>(state.claimedMilestones());
            claimed.addAll(newlyClaimed);
            return new QuestStreakEvaluation(state.withClaimedMilestones(claimed), newlyExecuted);
        });
    }

    private CompletableFuture<QuestStreakState> loadState(UUID playerId) {
        return repository.load(playerId);
    }

    private CompletableFuture<Void> saveState(QuestStreakState state) {
        return repository.save(state);
    }

    private QuestStreakState cachedOrEmpty(UUID playerId) {
        QuestStreakState state = cache.getIfPresent(playerId);
        return state == null ? QuestStreakState.empty(playerId) : state;
    }

    private void handleStreakMilestoneRewards(Player player, QuestStreakEvaluation evaluation) {
        if (evaluation.newlyExecutedMilestones().isEmpty()) {
            return;
        }
        // The claims are already committed, so the console commands must run even if the player has logged out
        // meanwhile; they only need the name and UUID, which are resolved now while the Player is at hand.
        String playerName = player.getName();
        UUID playerId = player.getUniqueId();
        List<String> commands = new ArrayList<>();
        for (QuestStreakMilestone milestone : evaluation.newlyExecutedMilestones()) {
            for (String command : milestone.commands()) {
                commands.add(replaceTokens(command, playerName, playerId, evaluation.state(), milestone));
            }
        }
        Scheduler.sync().run(task -> {
            for (String command : commands) {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                } catch (RuntimeException exception) {
                    log.error("Failed to run QuestsPlus streak milestone command '{}' for {}.", command, playerId, exception);
                }
            }
        });
    }

    private String replaceTokens(String template, String playerName, UUID playerId, QuestStreakState state, QuestStreakMilestone milestone) {
        Map<String, String> values = Map.of(
                "player", playerName,
                "uuid", playerId.toString(),
                "streak", Integer.toString(state.currentStreak()),
                "highest_streak", Integer.toString(state.highestStreak()),
                "milestone_streak", Integer.toString(milestone.streak())
        );
        return QuestTokens.apply(template, values);
    }

    private Optional<LocalDate> parseDate(String resetKey) {
        try {
            return Optional.of(LocalDate.parse(resetKey));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }
}
