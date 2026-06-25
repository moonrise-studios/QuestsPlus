package gg.moonrise.quests.sdk.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A reusable quest definition loaded from configuration.
 *
 * @param id normalized quest definition id
 * @param type quest type handled by a registered goal handler
 * @param enabled whether the definition can be selected for new quests
 * @param difficultyId normalized difficulty id
 * @param difficultyDisplayName rendered difficulty display name
 * @param displayName rendered quest display name
 * @param description rendered quest description lines
 * @param selectorTypes variable key to selector type mapping
 * @param selectorValues variable key to configured selector values mapping
 * @param schedule optional selection availability schedule
 * @param rewardCommands quest-specific bonus reward commands
 */
public record QuestDefinition(
        String id,
        QuestType type,
        boolean enabled,
        String difficultyId,
        String difficultyDisplayName,
        String displayName,
        List<String> description,
        Map<String, String> selectorTypes,
        Map<String, List<String>> selectorValues,
        QuestDefinitionSchedule schedule,
        List<String> rewardCommands
) {

    /**
     * Checks whether this definition can be selected at an instant.
     *
     * @param instant instant to test
     * @return true when the schedule allows selection
     */
    public boolean activeAt(Instant instant) {
        return schedule == null || schedule.activeAt(instant);
    }
}
