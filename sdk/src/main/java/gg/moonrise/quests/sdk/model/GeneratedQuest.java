package gg.moonrise.quests.sdk.model;

import java.util.Map;
import java.util.UUID;

/**
 * A generated quest instance with resolved variables and progress.
 *
 * @param instanceId unique quest instance id
 * @param playerId owner player id, or QuestsPlus' global placeholder id for global quests
 * @param resetKey personal reset key or global period key
 * @param definitionId source quest definition id
 * @param type quest type key
 * @param difficultyId difficulty id captured at generation time
 * @param difficultyDisplayName difficulty display captured at generation time
 * @param displayName quest display captured at generation time
 * @param description quest description captured at generation time
 * @param variables resolved raw variable values
 * @param slotIndex daily menu slot index, or -1 when not slot-bound
 * @param premium whether this personal quest was generated in a premium slot
 * @param goalAmount resolved goal amount
 * @param progress current progress
 * @param completed whether the quest is complete
 */
public record GeneratedQuest(
        UUID instanceId,
        UUID playerId,
        String resetKey,
        String definitionId,
        QuestType type,
        String difficultyId,
        String difficultyDisplayName,
        String displayName,
        java.util.List<String> description,
        Map<String, String> variables,
        int slotIndex,
        boolean premium,
        int goalAmount,
        int progress,
        boolean completed
) {

    /**
     * Creates a generated quest without a slot index.
     *
     * @param instanceId unique quest instance id
     * @param playerId owner player id
     * @param resetKey reset or period key
     * @param definitionId source definition id
     * @param type quest type key
     * @param difficultyId difficulty id
     * @param difficultyDisplayName difficulty display name
     * @param displayName quest display name
     * @param description quest description
     * @param variables resolved variables
     * @param goalAmount goal amount
     * @param progress current progress
     * @param completed completion state
     */
    public GeneratedQuest(
            UUID instanceId,
            UUID playerId,
            String resetKey,
            String definitionId,
            QuestType type,
            String difficultyId,
            String difficultyDisplayName,
            String displayName,
            java.util.List<String> description,
            Map<String, String> variables,
            int goalAmount,
            int progress,
            boolean completed
    ) {
        this(
                instanceId,
                playerId,
                resetKey,
                definitionId,
                type,
                difficultyId,
                difficultyDisplayName,
                displayName,
                description,
                variables,
                -1,
                false,
                goalAmount,
                progress,
                completed
        );
    }

    /**
     * Creates a generated quest with a slot index and without premium status.
     *
     * @param instanceId unique quest instance id
     * @param playerId owner player id
     * @param resetKey reset or period key
     * @param definitionId source definition id
     * @param type quest type key
     * @param difficultyId difficulty id
     * @param difficultyDisplayName difficulty display name
     * @param displayName quest display name
     * @param description quest description
     * @param variables resolved variables
     * @param slotIndex slot index
     * @param goalAmount goal amount
     * @param progress current progress
     * @param completed completion state
     */
    public GeneratedQuest(
            UUID instanceId,
            UUID playerId,
            String resetKey,
            String definitionId,
            QuestType type,
            String difficultyId,
            String difficultyDisplayName,
            String displayName,
            java.util.List<String> description,
            Map<String, String> variables,
            int slotIndex,
            int goalAmount,
            int progress,
            boolean completed
    ) {
        this(
                instanceId,
                playerId,
                resetKey,
                definitionId,
                type,
                difficultyId,
                difficultyDisplayName,
                displayName,
                description,
                variables,
                slotIndex,
                false,
                goalAmount,
                progress,
                completed
        );
    }

    /**
     * Returns a copy with updated progress.
     *
     * @param updatedProgress updated progress
     * @param updatedCompleted updated completion state
     * @return updated quest copy
     */
    public GeneratedQuest withProgress(int updatedProgress, boolean updatedCompleted) {
        return new GeneratedQuest(
                instanceId,
                playerId,
                resetKey,
                definitionId,
                type,
                difficultyId,
                difficultyDisplayName,
                displayName,
                description,
                variables,
                slotIndex,
                premium,
                goalAmount,
                updatedProgress,
                updatedCompleted
        );
    }

    /**
     * Returns a copy with a daily slot index.
     *
     * @param updatedSlotIndex updated slot index
     * @return updated quest copy
     */
    public GeneratedQuest withSlotIndex(int updatedSlotIndex) {
        return new GeneratedQuest(
                instanceId,
                playerId,
                resetKey,
                definitionId,
                type,
                difficultyId,
                difficultyDisplayName,
                displayName,
                description,
                variables,
                updatedSlotIndex,
                premium,
                goalAmount,
                progress,
                completed
        );
    }

    /**
     * Returns a copy with updated premium status.
     *
     * @param updatedPremium updated premium flag
     * @return updated quest copy
     */
    public GeneratedQuest withPremium(boolean updatedPremium) {
        return new GeneratedQuest(
                instanceId,
                playerId,
                resetKey,
                definitionId,
                type,
                difficultyId,
                difficultyDisplayName,
                displayName,
                description,
                variables,
                slotIndex,
                updatedPremium,
                goalAmount,
                progress,
                completed
        );
    }
}
