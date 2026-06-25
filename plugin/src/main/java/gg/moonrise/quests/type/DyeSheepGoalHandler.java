package gg.moonrise.quests.type;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.model.QuestTypes;
import gg.moonrise.quests.util.QuestDisplayNames;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.SheepDyeWoolEvent;
import org.springframework.context.annotation.Lazy;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@SpringComponent
public class DyeSheepGoalHandler implements GoalHandler {

    private final QuestService questService;
    private final QuestResetService resetService;

    public DyeSheepGoalHandler(@Lazy QuestService questService, @Lazy QuestResetService resetService) {
        this.questService = questService;
        this.resetService = resetService;
    }

    @Override
    public QuestType type() {
        return QuestTypes.DYE_SHEEP;
    }

    @Override
    public void validateDefinition(QuestDefinition definition) {
        if (!definition.selectorValues().containsKey(QuestTypeSupport.COLOR)) {
            throw new IllegalArgumentException(definition.id() + " is missing variable " + QuestTypeSupport.COLOR);
        }
        if (!definition.selectorValues().containsKey(QuestTypeSupport.GOAL_AMOUNT)) {
            throw new IllegalArgumentException(definition.id() + " is missing variable " + QuestTypeSupport.GOAL_AMOUNT);
        }
        for (String value : definition.selectorValues().get(QuestTypeSupport.COLOR)) {
            parseDyeColor(value, definition.id());
        }
        for (String value : definition.selectorValues().get(QuestTypeSupport.GOAL_AMOUNT)) {
            int amount = QuestTypeSupport.parsePositiveInt(value, definition.id());
            if (amount <= 0) {
                throw new IllegalArgumentException(definition.id() + " has non-positive goal-amount value " + value);
            }
        }
    }

    @Override
    public GeneratedQuest createGeneratedQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
        DyeColor color = parseDyeColor(variables.get(QuestTypeSupport.COLOR), definition.id());
        Map<String, String> canonicalVariables = new LinkedHashMap<>(variables);
        canonicalVariables.put(QuestTypeSupport.COLOR, color.name());
        return QuestTypeSupport.createGoalAmountQuest(definition, playerId, resetKey, canonicalVariables);
    }

    @Override
    public Map<String, String> variablePlaceholders(GeneratedQuest quest) {
        Map<String, String> values = GoalHandler.super.variablePlaceholders(quest);
        String color = quest.variables().get(QuestTypeSupport.COLOR);
        if (color != null) {
            String displayName = QuestDisplayNames.typeName(color);
            values.put(QuestTypeSupport.COLOR, displayName);
        }
        return values;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSheepDyeWool(SheepDyeWoolEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        String color = event.getColor().name();
        questService.progressMatching(player, type(), resetService.currentResetKey(), quest ->
                color.equals(quest.variables().get(QuestTypeSupport.COLOR))
        );
    }

    private DyeColor parseDyeColor(String value, String definitionId) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(definitionId + " has invalid color value " + value);
        }
        try {
            return DyeColor.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(definitionId + " has invalid color value " + value, exception);
        }
    }
}
