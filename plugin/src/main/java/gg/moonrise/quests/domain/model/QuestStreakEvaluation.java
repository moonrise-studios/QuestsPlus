package gg.moonrise.quests.domain.model;

import java.util.List;

public record QuestStreakEvaluation(
        QuestStreakState state,
        List<QuestStreakMilestone> newlyExecutedMilestones
) {
}
