package gg.moonrise.quests.type;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.core.service.QuestProgressService;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.model.QuestTypes;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;

@SpringComponent
@RequiredArgsConstructor
public class TravelDistanceGoalHandler implements GoalHandler {

    public static final String GOAL_AMOUNT = QuestTypeSupport.GOAL_AMOUNT;

    private final QuestService questService;
    private final QuestProgressService progressService;
    private final QuestResetService resetService;

    @Override
    public QuestType type() {
        return QuestTypes.TRAVEL_DISTANCE;
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
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isInsideVehicle() || (player.isInWater() && !player.isSwimming())) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        int deltaX = Math.abs(to.getBlockX() - from.getBlockX());
        int deltaZ = Math.abs(to.getBlockZ() - from.getBlockZ());
        int distance = Math.max(deltaX, deltaZ);
        if (distance <= 0) {
            return;
        }

        String resetKey = resetService.currentResetKey();
        boolean personalQuestActive = questService.cachedState(player.getUniqueId(), resetKey)
                .filter(state -> state.hasActiveQuest(type()))
                .isPresent();
        if (!personalQuestActive && !progressService.hasCachedGlobalQuest(type())) {
            return;
        }

        progressService.progressMatching(player, type(), resetKey, distance, quest -> true);
    }
}
