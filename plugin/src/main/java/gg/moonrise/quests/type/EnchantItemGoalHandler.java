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
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.springframework.context.annotation.Lazy;

import java.util.Map;
import java.util.UUID;

@SpringComponent
public class EnchantItemGoalHandler implements GoalHandler {

    private final QuestService questService;
    private final QuestResetService resetService;

    public EnchantItemGoalHandler(@Lazy QuestService questService, @Lazy QuestResetService resetService) {
        this.questService = questService;
        this.resetService = resetService;
    }

    @Override
    public QuestType type() {
        return QuestTypes.ENCHANT_ITEM;
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
    public void onEnchantItem(EnchantItemEvent event) {
        questService.progressMatching(event.getEnchanter(), type(), resetService.currentResetKey(), quest -> true);
    }
}
