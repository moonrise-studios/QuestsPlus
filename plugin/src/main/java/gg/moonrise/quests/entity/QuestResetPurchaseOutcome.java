package gg.moonrise.quests.entity;

/**
 * Result of recording a paid quest reset and deleting the player's quests for the window.
 */
public sealed interface QuestResetPurchaseOutcome {

    /**
     * The purchase was counted and the quests were deleted; {@code used} is the updated reset count.
     */
    record Applied(int used) implements QuestResetPurchaseOutcome {
    }

    /**
     * The player had no resets left for the window. Nothing was changed.
     */
    record LimitReached() implements QuestResetPurchaseOutcome {
    }
}
