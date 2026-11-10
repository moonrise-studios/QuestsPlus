package gg.moonrise.quests.presentation.goal;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.application.quest.QuestProgressService;
import gg.moonrise.quests.application.reset.QuestResetService;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.model.QuestTypes;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

@SpringComponent
@RequiredArgsConstructor
public class KillMobGoalHandler implements GoalHandler {

    public static final String MOB_TYPE = QuestTypeSupport.MOB_TYPE;
    public static final String GOAL_AMOUNT = QuestTypeSupport.GOAL_AMOUNT;

    private final QuestProgressService progressService;
    private final QuestResetService resetService;

    @Override
    public QuestType type() {
        return QuestTypes.KILL_MOB;
    }

    @Override
    public void validateDefinition(QuestDefinition definition) {
        QuestTypeSupport.validateMobTypeAndGoalAmount(definition);
    }

    @Override
    public GeneratedQuest createGeneratedQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
        String mobType = variables.get(MOB_TYPE);
        if (QuestTypeSupport.parseEntityType(mobType).isEmpty()) {
            throw new IllegalArgumentException(definition.id() + " generated invalid mob-type " + mobType);
        }
        return QuestTypeSupport.createGoalAmountQuest(definition, playerId, resetKey, variables);
    }

    @Override
    public Map<String, String> variablePlaceholders(GeneratedQuest quest) {
        return QuestTypeSupport.mobTypePlaceholders(quest);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        progressService.progressMatching(killer, type(), resetService.currentResetKey(), killMatcher(event.getEntityType()));
    }

    public static Predicate<GeneratedQuest> killMatcher(EntityType killedType) {
        return quest -> killedType.name().equalsIgnoreCase(quest.variables().get(MOB_TYPE));
    }
}
