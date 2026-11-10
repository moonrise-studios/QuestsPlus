package gg.moonrise.quests.entity;

public record QuestMilestoneClaim(
        String difficultyId,
        String difficultyDisplayName,
        int difficultyCompleted,
        QuestMilestone milestone
) {
}
