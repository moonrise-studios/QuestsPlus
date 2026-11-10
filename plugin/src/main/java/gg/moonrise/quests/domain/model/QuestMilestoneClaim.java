package gg.moonrise.quests.domain.model;

public record QuestMilestoneClaim(
        String difficultyId,
        String difficultyDisplayName,
        int difficultyCompleted,
        QuestMilestone milestone
) {
}
