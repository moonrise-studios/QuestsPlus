package gg.moonrise.quests.indicator;

import java.util.UUID;

public interface QuestProgressIndicator {

    public String type();

    public void show(QuestProgressIndicatorContext context);

    /**
     * Drops any per-player state this indicator holds, for example when the player quits.
     *
     * @param playerId the player whose state is cleared
     */
    public void clear(UUID playerId);

    public void clearAll();
}
