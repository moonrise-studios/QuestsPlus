package gg.moonrise.quests.entity;

import java.util.List;

public record QuestMilestone(
        String difficultyId,
        int completed,
        String displayName,
        List<String> lore,
        List<String> commands
) {
}
