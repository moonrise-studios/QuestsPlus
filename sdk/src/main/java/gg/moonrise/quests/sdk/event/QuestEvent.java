package gg.moonrise.quests.sdk.event;

import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * Base event for QuestsPlus quest instance events.
 *
 * <p>Quest events always include the player receiving the event, the source
 * quest definition, the generated quest instance, and the cause that triggered
 * the event.</p>
 */
public abstract class QuestEvent extends Event {

    private final Player player;
    private final QuestDefinition definition;
    private final GeneratedQuest quest;
    private final QuestProgressEvent.Cause cause;

    /**
     * Creates a quest event using Bukkit's async marker based on the current thread.
     *
     * @param player the player associated with the quest event
     * @param definition the source quest definition
     * @param quest the generated quest instance
     * @param cause the cause that triggered the quest event
     */
    protected QuestEvent(Player player, QuestDefinition definition, GeneratedQuest quest, QuestProgressEvent.Cause cause) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.definition = definition;
        this.quest = quest;
        this.cause = cause;
    }

    /**
     * Returns the player associated with this quest event.
     *
     * @return the player associated with this quest event
     */
    public Player player() {
        return player;
    }

    /**
     * Returns the source quest definition.
     *
     * @return the source quest definition
     */
    public QuestDefinition definition() {
        return definition;
    }

    /**
     * Returns the generated quest instance.
     *
     * @return the generated quest instance
     */
    public GeneratedQuest quest() {
        return quest;
    }

    /**
     * Returns the cause that triggered this quest event.
     *
     * @return the cause that triggered this quest event
     */
    public QuestProgressEvent.Cause cause() {
        return cause;
    }
}
