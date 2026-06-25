package gg.moonrise.quests.ui;

import gg.moonrise.quests.model.QuestDifficulty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DifficultySelectorSlotsTest {

    @Test
    void explicitPickerSlotOverridesFallbackOrder() {
        QuestDifficulty easy = difficulty("easy", -1, -1);
        QuestDifficulty medium = difficulty("medium", 15, -1);
        QuestDifficulty hard = difficulty("hard", -1, -1);

        Map<QuestDifficulty, Integer> slots = DifficultySelectorSlots.resolve(
                List.of(easy, medium, hard),
                QuestDifficulty::pickerSlot,
                List.of(10, 11, 12),
                27,
                Set.of()
        );

        assertEquals(10, slots.get(easy));
        assertEquals(15, slots.get(medium));
        assertEquals(11, slots.get(hard));
    }

    @Test
    void duplicateAndOutOfRangeSlotsUseFallbacks() {
        QuestDifficulty easy = difficulty("easy", 10, -1);
        QuestDifficulty medium = difficulty("medium", 10, -1);
        QuestDifficulty hard = difficulty("hard", 99, -1);

        Map<QuestDifficulty, Integer> slots = DifficultySelectorSlots.resolve(
                List.of(easy, medium, hard),
                QuestDifficulty::pickerSlot,
                List.of(10, 11, 12),
                27,
                Set.of()
        );

        assertEquals(10, slots.get(easy));
        assertEquals(11, slots.get(medium));
        assertEquals(12, slots.get(hard));
    }

    @Test
    void reservedMilestoneSelectorSlotUsesFallback() {
        QuestDifficulty easy = difficulty("easy", -1, 22);
        QuestDifficulty medium = difficulty("medium", -1, -1);

        Map<QuestDifficulty, Integer> slots = DifficultySelectorSlots.resolve(
                List.of(easy, medium),
                QuestDifficulty::milestonesSlot,
                List.of(10, 11),
                27,
                Set.of(22)
        );

        assertEquals(10, slots.get(easy));
        assertEquals(11, slots.get(medium));
    }

    @Test
    void reservedDifficultyPickerBackButtonSlotUsesFallback() {
        QuestDifficulty easy = difficulty("easy", 22, -1);
        QuestDifficulty medium = difficulty("medium", -1, -1);

        Map<QuestDifficulty, Integer> slots = DifficultySelectorSlots.resolve(
                List.of(easy, medium),
                QuestDifficulty::pickerSlot,
                List.of(10, 11),
                27,
                Set.of(22)
        );

        assertEquals(10, slots.get(easy));
        assertEquals(11, slots.get(medium));
    }

    private static QuestDifficulty difficulty(String id, int pickerSlot, int milestonesSlot) {
        return new QuestDifficulty(id, id, pickerSlot, milestonesSlot, Map.of(), List.of(), List.of(), List.of());
    }
}
