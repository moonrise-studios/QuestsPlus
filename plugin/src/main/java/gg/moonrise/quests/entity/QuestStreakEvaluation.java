package gg.moonrise.quests.entity;

import java.util.List;

public record QuestStreakEvaluation(
        QuestStreakState state,
        List<QuestStreakMilestone> newlyExecutedMilestones
) {
}
