package gg.moonrise.quests.type;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.core.service.QuestProgressService;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.model.QuestTypes;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@SpringComponent
@RequiredArgsConstructor
public class HarvestItemGoalHandler implements GoalHandler {

    private final QuestProgressService progressService;
    private final QuestResetService resetService;

    @Override
    public QuestType type() {
        return QuestTypes.HARVEST_ITEM;
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
    public void onBlockDropItem(BlockDropItemEvent event) {
        Map<String, Integer> droppedItems = new LinkedHashMap<>();
        for (Item item : event.getItems()) {
            QuestTypeSupport.itemTypeKey(item.getItemStack())
                    .ifPresent(itemTypeKey -> droppedItems.merge(itemTypeKey, item.getItemStack().getAmount(), Integer::sum));
        }

        for (Map.Entry<String, Integer> entry : droppedItems.entrySet()) {
            int amount = entry.getValue();
            if (amount <= 0) {
                continue;
            }
            progressService.progressMatching(event.getPlayer(), type(), resetService.currentResetKey(), amount, quest ->
                    entry.getKey().equals(quest.variables().get(QuestTypeSupport.ITEM_TYPE))
            );
        }
    }
}
