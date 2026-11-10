package gg.moonrise.quests.type;

import gg.moonrise.quests.application.quest.QuestProgressService;
import gg.moonrise.quests.application.reset.QuestResetService;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestTypes;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KillGoalMatcherTest {

    @Test
    public void killMobMatcherComparesMobTypeIgnoringCase() {
        Predicate<GeneratedQuest> matcher = KillMobGoalHandler.killMatcher(EntityType.ZOMBIE);

        assertTrue(matcher.test(quest(Map.of("mob-type", "zombie"))));
        assertFalse(matcher.test(quest(Map.of("mob-type", "SKELETON"))));
        assertFalse(matcher.test(quest(Map.of())));
    }

    @Test
    public void killMobInWorldMatcherRequiresMobTypeAndWorld() {
        Predicate<GeneratedQuest> matcher = KillMobInWorldGoalHandler.killMatcher(EntityType.BLAZE, "world_nether");

        assertTrue(matcher.test(quest(Map.of("mob-type", "BLAZE", "world", "world_nether"))));
        assertFalse(matcher.test(quest(Map.of("mob-type", "BLAZE", "world", "world"))));
        assertFalse(matcher.test(quest(Map.of("mob-type", "ZOMBIE", "world", "world_nether"))));
    }

    @Test
    public void killAllMobsMatcherMatchesEveryQuest() {
        assertTrue(KillAllMobsGoalHandler.killMatcher().test(quest(Map.of())));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void killMobInWorldDeathEventUsesSharedMatcher() {
        QuestProgressService progressService = mock(QuestProgressService.class);
        QuestResetService resetService = mock(QuestResetService.class);
        KillMobInWorldGoalHandler handler = new KillMobInWorldGoalHandler(progressService, resetService);
        Player killer = mock(Player.class);
        World world = mock(World.class);
        LivingEntity entity = mock(LivingEntity.class);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        ArgumentCaptor<Predicate<GeneratedQuest>> matcher = ArgumentCaptor.forClass(Predicate.class);

        when(resetService.currentResetKey()).thenReturn("daily");
        when(world.getName()).thenReturn("world_nether");
        when(entity.getKiller()).thenReturn(killer);
        when(entity.getWorld()).thenReturn(world);
        when(event.getEntity()).thenReturn(entity);
        when(event.getEntityType()).thenReturn(EntityType.BLAZE);

        handler.onMobDeath(event);

        verify(progressService).progressMatching(eq(killer), eq(QuestTypes.KILL_MOB_IN_WORLD), eq("daily"), matcher.capture());
        assertTrue(matcher.getValue().test(quest(Map.of("mob-type", "blaze", "world", "world_nether"))));
        assertFalse(matcher.getValue().test(quest(Map.of("mob-type", "blaze", "world", "world"))));
    }

    private static GeneratedQuest quest(Map<String, String> variables) {
        return new GeneratedQuest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "daily",
                "definition",
                QuestTypes.KILL_MOB,
                "easy",
                "Easy",
                "Quest",
                List.of(),
                variables,
                25,
                0,
                false
        );
    }
}
