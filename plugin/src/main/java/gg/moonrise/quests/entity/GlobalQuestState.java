package gg.moonrise.quests.entity;

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

    /**
     * Ranks {@code playerId} among the positive contributors, highest first with ties broken by UUID
     * string, in one pass by counting the contributors ahead instead of sorting everyone. A player
     * without a contribution has rank 0.
     */
    public Rank rank(UUID playerId) {
        int own = contribution(playerId);
        String ownKey = playerId.toString();
        int participants = 0;
        int ahead = 0;
        for (Map.Entry<UUID, Integer> entry : contributions.entrySet()) {
            int value = entry.getValue();
            if (value <= 0) {
                continue;
            }
            participants++;
            if (own > 0 && (value > own || value == own && entry.getKey().toString().compareTo(ownKey) < 0)) {
                ahead++;
            }
        }
        return new Rank(own > 0 ? ahead + 1 : 0, participants);
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

    public record Rank(int rank, int participants) {
    }
}
