package gg.moonrise.quests.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record QuestStreakState(
        UUID playerId,
        int currentStreak,
        int highestStreak,
        String lastCompletedResetKey,
        String lastEvaluatedResetKey,
        int lastLostStreak,
        String lostResetKey,
        int shieldBalance,
        int recoveryBalance,
        Set<Integer> claimedMilestones
) {

    public QuestStreakState {
        claimedMilestones = Set.copyOf(claimedMilestones == null ? Set.of() : claimedMilestones);
    }

    public static QuestStreakState empty(UUID playerId) {
        return new QuestStreakState(playerId, 0, 0, "", "", 0, "", 0, 0, Set.of());
    }

    public boolean hasClaimedMilestone(int streak) {
        return claimedMilestones.contains(streak);
    }

    public QuestStreakState withCurrentStreak(int streak) {
        return new QuestStreakState(playerId, streak, highestStreak, lastCompletedResetKey, lastEvaluatedResetKey, lastLostStreak, lostResetKey, shieldBalance, recoveryBalance, claimedMilestones);
    }

    public QuestStreakState withHighestStreak(int streak) {
        return new QuestStreakState(playerId, currentStreak, streak, lastCompletedResetKey, lastEvaluatedResetKey, lastLostStreak, lostResetKey, shieldBalance, recoveryBalance, claimedMilestones);
    }

    /**
     * Sets the current streak and raises the highest streak when the new value exceeds it.
     */
    public QuestStreakState withStreak(int streak) {
        return withCurrentStreak(streak).withHighestStreak(Math.max(highestStreak, streak));
    }

    public QuestStreakState withLastCompletedResetKey(String resetKey) {
        return new QuestStreakState(playerId, currentStreak, highestStreak, resetKey, lastEvaluatedResetKey, lastLostStreak, lostResetKey, shieldBalance, recoveryBalance, claimedMilestones);
    }

    public QuestStreakState withLastEvaluatedResetKey(String resetKey) {
        return new QuestStreakState(playerId, currentStreak, highestStreak, lastCompletedResetKey, resetKey, lastLostStreak, lostResetKey, shieldBalance, recoveryBalance, claimedMilestones);
    }

    /**
     * Records a lost streak and the reset window it was lost in; pass {@code 0} and {@code ""} to clear it.
     */
    public QuestStreakState withLostStreak(int streak, String resetKey) {
        return new QuestStreakState(playerId, currentStreak, highestStreak, lastCompletedResetKey, lastEvaluatedResetKey, streak, resetKey, shieldBalance, recoveryBalance, claimedMilestones);
    }

    public QuestStreakState withShieldBalance(int balance) {
        return new QuestStreakState(playerId, currentStreak, highestStreak, lastCompletedResetKey, lastEvaluatedResetKey, lastLostStreak, lostResetKey, balance, recoveryBalance, claimedMilestones);
    }

    public QuestStreakState withRecoveryBalance(int balance) {
        return new QuestStreakState(playerId, currentStreak, highestStreak, lastCompletedResetKey, lastEvaluatedResetKey, lastLostStreak, lostResetKey, shieldBalance, balance, claimedMilestones);
    }

    public QuestStreakState withClaimedMilestones(Set<Integer> milestones) {
        return new QuestStreakState(playerId, currentStreak, highestStreak, lastCompletedResetKey, lastEvaluatedResetKey, lastLostStreak, lostResetKey, shieldBalance, recoveryBalance, milestones);
    }

    public QuestStreakState markMilestonesClaimed(Set<Integer> milestones) {
        LinkedHashSet<Integer> claimed = new LinkedHashSet<>(claimedMilestones);
        claimed.addAll(milestones);
        return withClaimedMilestones(claimed);
    }
}
