package gg.moonrise.quests.model;

import java.util.List;

public record QuestStreakEvaluation(
        QuestStreakState state,
        List<QuestStreakMilestone> newlyExecutedMilestones
) {
}
