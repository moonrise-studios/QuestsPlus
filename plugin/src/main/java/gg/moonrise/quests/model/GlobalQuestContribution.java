package gg.moonrise.quests.model;

import java.util.UUID;

public record GlobalQuestContribution(
        UUID playerId,
        int contribution,
        int rank,
        int participants
) {
}
