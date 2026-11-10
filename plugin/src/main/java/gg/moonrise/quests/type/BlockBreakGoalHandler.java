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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;
import java.util.UUID;

@SpringComponent
@RequiredArgsConstructor
public class BlockBreakGoalHandler implements GoalHandler {

    public static final String BLOCK_TYPE = QuestTypeSupport.BLOCK_TYPE;
    public static final String GOAL_AMOUNT = QuestTypeSupport.GOAL_AMOUNT;

    private final QuestProgressService progressService;
    private final QuestResetService resetService;

    @Override
    public QuestType type() {
        return QuestTypes.BLOCK_BREAK;
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
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String brokenBlockKey = event.getBlock().getType().asBlockType().getKey().toString();
        progressService.progressMatching(player, type(), resetService.currentResetKey(), quest ->
                brokenBlockKey.equals(quest.variables().get(BLOCK_TYPE))
        );
    }
}
