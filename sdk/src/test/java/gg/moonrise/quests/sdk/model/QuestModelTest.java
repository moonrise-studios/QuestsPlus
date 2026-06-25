package gg.moonrise.quests.sdk.model;

import gg.moonrise.quests.sdk.util.QuestPlaceholders;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestModelTest {

    @Test
    void questTypeNormalizesKeysAndRejectsInvalidInput() {
        assertTrue(QuestType.class.isRecord());
        assertFalse(QuestType.class.isEnum());
        assertEquals("BREAK_BLOCKS", QuestType.normalize(" break-blocks "));
        assertEquals(QuestType.of("KILL MOBS"), QuestType.of("kill_mobs"));
        assertThrows(IllegalArgumentException.class, () -> QuestType.of(""));
        assertThrows(IllegalArgumentException.class, () -> QuestType.of("bad/key"));
    }

    @Test
    void questTypesExposeActivityBuiltIns() {
        assertEquals(QuestType.of("ENCHANT_ITEM"), QuestTypes.ENCHANT_ITEM);
        assertEquals(QuestType.of("PLACE_BLOCK"), QuestTypes.PLACE_BLOCK);
        assertEquals(QuestType.of("PLACE_ANY_BLOCK"), QuestTypes.PLACE_ANY_BLOCK);
        assertEquals(QuestType.of("HARVEST_ITEM"), QuestTypes.HARVEST_ITEM);
        assertEquals(QuestType.of("SHEAR_SHEEP"), QuestTypes.SHEAR_SHEEP);
        assertEquals(QuestType.of("FISH"), QuestTypes.FISH);
        assertEquals(QuestType.of("EAT_CAKE_SLICE"), QuestTypes.EAT_CAKE_SLICE);
        assertEquals(QuestType.of("CRAFT_ITEM"), QuestTypes.CRAFT_ITEM);
        assertEquals(QuestType.of("CRAFT_ANY_ITEM"), QuestTypes.CRAFT_ANY_ITEM);
        assertEquals(QuestType.of("DYE_SHEEP"), QuestTypes.DYE_SHEEP);
        assertEquals(QuestType.of("MILK_MOB"), QuestTypes.MILK_MOB);
        assertEquals(QuestType.of("THROW_ITEM"), QuestTypes.THROW_ITEM);
        assertEquals(QuestType.of("VILLAGER_TRADE"), QuestTypes.VILLAGER_TRADE);
    }

    @Test
    void placeholderKeysAreLowercaseAndUnderscored() {
        assertEquals("mob_type", QuestPlaceholders.placeholderKey(" Mob-Type "));
        assertEquals("", QuestPlaceholders.placeholderKey(null));
    }

    @Test
    void generatedQuestCopyMethodsPreserveUntouchedFields() {
        GeneratedQuest quest = new GeneratedQuest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "daily",
                "break-stone",
                QuestType.of("BREAK_BLOCK"),
                "easy",
                "Easy",
                "Break Stone",
                List.of("Break blocks"),
                Map.of("material", "STONE"),
                10,
                1,
                false
        );

        GeneratedQuest progressed = quest.withProgress(10, true);
        GeneratedQuest slotted = progressed.withSlotIndex(4);
        GeneratedQuest premium = slotted.withPremium(true);

        assertEquals(10, progressed.progress());
        assertTrue(progressed.completed());
        assertEquals(4, slotted.slotIndex());
        assertTrue(premium.premium());
        assertEquals(quest.instanceId(), premium.instanceId());
        assertEquals(quest.variables(), premium.variables());
    }
}
