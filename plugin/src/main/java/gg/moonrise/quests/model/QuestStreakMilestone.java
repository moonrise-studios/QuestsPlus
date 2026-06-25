package gg.moonrise.quests.model;

import java.util.List;

public record QuestStreakMilestone(
        int streak,
        String displayName,
        List<String> lore,
        List<String> commands
) {
}
