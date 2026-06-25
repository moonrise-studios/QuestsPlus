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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.springframework.context.annotation.Lazy;

import java.util.Map;
import java.util.UUID;

@SpringComponent
public class BrewAnyItemGoalHandler implements GoalHandler {

    public static final String GOAL_AMOUNT = "goal-amount";

    private final QuestService questService;
    private final QuestResetService resetService;
    private final BrewingStandOutputTracker outputTracker;

    public BrewAnyItemGoalHandler(@Lazy QuestService questService, @Lazy QuestResetService resetService, BrewingStandOutputTracker outputTracker) {
        this.questService = questService;
        this.resetService = resetService;
        this.outputTracker = outputTracker;
    }

    @Override
    public QuestType type() {
        return QuestTypes.BREW_ANY_ITEM;
    }

    @Override
    public void validateDefinition(QuestDefinition definition) {
        validateOnlyGoalAmount(definition);
        for (String value : definition.selectorValues().get(GOAL_AMOUNT)) {
            int amount = parsePositiveInt(value, definition.id());
            if (amount <= 0) {
                throw new IllegalArgumentException(definition.id() + " has non-positive goal-amount value " + value);
            }
        }
    }

    @Override
    public GeneratedQuest createGeneratedQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) {
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

    private void validateOnlyGoalAmount(QuestDefinition definition) {
        if (!definition.selectorValues().containsKey(GOAL_AMOUNT)) {
            throw new IllegalArgumentException(definition.id() + " is missing variable " + GOAL_AMOUNT);
        }
        if (definition.selectorValues().get(GOAL_AMOUNT).isEmpty()) {
            throw new IllegalArgumentException(definition.id() + " has no goal-amount values");
        }
        for (String key : definition.selectorValues().keySet()) {
            if (!GOAL_AMOUNT.equals(key)) {
                throw new IllegalArgumentException(definition.id() + " does not support variable " + key);
            }
        }
    }

    private int parsePositiveInt(String value, String definitionId) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(definitionId + " has invalid goal-amount value " + value, exception);
        }
    }
}
