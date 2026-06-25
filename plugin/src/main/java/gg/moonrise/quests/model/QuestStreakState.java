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

    public QuestStreakState withClaimedMilestones(Set<Integer> milestones) {
        return new QuestStreakState(playerId, currentStreak, highestStreak, lastCompletedResetKey, lastEvaluatedResetKey, lastLostStreak, lostResetKey, shieldBalance, recoveryBalance, milestones);
    }

    public QuestStreakState markMilestonesClaimed(Set<Integer> milestones) {
        LinkedHashSet<Integer> claimed = new LinkedHashSet<>(claimedMilestones);
        claimed.addAll(milestones);
        return withClaimedMilestones(claimed);
    }
}
