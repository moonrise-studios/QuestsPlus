package gg.moonrise.quests.listener;

import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.core.service.QuestIndicatorPreferenceService;
import gg.moonrise.quests.core.service.QuestProgressIndicatorService;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class PlayerQuestLoadListener implements Listener {

    private final QuestService questService;
    private final QuestResetService resetService;
    private final QuestIndicatorPreferenceService indicatorPreferenceService;
    private final QuestProgressIndicatorService progressIndicatorService;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        questService.ensurePlayerStateAsync(event.getPlayer(), resetService.currentResetKey())
                .exceptionally(throwable -> {
                    log.error("Failed to load QuestsPlus quest state for {}.", playerId, throwable);
                    return null;
                });
        indicatorPreferenceService.loadPreference(playerId)
                .exceptionally(throwable -> {
                    log.error("Failed to load QuestsPlus indicator preference for {}.", playerId, throwable);
                    return new QuestIndicatorPreferenceService.Preferences(null, null);
                });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        progressIndicatorService.clearPlayer(playerId);
        indicatorPreferenceService.evict(playerId);
    }
}
