package gg.moonrise.quests.domain.model;

import java.util.List;

public record QuestCompletionStats(
        int questsCompleted,
        int difficultyCompleted,
        List<QuestMilestone> newlyExecutedMilestones
) {
}
