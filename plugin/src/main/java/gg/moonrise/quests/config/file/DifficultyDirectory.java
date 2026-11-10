package gg.moonrise.quests.config.file;

import gg.moonrise.quests.config.quest.DifficultyConfig;

public record DifficultyDirectory(
        String difficultyId,
        DifficultyConfig settings,
        QuestsFile quests,
        DifficultyMilestonesFile milestones
) {
}
