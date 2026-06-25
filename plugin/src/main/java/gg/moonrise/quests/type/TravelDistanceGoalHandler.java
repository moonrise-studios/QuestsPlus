package gg.moonrise.quests.type;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.model.QuestTypes;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.springframework.context.annotation.Lazy;

import java.util.Map;
import java.util.UUID;

@SpringComponent
public class TravelDistanceGoalHandler implements GoalHandler {

    public static final String GOAL_AMOUNT = "goal-amount";

    private final QuestService questService;
    private final QuestResetService resetService;

    public TravelDistanceGoalHandler(@Lazy QuestService questService, @Lazy QuestResetService resetService) {
        this.questService = questService;
        this.resetService = resetService;
    }

    @Override
    public QuestType type() {
        return QuestTypes.TRAVEL_DISTANCE;
    }

    @Override
    public void validateDefinition(QuestDefinition definition) {
        validateOnlyGoalAmount(definition);
        for (String value : definition.selectorValues().get(GOAL_AMOUNT)) {
            int amount = parsePositiveInt(value, definition.id());
            if (amount <= 0) {
                throw new IllegalArgumentException(definition.id() + " has non-positive goal-amount value " + value);
            }
        }
    }

    @Override
    public GeneratedQuest createGeneratedQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
        int goalAmount = parsePositiveInt(variables.get(GOAL_AMOUNT), definition.id());
        return new GeneratedQuest(
                UUID.randomUUID(),
                playerId,
                resetKey,
                definition.id(),
                definition.type(),
                definition.difficultyId(),
                definition.difficultyDisplayName(),
                definition.displayName(),
                definition.description(),
                Map.copyOf(variables),
                goalAmount,
                0,
                false
        );
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
        PlayerQuestState state = questService.cachedState(player.getUniqueId(), resetKey);
        if ((state == null || !state.hasActiveQuest(type())) && !questService.hasCachedGlobalQuest(type())) {
            return;
        }

        questService.progressMatching(player, type(), resetKey, distance, quest -> true);
    }

    private void validateOnlyGoalAmount(QuestDefinition definition) {
        if (!definition.selectorValues().containsKey(GOAL_AMOUNT)) {
            throw new IllegalArgumentException(definition.id() + " is missing variable " + GOAL_AMOUNT);
        }
        if (definition.selectorValues().get(GOAL_AMOUNT).isEmpty()) {
            throw new IllegalArgumentException(definition.id() + " has no goal-amount values");
        }
        for (String key : definition.selectorValues().keySet()) {
            if (!GOAL_AMOUNT.equals(key)) {
                throw new IllegalArgumentException(definition.id() + " does not support variable " + key);
            }
        }
    }

    private int parsePositiveInt(String value, String definitionId) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(definitionId + " has invalid goal-amount value " + value, exception);
        }
    }
}
