package gg.moonrise.quests.integration;

import dev.rosewood.rosestacker.event.EntityStackMultipleDeathEvent;
import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import gg.moonrise.quests.sdk.model.QuestTypes;
import gg.moonrise.quests.type.KillMobGoalHandler;
import gg.moonrise.quests.type.KillMobInWorldGoalHandler;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class RoseStackerMobKillListener implements Listener {

    private final QuestService questService;
    private final QuestResetService resetService;

    RoseStackerMobKillListener(QuestService questService, QuestResetService resetService) {
        this.questService = questService;
        this.resetService = resetService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStackMultipleDeath(EntityStackMultipleDeathEvent event) {
        Player killer = event.getKiller();
        int additionalKills = additionalKillCount(event.getEntityKillCount());
        if (killer == null || additionalKills <= 0 || event.getMainEntity() instanceof Player) {
            return;
        }

        EntityType killedType = event.getMainEntity().getType();
        World killedWorld = event.getMainEntity().getWorld();
        Runnable progress = () -> progressStackedKills(killer, killedType, killedWorld.getName(), additionalKills);
        if (event.isAsynchronous()) {
            Scheduler.entity(killer).run(task -> progress.run());
            return;
        }
        progress.run();
    }

    int additionalKillCount(int roseStackerKillCount) {
        return Math.max(0, roseStackerKillCount - 1);
    }

    void progressStackedKills(Player killer, EntityType killedType, String killedWorldName, int amount) {
        String resetKey = resetService.currentResetKey();
        questService.progressMatching(killer, QuestTypes.KILL_MOB, resetKey, amount, quest ->
                killedType.name().equalsIgnoreCase(quest.variables().get(KillMobGoalHandler.MOB_TYPE))
        );
        questService.progressMatching(killer, QuestTypes.KILL_MOB_IN_WORLD, resetKey, amount, quest ->
                killedType.name().equalsIgnoreCase(quest.variables().get(KillMobInWorldGoalHandler.MOB_TYPE))
                        && killedWorldName.equals(quest.variables().get(KillMobInWorldGoalHandler.WORLD))
        );
        questService.progressMatching(killer, QuestTypes.KILL_ALL_MOBS, resetKey, amount, quest -> true);
    }
}
