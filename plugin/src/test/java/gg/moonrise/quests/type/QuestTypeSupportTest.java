package gg.moonrise.quests.type;

import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.sdk.model.QuestTypes;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class QuestTypeSupportTest {

    @Test
    public void goalOnlyHandlersRejectMissingExtraAndNonPositiveGoalAmounts() {
        TravelDistanceGoalHandler handler = new TravelDistanceGoalHandler(mock(QuestService.class), mock(QuestResetService.class));

        assertMessage("walk is missing variable goal-amount", () -> handler.validateDefinition(definition("walk", QuestTypes.TRAVEL_DISTANCE, Map.of())));
        assertMessage("walk has no goal-amount values", () -> handler.validateDefinition(definition("walk", QuestTypes.TRAVEL_DISTANCE, Map.of(
                "goal-amount", List.of()
        ))));
        assertMessage("walk does not support variable world", () -> handler.validateDefinition(definition("walk", QuestTypes.TRAVEL_DISTANCE, Map.of(
                "goal-amount", List.of("5"),
                "world", List.of("world")
        ))));
        assertMessage("walk has non-positive goal-amount value 0", () -> handler.validateDefinition(definition("walk", QuestTypes.TRAVEL_DISTANCE, Map.of(
                "goal-amount", List.of("0")
        ))));
        assertMessage("walk has invalid goal-amount value lots", () -> handler.validateDefinition(definition("walk", QuestTypes.TRAVEL_DISTANCE, Map.of(
                "goal-amount", List.of("lots")
        ))));
    }

    @Test
    public void goalOnlyHandlerCreatesQuestWithGoalAmount() {
        KillAllMobsGoalHandler handler = new KillAllMobsGoalHandler(mock(QuestService.class), mock(QuestResetService.class));
        QuestDefinition definition = definition("slayer", QuestTypes.KILL_ALL_MOBS, Map.of("goal-amount", List.of("12")));
        UUID playerId = UUID.randomUUID();

        handler.validateDefinition(definition);
        GeneratedQuest quest = handler.createGeneratedQuest(definition, playerId, "daily", Map.of("goal-amount", "12"));

        assertEquals(12, quest.goalAmount());
        assertEquals(0, quest.progress());
        assertEquals(playerId, quest.playerId());
        assertEquals("slayer", quest.definitionId());
    }

    @Test
    public void killMobValidatesMobTypesBeforeGoalAmounts() {
        KillMobGoalHandler handler = new KillMobGoalHandler(mock(QuestService.class), mock(QuestResetService.class));

        assertMessage("hunt is missing variable mob-type", () -> handler.validateDefinition(definition("hunt", QuestTypes.KILL_MOB, Map.of(
                "goal-amount", List.of("5")
        ))));
        assertMessage("hunt has invalid mob-type value ARROW", () -> handler.validateDefinition(definition("hunt", QuestTypes.KILL_MOB, Map.of(
                "mob-type", List.of("zombie", "ARROW"),
                "goal-amount", List.of("0")
        ))));
        assertMessage("hunt has non-positive goal-amount value -1", () -> handler.validateDefinition(definition("hunt", QuestTypes.KILL_MOB, Map.of(
                "mob-type", List.of("zombie"),
                "goal-amount", List.of("-1")
        ))));
    }

    @Test
    public void killMobCreatesQuestAndMobPlaceholders() {
        KillMobGoalHandler handler = new KillMobGoalHandler(mock(QuestService.class), mock(QuestResetService.class));
        QuestDefinition definition = definition("hunt", QuestTypes.KILL_MOB, Map.of(
                "mob-type", List.of("zombie"),
                "goal-amount", List.of("3")
        ));

        GeneratedQuest quest = handler.createGeneratedQuest(definition, UUID.randomUUID(), "daily", Map.of(
                "mob-type", "zombie",
                "goal-amount", "3"
        ));
        Map<String, String> placeholders = handler.variablePlaceholders(quest);

        assertEquals("zombie", quest.variables().get("mob-type"));
        assertEquals(3, quest.goalAmount());
        assertEquals("zombie", placeholders.get("mob-type-raw"));
        assertEquals("zombie", placeholders.get("mob_type_raw"));
        assertEquals(placeholders.get("mob-type"), placeholders.get("mob_type"));
        assertEquals("3", placeholders.get("goal_amount"));
        assertMessage("hunt generated invalid mob-type dragonish", () -> handler.createGeneratedQuest(definition, UUID.randomUUID(), "daily", Map.of(
                "mob-type", "dragonish",
                "goal-amount", "3"
        )));
    }

    @Test
    public void killMobInWorldTrimsWorldAndAddsWorldPlaceholders() {
        KillMobInWorldGoalHandler handler = new KillMobInWorldGoalHandler(mock(QuestService.class), mock(QuestResetService.class));
        QuestDefinition definition = definition("nether-hunt", QuestTypes.KILL_MOB_IN_WORLD, Map.of(
                "mob-type", List.of("blaze"),
                "world", List.of("world_nether"),
                "goal-amount", List.of("4")
        ));

        GeneratedQuest quest = handler.createGeneratedQuest(definition, UUID.randomUUID(), "daily", Map.of(
                "mob-type", "blaze",
                "world", " world_nether ",
                "goal-amount", "4"
        ));
        Map<String, String> placeholders = handler.variablePlaceholders(quest);

        assertEquals("world_nether", quest.variables().get("world"));
        assertEquals(4, quest.goalAmount());
        assertEquals("world_nether", placeholders.get("world_name"));
        assertEquals("blaze", placeholders.get("mob-type-raw"));
        assertMessage("nether-hunt is missing variable world", () -> handler.validateDefinition(definition("nether-hunt", QuestTypes.KILL_MOB_IN_WORLD, Map.of(
                "mob-type", List.of("blaze"),
                "goal-amount", List.of("4")
        ))));
        assertMessage("nether-hunt generated blank world", () -> handler.createGeneratedQuest(definition, UUID.randomUUID(), "daily", Map.of(
                "mob-type", "blaze",
                "world", " ",
                "goal-amount", "4"
        )));
    }

    @Test
    public void parseEntityTypeIsCaseInsensitiveAndNullSafe() {
        assertEquals(Optional.of(EntityType.ZOMBIE), QuestTypeSupport.parseEntityType(" zombie "));
        assertTrue(QuestTypeSupport.parseEntityType("not-a-mob").isEmpty());
        assertTrue(QuestTypeSupport.parseEntityType(" ").isEmpty());
        assertTrue(QuestTypeSupport.parseEntityType(null).isEmpty());
    }

    private static void assertMessage(String expected, Runnable action) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action::run);
        assertEquals(expected, exception.getMessage());
    }

    private static QuestDefinition definition(String id, QuestType type, Map<String, List<String>> selectorValues) {
        return new QuestDefinition(
                id,
                type,
                true,
                "easy",
                "Easy",
                "Quest",
                List.of(),
                Map.of(),
                selectorValues,
                null,
                List.of()
        );
    }
}
