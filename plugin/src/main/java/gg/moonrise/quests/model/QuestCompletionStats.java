package gg.moonrise.quests.model;

import java.util.List;

public record QuestCompletionStats(
        int questsCompleted,
        int difficultyCompleted,
        List<QuestMilestone> newlyExecutedMilestones
) {
}
