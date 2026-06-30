package gg.moonrise.quests.sdk;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Resolves configured quest variable values during quest generation.
 */
public interface QuestVariableSelector {

    /**
     * Returns the selector type key used in quest configuration.
     *
     * @return the selector type key
     */
    public String type();

    /**
     * Selects one value from the configured values.
     *
     * @param values configured candidate values
     * @param random random source supplied by QuestsPlus
     * @return the selected raw value
     */
    public String select(List<String> values, RandomGenerator random);
}
