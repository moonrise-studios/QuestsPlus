package gg.moonrise.quests.listener;

import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.core.service.QuestIndicatorPreferenceService;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class PlayerQuestLoadListener implements Listener {

    private final QuestService questService;
    private final QuestResetService resetService;
    private final QuestIndicatorPreferenceService indicatorPreferenceService;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        questService.ensurePlayerStateAsync(event.getPlayer(), resetService.currentResetKey());
        indicatorPreferenceService.loadPreference(event.getPlayer().getUniqueId())
                .exceptionally(throwable -> {
                    log.error("Failed to load QuestsPlus indicator preference for {}.", event.getPlayer().getUniqueId(), throwable);
                    return new QuestIndicatorPreferenceService.Preferences(null, null);
                });
    }
}
