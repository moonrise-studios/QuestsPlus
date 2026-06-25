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
import org.bukkit.Material;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.springframework.context.annotation.Lazy;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@SpringComponent
public class MilkMobGoalHandler implements GoalHandler {

    public static final String MOB_TYPE = "mob-type";
    public static final String GOAL_AMOUNT = "goal-amount";

    private final QuestService questService;
    private final QuestResetService resetService;

    public MilkMobGoalHandler(@Lazy QuestService questService, @Lazy QuestResetService resetService) {
        this.questService = questService;
        this.resetService = resetService;
    }

    @Override
    public QuestType type() {
        return QuestTypes.MILK_MOB;
    }

    @Override
    public void validateDefinition(QuestDefinition definition) {
        if (!definition.selectorValues().containsKey(MOB_TYPE)) {
            throw new IllegalArgumentException(definition.id() + " is missing variable " + MOB_TYPE);
        }
        if (!definition.selectorValues().containsKey(GOAL_AMOUNT)) {
            throw new IllegalArgumentException(definition.id() + " is missing variable " + GOAL_AMOUNT);
        }
        for (String key : definition.selectorValues().keySet()) {
            if (!MOB_TYPE.equals(key) && !GOAL_AMOUNT.equals(key)) {
                throw new IllegalArgumentException(definition.id() + " does not support variable " + key);
            }
        }

        for (String value : definition.selectorValues().get(MOB_TYPE)) {
            EntityType entityType = parseEntityType(value);
            if (!isMilkableType(entityType)) {
                throw new IllegalArgumentException(definition.id() + " has invalid milkable mob-type value " + value);
            }
        }
        for (String value : definition.selectorValues().get(GOAL_AMOUNT)) {
            int amount = QuestTypeSupport.parsePositiveInt(value, definition.id());
            if (amount <= 0) {
                throw new IllegalArgumentException(definition.id() + " has non-positive goal-amount value " + value);
            }
        }
    }

    @Override
    public GeneratedQuest createGeneratedQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
        EntityType entityType = parseEntityType(variables.get(MOB_TYPE));
        if (!isMilkableType(entityType)) {
            throw new IllegalArgumentException(definition.id() + " generated invalid milkable mob-type " + variables.get(MOB_TYPE));
        }

        Map<String, String> canonicalVariables = new LinkedHashMap<>(variables);
        canonicalVariables.put(MOB_TYPE, entityType.name());
        int goalAmount = QuestTypeSupport.parsePositiveInt(variables.get(GOAL_AMOUNT), definition.id());
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
                Map.copyOf(canonicalVariables),
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
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        EntityType entityType = entity.getType();
        if (!isMilkableType(entityType) || (entity instanceof Ageable ageable && !ageable.isAdult())) {
            return;
        }

        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item.getType() != Material.BUCKET) {
            return;
        }

        questService.progressMatching(event.getPlayer(), type(), resetService.currentResetKey(), quest ->
                entityType.name().equalsIgnoreCase(quest.variables().get(MOB_TYPE))
        );
    }

    private static boolean isMilkableType(EntityType entityType) {
        return entityType == EntityType.COW || entityType == EntityType.GOAT || entityType == EntityType.MOOSHROOM;
    }

    private static EntityType parseEntityType(String value) {
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
