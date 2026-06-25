package gg.moonrise.quests.type;

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
import org.bukkit.block.BlockType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.springframework.context.annotation.Lazy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@SpringComponent
public class BlockBreakGoalHandler implements GoalHandler {

    public static final String BLOCK_TYPE = "block-type";
    public static final String GOAL_AMOUNT = "goal-amount";

    private final QuestService questService;
    private final QuestResetService resetService;

    public BlockBreakGoalHandler(@Lazy QuestService questService, @Lazy QuestResetService resetService) {
        this.questService = questService;
        this.resetService = resetService;
    }

    @Override
    public QuestType type() {
        return QuestTypes.BLOCK_BREAK;
    }

    @Override
    public void validateDefinition(QuestDefinition definition) {
        if (!definition.selectorValues().containsKey(BLOCK_TYPE)) {
            throw new IllegalArgumentException(definition.id() + " is missing variable " + BLOCK_TYPE);
        }
        if (!definition.selectorValues().containsKey(GOAL_AMOUNT)) {
            throw new IllegalArgumentException(definition.id() + " is missing variable " + GOAL_AMOUNT);
        }

        for (String value : definition.selectorValues().get(BLOCK_TYPE)) {
            if (parseBlockType(value) == null) {
                throw new IllegalArgumentException(definition.id() + " has invalid block-type value " + value);
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
        BlockType blockType = parseBlockType(variables.get(BLOCK_TYPE));
        if (blockType == null) {
            throw new IllegalArgumentException(definition.id() + " generated invalid block-type " + variables.get(BLOCK_TYPE));
        }

        Map<String, String> canonicalVariables = new LinkedHashMap<>(variables);
        canonicalVariables.put(BLOCK_TYPE, blockType.getKey().toString());

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
        String blockType = quest.variables().get(BLOCK_TYPE);
        if (blockType != null) {
            String displayName = QuestDisplayNames.typeName(blockType);
            values.put(BLOCK_TYPE, displayName);
            values.put("block_type", displayName);
            values.put("block-type-key", blockType);
            values.put("block_type_key", blockType);
        }
        return values;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String brokenBlockKey = event.getBlock().getType().asBlockType().getKey().toString();
        questService.progressMatching(player, type(), resetService.currentResetKey(), quest ->
                brokenBlockKey.equals(quest.variables().get(BLOCK_TYPE))
        );
    }

    private int parsePositiveInt(String value, String definitionId) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(definitionId + " has invalid goal-amount value " + value, exception);
        }
    }

    private BlockType parseBlockType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.BLOCK)
                    .get(normalizeKey(value));
        } catch (InvalidKeyException exception) {
            return null;
        }
    }

    private Key normalizeKey(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        return Key.key(normalized);
    }
}
