package gg.moonrise.quests.sdk.event;

import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Called before QuestsPlus applies progress to a generated quest.
 *
 * <p>Listeners may cancel this event to prevent the progress operation, or
 * change the amount before QuestsPlus updates cache, persists progress, runs
 * rewards, records global contribution, or evaluates streaks and milestones.
 * A final amount less than or equal to zero is treated as no progress.</p>
 */
public class QuestProgressEvent extends QuestEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int originalAmount;
    private int amount;
    private boolean cancelled;

    /**
     * Creates a quest progress event.
     *
     * @param player the player receiving progress
     * @param definition the source quest definition
     * @param quest the generated quest instance before progress is applied
     * @param cause the cause that triggered the progress
     * @param amount the requested progress amount
     */
    public QuestProgressEvent(Player player, QuestDefinition definition, GeneratedQuest quest, Cause cause, int amount) {
        super(player, definition, quest, cause);
        this.originalAmount = amount;
        this.amount = amount;
    }

    /**
     * Returns the amount requested by the original progress source.
     *
     * @return the original progress amount
     */
    public int originalAmount() {
        return originalAmount;
    }

    /**
     * Returns the amount that QuestsPlus should apply if the event is not cancelled.
     *
     * @return the current progress amount
     */
    public int amount() {
        return amount;
    }

    /**
     * Changes the amount that QuestsPlus should apply.
     *
     * <p>Setting the amount to zero or a negative number causes QuestsPlus to
     * skip the progress operation for this generated quest.</p>
     *
     * @param amount the updated progress amount
     */
    public void amount(int amount) {
        this.amount = amount;
    }

    /**
     * Returns the amount that QuestsPlus should apply if the event is not cancelled.
     *
     * @return the current progress amount
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Changes the amount that QuestsPlus should apply.
     *
     * @param amount the updated progress amount
     */
    public void setAmount(int amount) {
        amount(amount);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the Bukkit handler list for this event.
     *
     * @return the Bukkit handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Describes the source that requested quest progress.
     */
    public enum Cause {
        /**
         * Progress came from a Bukkit event handled by a goal handler.
         */
        EVENT,
        /**
         * Progress came from an external integration through {@code QuestApi}.
         */
        API,
        /**
         * Progress came from an QuestsPlus admin command.
         */
        COMMAND,
        /**
         * Progress came from an unspecified or fallback source.
         */
        UNKNOWN
    }
}
