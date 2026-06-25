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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.springframework.context.annotation.Lazy;

import java.util.Map;
import java.util.Locale;
import java.util.UUID;

@SpringComponent
public class KillMobGoalHandler implements GoalHandler {

    public static final String MOB_TYPE = "mob-type";
    public static final String GOAL_AMOUNT = "goal-amount";

    private final QuestService questService;
    private final QuestResetService resetService;

    public KillMobGoalHandler(@Lazy QuestService questService, @Lazy QuestResetService resetService) {
        this.questService = questService;
        this.resetService = resetService;
    }

    @Override
    public QuestType type() {
        return QuestTypes.KILL_MOB;
    }

    @Override
    public void validateDefinition(QuestDefinition definition) {
        if (!definition.selectorValues().containsKey(MOB_TYPE)) {
            throw new IllegalArgumentException(definition.id() + " is missing variable " + MOB_TYPE);
        }
        if (!definition.selectorValues().containsKey(GOAL_AMOUNT)) {
            throw new IllegalArgumentException(definition.id() + " is missing variable " + GOAL_AMOUNT);
        }

        for (String value : definition.selectorValues().get(MOB_TYPE)) {
            EntityType entityType = parseEntityType(value);
            if (entityType == null || !entityType.isAlive()) {
                throw new IllegalArgumentException(definition.id() + " has invalid mob-type value " + value);
            }
        }
        for (String value : definition.selectorValues().get(GOAL_AMOUNT)) {
            int amount = parsePositiveInt(value, definition.id());
            if (amount <= 0) {
                throw new IllegalArgumentException(definition.id() + " has non-positive goal-amount value " + value);
            }
        }
    }

    @Override
    public GeneratedQuest createGeneratedQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
        String mobType = variables.get(MOB_TYPE);
        if (parseEntityType(mobType) == null) {
            throw new IllegalArgumentException(definition.id() + " generated invalid mob-type " + mobType);
        }
        int goalAmount = parsePositiveInt(variables.get(GOAL_AMOUNT), definition.id());
        return new GeneratedQuest(
                UUID.randomUUID(),
                playerId,
                resetKey,
                definition.id(),
                definition.type(),
                definition.difficultyId(),
                definition.difficultyDisplayName(),
                definition.displayName(),
                definition.description(),
                Map.copyOf(variables),
                goalAmount,
                0,
                false
        );
    }

    @Override
    public Map<String, String> variablePlaceholders(GeneratedQuest quest) {
        Map<String, String> values = GoalHandler.super.variablePlaceholders(quest);
        String mobType = quest.variables().get(MOB_TYPE);
        if (mobType != null) {
            String displayName = QuestDisplayNames.typeName(mobType, quest.goalAmount());
            values.put(MOB_TYPE, displayName);
            values.put("mob_type", displayName);
            values.put("mob-type-raw", mobType);
            values.put("mob_type_raw", mobType);
        }
        return values;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        EntityType killedType = event.getEntityType();
        questService.progressMatching(killer, type(), resetService.currentResetKey(), quest ->
                killedType.name().equalsIgnoreCase(quest.variables().get(MOB_TYPE))
        );
    }

    private int parsePositiveInt(String value, String definitionId) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(definitionId + " has invalid goal-amount value " + value, exception);
        }
    }

    private EntityType parseEntityType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EntityType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
