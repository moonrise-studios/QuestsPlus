package gg.moonrise.quests.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestRowCodecTest {

    @Test
    public void descriptionAndVariablesRoundTripThroughJson() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("mob", "zombie");
        variables.put("amount", "10");

        assertEquals(List.of("<gray>Line one", "Line two"), QuestRowCodec.stringList(QuestRowCodec.toJson(List.of("<gray>Line one", "Line two"))));
        assertEquals(variables, QuestRowCodec.stringMap(QuestRowCodec.toJson(variables)));
    }

    @Test
    public void emptyJsonColumnsDecodeToEmptyCollections() {
        assertTrue(QuestRowCodec.stringList(null).isEmpty());
        assertTrue(QuestRowCodec.stringMap(null).isEmpty());
    }

    @Test
    public void jsonNullVariableValuesStayNull() {
        Map<String, String> decoded = QuestRowCodec.stringMap("{\"mob\":null}");

        assertTrue(decoded.containsKey("mob"));
        assertNull(decoded.get("mob"));
    }

    @Test
    public void blankDifficultyColumnsFallBackToEasy() {
        assertEquals("easy", QuestRowCodec.difficultyId(null));
        assertEquals("easy", QuestRowCodec.difficultyId(" "));
        assertEquals("hard", QuestRowCodec.difficultyId("hard"));
        assertEquals("<green><b>EASY", QuestRowCodec.difficultyDisplayName(""));
        assertEquals("<red>Hard", QuestRowCodec.difficultyDisplayName("<red>Hard"));
    }
}
