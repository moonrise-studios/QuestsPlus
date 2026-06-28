package gg.moonrise.quests.core.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestStreakEvaluation;
import gg.moonrise.quests.model.QuestStreakMilestone;
import gg.moonrise.quests.model.QuestStreakState;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.util.QuestNumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

    private final ConfigProvider configProvider;
    private final SqliteProvider sqliteProvider;
    private final Cache<UUID, QuestStreakState> cache = Caffeine.newBuilder().maximumSize(10_000).build();

    public boolean isEnabled() {
        Config config = configProvider.get();
        if (config == null) {
            return true;
        }
        Config.Streaks streaks = config.getStreaks();
        return streaks != null && streaks.isEnabled();
    }

    public CompletableFuture<QuestStreakState> ensureState(Player player, String currentResetKey) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(cachedOrEmpty(player.getUniqueId()));
        }
        return loadState(player.getUniqueId())
                .thenCompose(state -> evaluateMissedWindows(state, currentResetKey))
                .thenApply(state -> {
                    cache.put(player.getUniqueId(), state);
                    return state;
                });
    }

    public CompletableFuture<QuestStreakState> stateForMenu(Player player, String currentResetKey) {
        return ensureState(player, currentResetKey);
    }

    public CompletableFuture<QuestStreakEvaluation> evaluateQuestCompletion(Player player, PlayerQuestState questState) {
        return evaluateQuestCompletion(player, questState, questState.quests());
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

        return ensureState(player, questState.resetKey())
                .thenCompose(state -> {
                    if (questState.resetKey().equals(state.lastCompletedResetKey())) {
                        return CompletableFuture.completedFuture(new QuestStreakEvaluation(state, List.of()));
                    }
                    return awardStreak(player.getUniqueId(), state, questState.resetKey());
                })
                .thenApply(evaluation -> {
                    cache.put(player.getUniqueId(), evaluation.state());
                    handleStreakMilestoneRewards(player, evaluation);
                    return evaluation;
                });
    }

    public CompletableFuture<StreakRecoveryResult> recoverStreak(Player player, String currentResetKey) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(new StreakRecoveryResult(false, new QuestStreakEvaluation(cachedOrEmpty(player.getUniqueId()), List.of())));
        }
        return ensureState(player, currentResetKey)
                .thenCompose(state -> {
                    if (!canRecover(state, currentResetKey) || state.recoveryBalance() <= 0) {
                        return CompletableFuture.completedFuture(new StreakRecoveryResult(false, new QuestStreakEvaluation(state, List.of())));
                    }
                    int restored = Math.max(state.currentStreak(), state.lastLostStreak());
                    QuestStreakState recovered = new QuestStreakState(
                            state.playerId(),
                            restored,
                            Math.max(state.highestStreak(), restored),
                            state.lastCompletedResetKey(),
                            state.lastEvaluatedResetKey(),
                            0,
                            "",
                            state.shieldBalance(),
                            state.recoveryBalance() - 1,
                            state.claimedMilestones()
                    );
                    return saveState(recovered)
                            .thenCompose(unused -> claimStreakMilestones(recovered))
                            .thenApply(evaluation -> new StreakRecoveryResult(true, evaluation));
                })
                .thenApply(result -> {
                    cache.put(player.getUniqueId(), result.evaluation().state());
                    handleStreakMilestoneRewards(player, result.evaluation());
                    return result;
                });
    }

    public CompletableFuture<Integer> adjustCurrency(UUID playerId, StreakCurrency currency, int delta) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(0);
        }
        return loadState(playerId)
                .thenCompose(state -> {
                    int shieldBalance = state.shieldBalance();
                    int recoveryBalance = state.recoveryBalance();
                    if (currency == StreakCurrency.SHIELD) {
                        shieldBalance = Math.max(0, shieldBalance + delta);
                    } else {
                        recoveryBalance = Math.max(0, recoveryBalance + delta);
                    }
                    QuestStreakState updated = new QuestStreakState(
                            state.playerId(),
                            state.currentStreak(),
                            state.highestStreak(),
                            state.lastCompletedResetKey(),
                            state.lastEvaluatedResetKey(),
                            state.lastLostStreak(),
                            state.lostResetKey(),
                            shieldBalance,
                            recoveryBalance,
                            state.claimedMilestones()
                    );
                    return saveState(updated).thenApply(unused -> {
                        cache.put(playerId, updated);
                        return currency == StreakCurrency.SHIELD ? updated.shieldBalance() : updated.recoveryBalance();
                    });
                });
    }

    public int dailyRequiredCompletions(PlayerQuestState state) {
        return dailyRequiredCompletions(state, state.quests());
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

    public int dailyCompleted(PlayerQuestState state) {
        return dailyCompleted(state.quests());
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
        LocalDate lost = parseDate(state.lostResetKey());
        LocalDate current = parseDate(currentResetKey);
        if (lost == null || current == null || current.isBefore(lost)) {
            return 0;
        }
        int window = configProvider.get().getStreaks().getRecoveryWindowDays();
        if (window <= 0) {
            return 0;
        }
        int elapsed = (int) java.time.temporal.ChronoUnit.DAYS.between(lost, current);
        return Math.max(0, window - elapsed + 1);
    }

    public List<QuestStreakMilestone> milestones() {
        if (!isEnabled()) {
            return List.of();
        }
        List<QuestStreakMilestone> milestones = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        List<Config.StreakMilestoneConfig> configured = configProvider.get().getStreaks().getMilestones();
        if (configured == null) {
            return List.of();
        }
        for (Config.StreakMilestoneConfig milestone : configured) {
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

    private CompletableFuture<QuestStreakState> evaluateMissedWindows(QuestStreakState state, String currentResetKey) {
        LocalDate current = parseDate(currentResetKey);
        if (current == null) {
            return CompletableFuture.completedFuture(state);
        }
        if (state.lastEvaluatedResetKey() == null || state.lastEvaluatedResetKey().isBlank()) {
            QuestStreakState initialized = withLastEvaluated(state, current.minusDays(resetStepDays()).toString());
            return saveState(initialized).thenApply(unused -> initialized);
        }

        LocalDate cursor = parseDate(state.lastEvaluatedResetKey());
        if (cursor == null || !cursor.isBefore(current)) {
            return CompletableFuture.completedFuture(state);
        }

        QuestStreakState updated = state;
        int stepDays = resetStepDays();
        cursor = cursor.plusDays(stepDays);
        while (cursor.isBefore(current)) {
            String window = cursor.toString();
            if (!window.equals(updated.lastCompletedResetKey())) {
                updated = applyMissedWindow(updated, window);
            }
            updated = withLastEvaluated(updated, window);
            cursor = cursor.plusDays(stepDays);
        }
        QuestStreakState finalState = updated;
        return saveState(finalState).thenApply(unused -> finalState);
    }

    private int resetStepDays() {
        return configProvider.get().getDaily().isWeekly() ? 7 : 1;
    }

    QuestStreakState applyMissedWindow(QuestStreakState state, String missedResetKey) {
        if (state.currentStreak() <= 0) {
            return state;
        }
        if (state.shieldBalance() > 0) {
            return new QuestStreakState(
                    state.playerId(),
                    state.currentStreak(),
                    state.highestStreak(),
                    state.lastCompletedResetKey(),
                    state.lastEvaluatedResetKey(),
                    state.lastLostStreak(),
                    state.lostResetKey(),
                    state.shieldBalance() - 1,
                    state.recoveryBalance(),
                    state.claimedMilestones()
            );
        }
        return new QuestStreakState(
                state.playerId(),
                0,
                state.highestStreak(),
                state.lastCompletedResetKey(),
                state.lastEvaluatedResetKey(),
                state.currentStreak(),
                missedResetKey,
                state.shieldBalance(),
                state.recoveryBalance(),
                state.claimedMilestones()
        );
    }

    private CompletableFuture<QuestStreakEvaluation> awardStreak(UUID playerId, QuestStreakState state, String resetKey) {
        QuestStreakState awarded = new QuestStreakState(
                playerId,
                state.currentStreak() + 1,
                Math.max(state.highestStreak(), state.currentStreak() + 1),
                resetKey,
                resetKey,
                state.lastLostStreak(),
                state.lostResetKey(),
                state.shieldBalance(),
                state.recoveryBalance(),
                state.claimedMilestones()
        );
        return saveState(awarded).thenCompose(unused -> claimStreakMilestones(awarded));
    }

    private CompletableFuture<QuestStreakEvaluation> claimStreakMilestones(QuestStreakState state) {
        return sqliteProvider.supplyAsync(() -> {
            List<QuestStreakMilestone> newlyExecuted = new ArrayList<>();
            Set<Integer> claimed = new LinkedHashSet<>(state.claimedMilestones());
            try (Connection connection = sqliteProvider.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    for (QuestStreakMilestone milestone : milestones()) {
                        if (milestone.streak() > state.currentStreak()) {
                            continue;
                        }
                        if (insertStreakMilestone(connection, state.playerId(), milestone.streak())) {
                            newlyExecuted.add(milestone);
                            claimed.add(milestone.streak());
                        }
                    }
                    connection.commit();
                    return new QuestStreakEvaluation(state.withClaimedMilestones(claimed), List.copyOf(newlyExecuted));
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
            }
        });
    }

    private CompletableFuture<QuestStreakState> loadState(UUID playerId) {
        return sqliteProvider.supplyAsync(() -> {
            QuestStreakState state = readState(playerId);
            return state.withClaimedMilestones(readClaimedStreakMilestones(playerId));
        });
    }

    private CompletableFuture<Void> saveState(QuestStreakState state) {
        return sqliteProvider.runAsync(() -> {
            try (Connection connection = sqliteProvider.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO player_quest_streaks (
                             player_uuid, current_streak, highest_streak, last_completed_reset_key, last_evaluated_reset_key,
                             last_lost_streak, lost_reset_key, shield_balance, recovery_balance
                         )
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                         ON CONFLICT(player_uuid) DO UPDATE SET
                             current_streak = excluded.current_streak,
                             highest_streak = excluded.highest_streak,
                             last_completed_reset_key = excluded.last_completed_reset_key,
                             last_evaluated_reset_key = excluded.last_evaluated_reset_key,
                             last_lost_streak = excluded.last_lost_streak,
                             lost_reset_key = excluded.lost_reset_key,
                             shield_balance = excluded.shield_balance,
                             recovery_balance = excluded.recovery_balance
                         """)) {
                bindState(statement, state);
                statement.executeUpdate();
            }
        });
    }

    private QuestStreakState readState(UUID playerId) throws SQLException {
        try (Connection connection = sqliteProvider.getConnection();
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
        try (Connection connection = sqliteProvider.getConnection();
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
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO player_quest_streak_milestones (player_uuid, streak)
                VALUES (?, ?)
                """)) {
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

    private QuestStreakState withLastEvaluated(QuestStreakState state, String resetKey) {
        return new QuestStreakState(
                state.playerId(),
                state.currentStreak(),
                state.highestStreak(),
                state.lastCompletedResetKey(),
                resetKey,
                state.lastLostStreak(),
                state.lostResetKey(),
                state.shieldBalance(),
                state.recoveryBalance(),
                state.claimedMilestones()
        );
    }

    private QuestStreakState cachedOrEmpty(UUID playerId) {
        QuestStreakState state = cache.getIfPresent(playerId);
        return state == null ? QuestStreakState.empty(playerId) : state;
    }

    private void handleStreakMilestoneRewards(Player player, QuestStreakEvaluation evaluation) {
        if (evaluation.newlyExecutedMilestones().isEmpty()) {
            return;
        }
        Scheduler.entity(player).run(task -> {
            if (!player.isOnline()) {
                return;
            }
            for (QuestStreakMilestone milestone : evaluation.newlyExecutedMilestones()) {
                for (String command : milestone.commands()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replaceTokens(command, player, evaluation.state(), milestone));
                }
            }
        });
    }

    private String replaceTokens(String template, Player player, QuestStreakState state, QuestStreakMilestone milestone) {
        String output = template == null ? "" : template;
        Map<String, String> values = Map.of(
                "player", player.getName(),
                "uuid", player.getUniqueId().toString(),
                "streak", Integer.toString(state.currentStreak()),
                "highest_streak", Integer.toString(state.highestStreak()),
                "milestone_streak", Integer.toString(milestone.streak())
        );
        for (Map.Entry<String, String> entry : values.entrySet()) {
            output = output.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return output;
    }

    private LocalDate parseDate(String resetKey) {
        try {
            return LocalDate.parse(resetKey);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
