package gg.moonrise.quests.core.service.indicator;

import gg.moonrise.quests.config.indicator.ProgressIndicators;

import java.util.UUID;

public interface QuestProgressIndicator {

    public String type();

    /**
     * @param config the progress indicator settings, never null
     * @return whether server owners enabled this indicator
     */
    public boolean isEnabled(ProgressIndicators config);

    /**
     * @param config the progress indicator settings, never null
     * @return the MiniMessage template rendered into {@link QuestProgressIndicatorContext#title()}
     */
    public String titleTemplate(ProgressIndicators config);

    public void show(QuestProgressIndicatorContext context);

    /**
     * Drops any per-player state this indicator holds, for example when the player quits.
     *
     * @param playerId the player whose state is cleared
     */
    public void clear(UUID playerId);

    public void clearAll();
}
