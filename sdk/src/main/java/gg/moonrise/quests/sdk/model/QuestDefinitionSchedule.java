package gg.moonrise.quests.sdk.model;

import java.time.Instant;

/**
 * Optional begin/end availability window for a quest definition.
 *
 * @param begin inclusive begin instant, or null for no lower bound
 * @param end exclusive end instant, or null for no upper bound
 */
public record QuestDefinitionSchedule(
        Instant begin,
        Instant end
) {

    /**
     * Creates an always-active schedule.
     *
     * @return an always-active schedule
     */
    public static QuestDefinitionSchedule alwaysActive() {
        return new QuestDefinitionSchedule(null, null);
    }

    /**
     * Checks whether an instant is inside this schedule.
     *
     * @param instant instant to test
     * @return true when the instant is inside the configured window
     */
    public boolean activeAt(Instant instant) {
        if (begin != null && instant.isBefore(begin)) {
            return false;
        }
        return end == null || instant.isBefore(end);
    }
}
