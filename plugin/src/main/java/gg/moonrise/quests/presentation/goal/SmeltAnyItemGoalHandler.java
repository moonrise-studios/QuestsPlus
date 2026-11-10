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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.FurnaceExtractEvent;

import java.util.Map;
import java.util.UUID;

@SpringComponent
@RequiredArgsConstructor
public class SmeltAnyItemGoalHandler implements GoalHandler {

    public static final String GOAL_AMOUNT = QuestTypeSupport.GOAL_AMOUNT;

    private final QuestProgressService progressService;
    private final QuestResetService resetService;

    @Override
    public QuestType type() {
        return QuestTypes.SMELT_ANY_ITEM;
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
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        int amount = event.getItemAmount();
        if (amount <= 0) {
            return;
        }

        progressService.progressMatching(event.getPlayer(), type(), resetService.currentResetKey(), amount, quest -> true);
    }
}
