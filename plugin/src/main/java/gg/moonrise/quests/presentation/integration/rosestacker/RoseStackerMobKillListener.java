package gg.moonrise.quests.presentation.integration.rosestacker;

import dev.rosewood.rosestacker.event.EntityStackMultipleDeathEvent;
import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.quests.application.quest.QuestProgressService;
import gg.moonrise.quests.application.reset.QuestResetService;
import gg.moonrise.quests.sdk.model.QuestTypes;
import gg.moonrise.quests.presentation.goal.KillAllMobsGoalHandler;
import gg.moonrise.quests.presentation.goal.KillMobGoalHandler;
import gg.moonrise.quests.presentation.goal.KillMobInWorldGoalHandler;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class RoseStackerMobKillListener implements Listener {

    private final QuestProgressService progressService;
    private final QuestResetService resetService;

    public RoseStackerMobKillListener(QuestProgressService progressService, QuestResetService resetService) {
        this.progressService = progressService;
        this.resetService = resetService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStackMultipleDeath(EntityStackMultipleDeathEvent event) {
        Player killer = event.getKiller();
        int additionalKills = additionalKillCount(event.getEntityKillCount());
        if (killer == null || additionalKills <= 0) {
            return;
        }
        if (event.isAsynchronous()) {
            Scheduler.entity(killer).run(task -> progressStackedEvent(killer, event, additionalKills));
            return;
        }
        progressStackedEvent(killer, event, additionalKills);
    }

    public int additionalKillCount(int roseStackerKillCount) {
        return Math.max(0, roseStackerKillCount - 1);
    }

    private void progressStackedEvent(Player killer, EntityStackMultipleDeathEvent event, int additionalKills) {
        if (event.getMainEntity() instanceof Player) {
            return;
        }

        EntityType killedType = event.getMainEntity().getType();
        World killedWorld = event.getMainEntity().getWorld();
        progressStackedKills(killer, killedType, killedWorld.getName(), additionalKills);
    }

    public void progressStackedKills(Player killer, EntityType killedType, String killedWorldName, int amount) {
        String resetKey = resetService.currentResetKey();
        progressService.progressMatching(killer, QuestTypes.KILL_MOB, resetKey, amount, KillMobGoalHandler.killMatcher(killedType));
        progressService.progressMatching(killer, QuestTypes.KILL_MOB_IN_WORLD, resetKey, amount, KillMobInWorldGoalHandler.killMatcher(killedType, killedWorldName));
        progressService.progressMatching(killer, QuestTypes.KILL_ALL_MOBS, resetKey, amount, KillAllMobsGoalHandler.killMatcher());
    }
}
