package gg.moonrise.quests.type;

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
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SpringComponent
@RequiredArgsConstructor
public class PlaceBlockGoalHandler implements GoalHandler {

    private final QuestProgressService progressService;
    private final QuestResetService resetService;

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

        Optional<String> placedBlock = QuestTypeSupport.blockTypeKey(event.getBlockPlaced().getType());
        if (placedBlock.isEmpty()) {
            return;
        }
        String placedBlockKey = placedBlock.get();

        progressService.progressMatching(event.getPlayer(), type(), resetService.currentResetKey(), quest ->
                placedBlockKey.equals(quest.variables().get(QuestTypeSupport.BLOCK_TYPE))
        );
    }
}
