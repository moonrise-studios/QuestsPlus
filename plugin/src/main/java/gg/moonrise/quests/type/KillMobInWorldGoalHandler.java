package gg.moonrise.quests.type;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import gg.moonrise.quests.sdk.GoalHandler;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.model.QuestTypes;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class KillMobInWorldGoalHandler implements GoalHandler {

    public static final String MOB_TYPE = QuestTypeSupport.MOB_TYPE;
    public static final String WORLD = "world";
    public static final String GOAL_AMOUNT = QuestTypeSupport.GOAL_AMOUNT;

    private final QuestService questService;
    private final QuestResetService resetService;

    @Override
    public QuestType type() {
        return QuestTypes.KILL_MOB_IN_WORLD;
    }

    @Override
    public void validateDefinition(QuestDefinition definition) {
        QuestTypeSupport.requireVariables(definition, MOB_TYPE, WORLD, GOAL_AMOUNT);
        QuestTypeSupport.validateMobTypes(definition);
        for (String value : definition.selectorValues().get(WORLD)) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(definition.id() + " has blank world value");
            }
            if (Bukkit.getWorld(value.trim()) == null) {
                log.warn("QuestsPlus quest definition '{}' references world '{}' before it is loaded. Runtime matching will still use the configured world name.", definition.id(), value);
            }
        }
        QuestTypeSupport.validateGoalAmounts(definition);
    }

    @Override
    public GeneratedQuest createGeneratedQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
        String mobType = variables.get(MOB_TYPE);
        if (QuestTypeSupport.parseEntityType(mobType).isEmpty()) {
            throw new IllegalArgumentException(definition.id() + " generated invalid mob-type " + mobType);
        }

        String worldName = variables.get(WORLD);
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException(definition.id() + " generated blank world");
        }

        Map<String, String> canonicalVariables = new LinkedHashMap<>(variables);
        canonicalVariables.put(WORLD, worldName.trim());
        return QuestTypeSupport.createGoalAmountQuest(definition, playerId, resetKey, canonicalVariables);
    }

    @Override
    public Map<String, String> variablePlaceholders(GeneratedQuest quest) {
        Map<String, String> values = QuestTypeSupport.mobTypePlaceholders(quest);
        String world = quest.variables().get(WORLD);
        if (world != null) {
            values.put(WORLD, world);
            values.put("world_name", world);
        }
        return values;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        String killedWorldName = event.getEntity().getWorld().getName();
        questService.progressMatching(killer, type(), resetService.currentResetKey(), killMatcher(event.getEntityType(), killedWorldName));
    }

    public static Predicate<GeneratedQuest> killMatcher(EntityType killedType, String killedWorldName) {
        return quest -> killedType.name().equalsIgnoreCase(quest.variables().get(MOB_TYPE))
                && killedWorldName.equals(quest.variables().get(WORLD));
    }
}
