package gg.moonrise.quests.integration;

import gg.moonrise.moss.spring.Disableable;
import gg.moonrise.moss.spring.Enableable;
import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class RoseStackerIntegrationRegistrar implements Enableable, Disableable {

    private final QuestsPlusPlugin plugin;
    private final QuestService questService;
    private final QuestResetService resetService;

    private Listener listener;

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("RoseStacker")) {
            return;
        }

        listener = new RoseStackerMobKillListener(questService, resetService);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        log.info("Registered RoseStacker stacked mob kill quest integration.");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }
}
