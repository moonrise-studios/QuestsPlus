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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.springframework.context.annotation.Lazy;

import java.util.Map;
import java.util.UUID;

@SpringComponent
public class BrewItemGoalHandler implements GoalHandler {

    public static final String ITEM_TYPE = QuestTypeSupport.ITEM_TYPE;
    public static final String GOAL_AMOUNT = QuestTypeSupport.GOAL_AMOUNT;

    private final QuestService questService;
    private final QuestResetService resetService;
    private final BrewingStandOutputTracker outputTracker;

    public BrewItemGoalHandler(@Lazy QuestService questService, @Lazy QuestResetService resetService, BrewingStandOutputTracker outputTracker) {
        this.questService = questService;
        this.resetService = resetService;
        this.outputTracker = outputTracker;
    }

    @Override
    public QuestType type() {
        return QuestTypes.BREW_ITEM;
    }

    @Override
    public void validateDefinition(QuestDefinition definition) {
        QuestTypeSupport.validateItemTypeAndGoalAmount(definition);
    }

    @Override
    public GeneratedQuest createGeneratedQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
        return QuestTypeSupport.createItemTypeQuest(definition, playerId, resetKey, variables);
    }

    @Override
    public Map<String, String> variablePlaceholders(GeneratedQuest quest) {
        return QuestTypeSupport.itemTypePlaceholders(quest);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewComplete(BrewEvent event) {
        outputTracker.recordBrew(event, event.getResults());
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

            questService.progressMatching(player, type(), resetService.currentResetKey(), take.amount(), quest ->
                    take.itemKey().equals(quest.variables().get(ITEM_TYPE))
            );
        }));
    }
}
