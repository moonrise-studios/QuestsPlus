package gg.moonrise.quests.model;

public record QuestMilestoneClaim(
        String difficultyId,
        String difficultyDisplayName,
        int difficultyCompleted,
        QuestMilestone milestone
) {
}
