package gg.moonrise.quests.type;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import gg.moonrise.quests.sdk.GoalHandler;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.model.QuestTypes;
import gg.moonrise.quests.util.QuestDisplayNames;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemType;
import org.springframework.context.annotation.Lazy;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@SpringComponent
public class BrewItemGoalHandler implements GoalHandler {

    public static final String ITEM_TYPE = "item-type";
    public static final String GOAL_AMOUNT = "goal-amount";

    private final QuestService questService;
    private final QuestResetService resetService;
    private final BrewingStandOutputTracker outputTracker;

    public BrewItemGoalHandler(@Lazy QuestService questService, @Lazy QuestResetService resetService, BrewingStandOutputTracker outputTracker) {
        this.questService = questService;
        this.resetService = resetService;
        this.outputTracker = outputTracker;
    }

    @Override
    public QuestType type() {
        return QuestTypes.BREW_ITEM;
    }

    @Override
    public void validateDefinition(QuestDefinition definition) {
        if (!definition.selectorValues().containsKey(ITEM_TYPE)) {
            throw new IllegalArgumentException(definition.id() + " is missing variable " + ITEM_TYPE);
        }
        if (!definition.selectorValues().containsKey(GOAL_AMOUNT)) {
            throw new IllegalArgumentException(definition.id() + " is missing variable " + GOAL_AMOUNT);
        }

        for (String value : definition.selectorValues().get(ITEM_TYPE)) {
            if (parseItemType(value) == null) {
                throw new IllegalArgumentException(definition.id() + " has invalid item-type value " + value);
            }
        }
        for (String value : definition.selectorValues().get(GOAL_AMOUNT)) {
            int amount = parsePositiveInt(value, definition.id());
            if (amount <= 0) {
                throw new IllegalArgumentException(definition.id() + " has non-positive goal-amount value " + value);
            }
        }
    }

    @Override
    public GeneratedQuest createGeneratedQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
        ItemType itemType = parseItemType(variables.get(ITEM_TYPE));
        if (itemType == null) {
            throw new IllegalArgumentException(definition.id() + " generated invalid item-type " + variables.get(ITEM_TYPE));
        }

        Map<String, String> canonicalVariables = new LinkedHashMap<>(variables);
        canonicalVariables.put(ITEM_TYPE, itemType.getKey().toString());

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
                Map.copyOf(canonicalVariables),
                goalAmount,
                0,
                false
        );
    }

    @Override
    public Map<String, String> variablePlaceholders(GeneratedQuest quest) {
        Map<String, String> values = GoalHandler.super.variablePlaceholders(quest);
        String itemType = quest.variables().get(ITEM_TYPE);
        if (itemType != null) {
            String displayName = QuestDisplayNames.typeName(itemType, quest.goalAmount());
            values.put(ITEM_TYPE, displayName);
            values.put("item_type", displayName);
            values.put("item-type-key", itemType);
            values.put("item_type_key", itemType);
        }
        return values;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewComplete(BrewEvent event) {
        outputTracker.recordBrew(event, event.getResults());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) {
            return;
        }

        outputTracker.brewClick(event).ifPresent(click -> Scheduler.entity(player).run(task -> {
            BrewingStandOutputTracker.BrewedTake take = outputTracker.consume(event, click);
            if (!take.present()) {
                return;
            }

            questService.progressMatching(player, type(), resetService.currentResetKey(), take.amount(), quest ->
                    take.itemKey().equals(quest.variables().get(ITEM_TYPE))
            );
        }));
    }

    private int parsePositiveInt(String value, String definitionId) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(definitionId + " has invalid goal-amount value " + value, exception);
        }
    }

    private ItemType parseItemType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.ITEM)
                    .get(normalizeKey(value));
        } catch (InvalidKeyException exception) {
            return null;
        }
    }

    private Key normalizeKey(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        return Key.key(normalized);
    }
}
