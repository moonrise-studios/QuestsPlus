package gg.moonrise.quests.indicator;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.config.Config;

import java.util.UUID;

@SpringComponent
public class ActionBarQuestProgressIndicator implements QuestProgressIndicator {

    private static final String TYPE = "ACTION_BAR";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public boolean isEnabled(Config.ProgressIndicators config) {
        return settings(config).isEnabled();
    }

    @Override
    public String titleTemplate(Config.ProgressIndicators config) {
        return settings(config).getTitle();
    }

    @Override
    public void show(QuestProgressIndicatorContext context) {
        var player = context.player();
        Scheduler.entity(player).run(task -> {
            if (player.isOnline()) {
                player.sendActionBar(context.title());
            }
        });
    }

    @Override
    public void clear(UUID playerId) {
        // ActionBars keep no per-player state.
    }

    @Override
    public void clearAll() {
        // ActionBars expire client-side.
    }

    private Config.ActionBarProgressIndicator settings(Config.ProgressIndicators config) {
        return config.getActionBar() == null ? new Config.ActionBarProgressIndicator() : config.getActionBar();
    }
}
