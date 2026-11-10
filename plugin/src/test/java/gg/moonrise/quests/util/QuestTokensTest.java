package gg.moonrise.quests.util;

import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestTypes;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QuestTokensTest {

    @Test
    public void applyReplacesEveryOccurrenceAndLeavesUnknownTokens() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", "Steve");
        values.put("goal-amount", "1,000");

        assertEquals("Steve/Steve needs 1,000 <unknown>", QuestTokens.apply("<player>/<player> needs <goal-amount> <unknown>", values));
        assertEquals("", QuestTokens.apply(null, values));
    }

    @Test
    public void applyReplacesKeysInMapOrder() {
        Map<String, String> earlierKeyFirst = new LinkedHashMap<>();
        earlierKeyFirst.put("outer", "<inner>");
        earlierKeyFirst.put("inner", "value");
        Map<String, String> laterKeyFirst = new LinkedHashMap<>();
        laterKeyFirst.put("inner", "value");
        laterKeyFirst.put("outer", "<inner>");

        assertEquals("value", QuestTokens.apply("<outer>", earlierKeyFirst));
        assertEquals("<inner>", QuestTokens.apply("<outer>", laterKeyFirst));
    }

    @Test
    public void questNumbersAreFormattedOnlyForDisplay() {
        GeneratedQuest quest = quest(12_345, 1_500);
        Map<String, String> formatted = new LinkedHashMap<>();
        Map<String, String> raw = new LinkedHashMap<>();

        QuestTokens.putQuestNumbers(formatted, quest, true);
        QuestTokens.putQuestNumbers(raw, quest, false);

        assertEquals(List.of("goal_amount", "goal-amount", "progress"), List.copyOf(formatted.keySet()));
        assertEquals(Map.of("goal_amount", "12,345", "goal-amount", "12,345", "progress", "1,500"), formatted);
        assertEquals(Map.of("goal_amount", "12345", "goal-amount", "12345", "progress", "1500"), raw);
        assertEquals("-7", QuestTokens.number(-7, true));
    }

    @Test
    public void percentRoundsDownCapsAtHundredAndHandlesEmptyMax() {
        assertEquals("33", QuestTokens.percent(1, 3, true));
        assertEquals("99", QuestTokens.percent(1_999, 2_000, true));
        assertEquals("100", QuestTokens.percent(5_000, 2_000, false));
        assertEquals("0", QuestTokens.percent(5, 0, true));
        assertEquals("0", QuestTokens.percent(5, -1, false));
    }

    @Test
    public void progressPercentHasTwoDecimalsAndIsClamped() {
        assertEquals("33.33%", QuestTokens.progressPercent(quest(3, 1)));
        assertEquals("66.67%", QuestTokens.progressPercent(quest(3, 2)));
        assertEquals("100.00%", QuestTokens.progressPercent(quest(10, 25)));
        assertEquals("0.00%", QuestTokens.progressPercent(quest(0, 5)));
        assertEquals("0.00%", QuestTokens.progressPercent(quest(4, -2)));
    }

    @Test
    public void stripTagsRemovesEveryTagAndTrims() {
        assertEquals("EASY", QuestTokens.stripTags("<green><b>EASY"));
        assertEquals("Kill zombies", QuestTokens.stripTags(" <green>Kill <b>zombies</b> "));
        assertEquals("Custom", QuestTokens.stripTags("<not_a_real_tag>Custom</not_a_real_tag>"));
        assertEquals("a  c", QuestTokens.stripTags("a <b> c"));
        assertEquals("plain", QuestTokens.stripTags("plain"));
        assertEquals("", QuestTokens.stripTags(null));
    }

    private static GeneratedQuest quest(int goalAmount, int progress) {
        return new GeneratedQuest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "reset",
                "zombies",
                QuestTypes.KILL_MOB,
                "easy",
                "Easy",
                "Kill zombies",
                List.of(),
                Map.of(),
                goalAmount,
                progress,
                false
        );
    }
}
