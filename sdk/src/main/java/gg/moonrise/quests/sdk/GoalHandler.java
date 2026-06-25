package gg.moonrise.quests.sdk;

import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.util.QuestPlaceholders;
import org.bukkit.event.Listener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles validation, generation, placeholder rendering, and optional events for a quest type.
 *
 * <p>Goal handlers are Bukkit listeners. QuestsPlus automatically registers built-in Spring
 * handlers, and external plugins may register their handlers through {@link QuestApi}.</p>
 */
public interface GoalHandler extends Listener {

    /**
     * Returns the quest type handled by this handler.
     *
     * @return the quest type key
     */
    QuestType type();

    /**
     * Validates a configured quest definition before it can be selected.
     *
     * @param definition the definition to validate
     * @throws IllegalArgumentException when the definition is invalid for this goal type
     */
    void validateDefinition(QuestDefinition definition);

    /**
     * Creates a generated quest instance with resolved variables.
     *
     * @param definition the source definition
     * @param playerId the player id, or QuestsPlus' global placeholder id for global quests
     * @param resetKey the reset or period key
     * @param variables resolved variable values
     * @return a generated quest instance
     */
    GeneratedQuest createGeneratedQuest(QuestDefinition definition, java.util.UUID playerId, String resetKey, Map<String, String> variables);

    /**
     * Provides additional variable placeholders for display and reward rendering.
     *
     * <p>The default implementation exposes each raw variable key and an underscore alias.</p>
     *
     * @param quest the generated quest being rendered
     * @return placeholder values keyed without angle brackets
     */
    default Map<String, String> variablePlaceholders(GeneratedQuest quest) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : quest.variables().entrySet()) {
            values.put(entry.getKey(), entry.getValue());
            values.put(QuestPlaceholders.placeholderKey(entry.getKey()), entry.getValue());
        }
        return values;
    }
}
