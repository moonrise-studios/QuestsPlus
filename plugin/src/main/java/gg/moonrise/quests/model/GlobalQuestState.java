package gg.moonrise.quests.model;

import gg.moonrise.quests.sdk.model.GeneratedQuest;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record GlobalQuestState(
        GeneratedQuest quest,
        String periodKey,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Map<UUID, Integer> contributions,
        boolean rewardsExecuted
) {

    public int contribution(UUID playerId) {
        return contributions.getOrDefault(playerId, 0);
    }

    public int participants() {
        return (int) contributions.values().stream().filter(value -> value > 0).count();
    }

    public GlobalQuestState withQuest(GeneratedQuest updatedQuest, Map<UUID, Integer> updatedContributions) {
        return new GlobalQuestState(
                updatedQuest,
                periodKey,
                startsAt,
                endsAt,
                Map.copyOf(updatedContributions),
                rewardsExecuted
        );
    }
}
