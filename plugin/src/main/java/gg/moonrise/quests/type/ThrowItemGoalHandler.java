package gg.moonrise.quests.type;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.core.service.QuestProgressService;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.model.QuestTypes;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SpringComponent
@RequiredArgsConstructor
public class ThrowItemGoalHandler implements GoalHandler {

    private final QuestProgressService progressService;
    private final QuestResetService resetService;

    @Override
    public QuestType type() {
        return QuestTypes.THROW_ITEM;
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
    public void onPlayerLaunchProjectile(PlayerLaunchProjectileEvent event) {
        Optional<String> thrownItem = QuestTypeSupport.itemTypeKey(event.getItemStack());
        if (thrownItem.isEmpty()) {
            return;
        }
        String itemTypeKey = thrownItem.get();

        progressService.progressMatching(event.getPlayer(), type(), resetService.currentResetKey(), quest ->
                itemTypeKey.equals(quest.variables().get(QuestTypeSupport.ITEM_TYPE))
        );
    }
}
