package gg.moonrise.quests.type;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.application.quest.QuestProgressService;
import gg.moonrise.quests.application.reset.QuestResetService;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.model.QuestTypes;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Cake;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;
import java.util.UUID;

@SpringComponent
@RequiredArgsConstructor
public class EatCakeSliceGoalHandler implements GoalHandler {

    private final QuestProgressService progressService;
    private final QuestResetService resetService;

    @Override
    public QuestType type() {
        return QuestTypes.EAT_CAKE_SLICE;
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
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CAKE || !(block.getBlockData() instanceof Cake cake)) {
            return;
        }

        int previousBites = cake.getBites();
        Scheduler.entity(event.getPlayer()).run(task -> {
            Block currentBlock = block.getLocation().getBlock();
            boolean sliceEaten = currentBlock.getType() != Material.CAKE;
            if (!sliceEaten && currentBlock.getBlockData() instanceof Cake currentCake) {
                sliceEaten = currentCake.getBites() > previousBites;
            }
            if (sliceEaten) {
                progressService.progressMatching(event.getPlayer(), type(), resetService.currentResetKey(), quest -> true);
            }
        });
    }
}
