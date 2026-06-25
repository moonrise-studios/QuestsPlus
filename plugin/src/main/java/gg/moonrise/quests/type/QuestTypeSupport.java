package gg.moonrise.quests.type;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.util.QuestPlaceholders;
import gg.moonrise.quests.util.QuestDisplayNames;
import org.bukkit.Material;
import org.bukkit.block.BlockType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class QuestTypeSupport {

    static final String BLOCK_TYPE = "block-type";
    static final String COLOR = "color";
    static final String GOAL_AMOUNT = "goal-amount";
    static final String ITEM_TYPE = "item-type";

    private QuestTypeSupport() {
    }

    static void validateOnlyGoalAmount(QuestDefinition definition) {
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
        validateGoalAmounts(definition);
    }

    static void validateBlockTypeAndGoalAmount(QuestDefinition definition) {
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
        validateGoalAmounts(definition);
    }

    static void validateItemTypeAndGoalAmount(QuestDefinition definition) {
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
        validateGoalAmounts(definition);
    }

    static GeneratedQuest createGoalAmountQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
        int goalAmount = parsePositiveInt(variables.get(GOAL_AMOUNT), definition.id());
        return generatedQuest(definition, playerId, resetKey, Map.copyOf(variables), goalAmount);
    }

    static GeneratedQuest createBlockTypeQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
        BlockType blockType = parseBlockType(variables.get(BLOCK_TYPE));
        if (blockType == null) {
            throw new IllegalArgumentException(definition.id() + " generated invalid block-type " + variables.get(BLOCK_TYPE));
        }

        Map<String, String> canonicalVariables = new LinkedHashMap<>(variables);
        canonicalVariables.put(BLOCK_TYPE, blockType.getKey().toString());
        int goalAmount = parsePositiveInt(variables.get(GOAL_AMOUNT), definition.id());
        return generatedQuest(definition, playerId, resetKey, Map.copyOf(canonicalVariables), goalAmount);
    }

    static GeneratedQuest createItemTypeQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables) {
        ItemType itemType = parseItemType(variables.get(ITEM_TYPE));
        if (itemType == null) {
            throw new IllegalArgumentException(definition.id() + " generated invalid item-type " + variables.get(ITEM_TYPE));
        }

        Map<String, String> canonicalVariables = new LinkedHashMap<>(variables);
        canonicalVariables.put(ITEM_TYPE, itemType.getKey().toString());
        int goalAmount = parsePositiveInt(variables.get(GOAL_AMOUNT), definition.id());
        return generatedQuest(definition, playerId, resetKey, Map.copyOf(canonicalVariables), goalAmount);
    }

    static Map<String, String> blockTypePlaceholders(GeneratedQuest quest) {
        Map<String, String> values = variablePlaceholders(quest);
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

    static Map<String, String> itemTypePlaceholders(GeneratedQuest quest) {
        Map<String, String> values = variablePlaceholders(quest);
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

    static int parsePositiveInt(String value, String definitionId) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(definitionId + " has invalid goal-amount value " + value, exception);
        }
    }

    static BlockType parseBlockType(String value) {
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

    static ItemType parseItemType(String value) {
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

    static String blockTypeKey(Material material) {
        if (material == null || !material.isBlock()) {
            return null;
        }
        BlockType blockType = material.asBlockType();
        return blockType == null ? null : blockType.getKey().toString();
    }

    static String itemTypeKey(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        return itemTypeKey(itemStack.getType());
    }

    static String itemTypeKey(Material material) {
        if (material == null || !material.isItem()) {
            return null;
        }
        ItemType itemType = material.asItemType();
        return itemType == null ? null : itemType.getKey().toString();
    }

    private static void validateGoalAmounts(QuestDefinition definition) {
        for (String value : definition.selectorValues().get(GOAL_AMOUNT)) {
            int amount = parsePositiveInt(value, definition.id());
            if (amount <= 0) {
                throw new IllegalArgumentException(definition.id() + " has non-positive goal-amount value " + value);
            }
        }
    }

    private static Map<String, String> variablePlaceholders(GeneratedQuest quest) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : quest.variables().entrySet()) {
            values.put(entry.getKey(), entry.getValue());
            values.put(QuestPlaceholders.placeholderKey(entry.getKey()), entry.getValue());
        }
        return values;
    }

    private static GeneratedQuest generatedQuest(QuestDefinition definition, UUID playerId, String resetKey, Map<String, String> variables, int goalAmount) {
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
                variables,
                goalAmount,
                0,
                false
        );
    }

    private static Key normalizeKey(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        return Key.key(normalized);
    }
}
