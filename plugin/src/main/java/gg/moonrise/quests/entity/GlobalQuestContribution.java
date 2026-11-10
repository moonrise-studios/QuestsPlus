package gg.moonrise.quests.entity;

import java.util.UUID;

public record GlobalQuestContribution(
        UUID playerId,
        int contribution,
        int rank,
        int participants
) {
}
