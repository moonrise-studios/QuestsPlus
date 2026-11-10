package gg.moonrise.quests.entity;

import java.util.List;

public record QuestCompletionStats(
        int questsCompleted,
        int difficultyCompleted,
        List<QuestMilestone> newlyExecutedMilestones
) {
}
