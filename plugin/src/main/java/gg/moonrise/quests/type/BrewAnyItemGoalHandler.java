package gg.moonrise.quests.type;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.model.QuestTypes;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.UUID;

@SpringComponent
@RequiredArgsConstructor
public class BrewAnyItemGoalHandler implements GoalHandler {

    public static final String GOAL_AMOUNT = QuestTypeSupport.GOAL_AMOUNT;

    private final QuestService questService;
    private final QuestResetService resetService;
    private final BrewingStandOutputTracker outputTracker;

    @Override
    public QuestType type() {
        return QuestTypes.BREW_ANY_ITEM;
    }

    @Override
    public void validateDefinition(QuestDefinition definition) {
        QuestTypeSupport.validateOnlyGoalAmount(definition);
    }

    @Override
    public GeneratedQuest createGeneratedQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
        return QuestTypeSupport.createGoalAmountQuest(definition, playerId, resetKey, variables);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        outputTracker.brewClick(event).ifPresent(click -> Scheduler.entity(player).run(task -> {
            BrewingStandOutputTracker.BrewedTake take = outputTracker.consume(event, click);
            if (!take.present()) {
                return;
            }

            questService.progressMatching(player, type(), resetService.currentResetKey(), take.amount(), quest -> true);
        }));
    }
}
