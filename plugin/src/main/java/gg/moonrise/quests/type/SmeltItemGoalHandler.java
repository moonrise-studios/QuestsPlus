package gg.moonrise.quests.type;

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
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.springframework.context.annotation.Lazy;

import java.util.Map;
import java.util.UUID;

@SpringComponent
public class SmeltItemGoalHandler implements GoalHandler {

    public static final String ITEM_TYPE = QuestTypeSupport.ITEM_TYPE;
    public static final String GOAL_AMOUNT = QuestTypeSupport.GOAL_AMOUNT;

    private final QuestService questService;
    private final QuestResetService resetService;

    public SmeltItemGoalHandler(@Lazy QuestService questService, @Lazy QuestResetService resetService) {
        this.questService = questService;
        this.resetService = resetService;
    }

    @Override
    public QuestType type() {
        return QuestTypes.SMELT_ITEM;
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
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        int amount = event.getItemAmount();
        if (amount <= 0) {
            return;
        }

        String extractedItemKey = QuestTypeSupport.itemTypeKey(event.getItemType());
        if (extractedItemKey == null) {
            return;
        }

        questService.progressMatching(event.getPlayer(), type(), resetService.currentResetKey(), amount, quest ->
                extractedItemKey.equals(quest.variables().get(ITEM_TYPE))
        );
    }
}
