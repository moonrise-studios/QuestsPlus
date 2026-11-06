package gg.moonrise.quests.config;

public record DifficultyDirectory(
        String difficultyId,
        DifficultyConfig settings,
        QuestsFile quests,
        DifficultyMilestonesFile milestones
) {
}
