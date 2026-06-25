package gg.moonrise.quests.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record QuestDifficulty(
        String id,
        String displayName,
        int pickerSlot,
        int milestonesSlot,
        Map<String, Integer> requirements,
        List<String> rewardCommands,
        List<String> lore,
        List<QuestMilestone> milestones
) {

    public QuestDifficulty {
        requirements = requirements == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(requirements));
    }

    public boolean requirementsMet(PlayerQuestState state) {
        return unmetRequirements(state).isEmpty();
    }

    public Map<String, Integer> unmetRequirements(PlayerQuestState state) {
        if (requirements.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> unmet = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            int required = entry.getValue() == null ? 0 : entry.getValue();
            if (required <= 0) {
                continue;
            }
            String difficultyId = entry.getKey();
            int completed = state == null ? 0 : state.difficultyCompletions(difficultyId);
            if (completed < required) {
                unmet.put(difficultyId, required);
            }
        }
        return Collections.unmodifiableMap(unmet);
    }
}
