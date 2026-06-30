package gg.moonrise.quests.integration;

import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestTypes;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoseStackerMobKillListenerTest {

    @Test
    public void additionalKillCountExcludesVisibleEntityHandledByBukkitDeathEvent() {
        RoseStackerMobKillListener listener = new RoseStackerMobKillListener(
                mock(QuestService.class),
                mock(QuestResetService.class)
        );

        assertEquals(0, listener.additionalKillCount(0));
        assertEquals(0, listener.additionalKillCount(1));
        assertEquals(1, listener.additionalKillCount(2));
        assertEquals(24, listener.additionalKillCount(25));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void stackedKillsProgressMatchingMobQuestTypesWithExtraCount() {
        QuestService questService = mock(QuestService.class);
        QuestResetService resetService = mock(QuestResetService.class);
        Player player = mock(Player.class);
        RoseStackerMobKillListener listener = new RoseStackerMobKillListener(questService, resetService);
        ArgumentCaptor<Predicate<GeneratedQuest>> killMobMatcher = ArgumentCaptor.forClass(Predicate.class);
        ArgumentCaptor<Predicate<GeneratedQuest>> killMobInWorldMatcher = ArgumentCaptor.forClass(Predicate.class);
        ArgumentCaptor<Predicate<GeneratedQuest>> killAllMobsMatcher = ArgumentCaptor.forClass(Predicate.class);

        when(resetService.currentResetKey()).thenReturn("daily");

        listener.progressStackedKills(player, EntityType.ZOMBIE, "world", 24);

        verify(questService).progressMatching(eq(player), eq(QuestTypes.KILL_MOB), eq("daily"), eq(24), killMobMatcher.capture());
        verify(questService).progressMatching(eq(player), eq(QuestTypes.KILL_MOB_IN_WORLD), eq("daily"), eq(24), killMobInWorldMatcher.capture());
        verify(questService).progressMatching(eq(player), eq(QuestTypes.KILL_ALL_MOBS), eq("daily"), eq(24), killAllMobsMatcher.capture());

        assertTrue(killMobMatcher.getValue().test(quest(Map.of("mob-type", "ZOMBIE"))));
        assertFalse(killMobMatcher.getValue().test(quest(Map.of("mob-type", "SKELETON"))));
        assertTrue(killMobInWorldMatcher.getValue().test(quest(Map.of("mob-type", "ZOMBIE", "world", "world"))));
        assertFalse(killMobInWorldMatcher.getValue().test(quest(Map.of("mob-type", "ZOMBIE", "world", "world_nether"))));
        assertTrue(killAllMobsMatcher.getValue().test(quest(Map.of())));
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
