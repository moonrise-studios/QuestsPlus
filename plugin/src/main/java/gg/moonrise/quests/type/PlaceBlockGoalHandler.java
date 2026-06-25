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
import org.bukkit.event.block.BlockPlaceEvent;
import org.springframework.context.annotation.Lazy;

import java.util.Map;
import java.util.UUID;

@SpringComponent
public class PlaceBlockGoalHandler implements GoalHandler {

    private final QuestService questService;
    private final QuestResetService resetService;

    public PlaceBlockGoalHandler(@Lazy QuestService questService, @Lazy QuestResetService resetService) {
        this.questService = questService;
        this.resetService = resetService;
    }

    @Override
    public QuestType type() {
        return QuestTypes.PLACE_BLOCK;
    }

    @Override
    public void validateDefinition(QuestDefinition definition) {
        QuestTypeSupport.validateBlockTypeAndGoalAmount(definition);
    }

    @Override
    public GeneratedQuest createGeneratedQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
        return QuestTypeSupport.createBlockTypeQuest(definition, playerId, resetKey, variables);
    }

    @Override
    public Map<String, String> variablePlaceholders(GeneratedQuest quest) {
        return QuestTypeSupport.blockTypePlaceholders(quest);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.canBuild()) {
            return;
        }

        String placedBlockKey = QuestTypeSupport.blockTypeKey(event.getBlockPlaced().getType());
        if (placedBlockKey == null) {
            return;
        }

        questService.progressMatching(event.getPlayer(), type(), resetService.currentResetKey(), quest ->
                placedBlockKey.equals(quest.variables().get(QuestTypeSupport.BLOCK_TYPE))
        );
    }
}
