package gg.moonrise.quests.model;

import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.command.QuestCommand;
import gg.moonrise.quests.core.service.QuestIndicatorPreferenceService;
import gg.moonrise.quests.util.QuestDisplayNames;
import gg.moonrise.quests.util.QuestNames;
import gg.moonrise.quests.util.QuestNumberFormatter;
import org.incendo.cloud.annotations.Command;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestStateAndNamesTest {

    @Test
    public void questNamesNormalizeForStorageAndPlaceholders() {
        assertEquals("daily-kills", QuestNames.normalize(" Daily_Kills "));
        assertEquals("daily_kills", QuestNames.placeholderKey("Daily-Kills"));
        assertEquals("", QuestNames.normalize(null));
    }

    @Test
    public void questNumberFormatterUsesGrouping() {
        assertEquals("1,234,567", QuestNumberFormatter.format(1_234_567));
        assertEquals("-1,234", QuestNumberFormatter.format(-1_234));
    }

    @Test
    public void indicatorPreferencesNormalizeCommandInput() {
        QuestIndicatorPreferenceService service = new QuestIndicatorPreferenceService(null);

        assertEquals("BOSS_BAR", service.normalize(" boss-bar "));
        assertEquals("ACTION_BAR", service.normalize("action bar"));
        assertEquals("CHAT", service.normalize("chat!"));
        assertEquals("", service.normalize(null));
    }

    @Test
    public void questIndicatorCommandUsesScopedTwoStepSelection() {
        Set<String> commands = Arrays.stream(QuestCommand.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Command.class))
                .filter(Objects::nonNull)
                .map(Command::value)
                .filter(value -> value.startsWith("q|quests indicator"))
                .collect(Collectors.toSet());

        assertTrue(commands.contains("q|quests indicator <scope> <type>"));
        assertFalse(commands.contains("q|quests indicator <type>"));
        assertFalse(commands.contains("q|quests indicator main <type>"));
        assertFalse(commands.contains("q|quests indicator global <type>"));
        assertFalse(commands.contains("q|quests indicator both <type>"));
    }

    @Test
    public void adminCommandsUseDailyRerollsAndQuestTypes() {
        Set<String> commands = Arrays.stream(QuestCommand.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Command.class))
                .filter(Objects::nonNull)
                .map(Command::value)
                .collect(Collectors.toSet());

        assertTrue(commands.contains("qa|questsadmin dailyrerolls reset <player>"));
        assertTrue(commands.contains("qa|questsadmin add <amount> <quest-type> <player>"));
        assertFalse(commands.contains("qa|questsadmin reroll <player>"));
        assertFalse(commands.contains("qa|questsadmin rerolls reset <player>"));
        assertFalse(commands.contains("qa|questsadmin add <amount> <quest-id> <player>"));
    }

    @Test
    public void questDisplayNamesPluralizeTargetNameForCounts() {
        assertEquals("Iron Ingot", QuestDisplayNames.typeName("minecraft:iron_ingot", 1));
        assertEquals("Iron Ingots", QuestDisplayNames.typeName("minecraft:iron_ingot", 16));
        assertEquals("Charcoal", QuestDisplayNames.typeName("minecraft:charcoal", 16));
        assertEquals("Glass", QuestDisplayNames.typeName("minecraft:glass", 16));
        assertEquals("Raw Iron", QuestDisplayNames.typeName("minecraft:raw_iron", 16));
        assertEquals("Quartz", QuestDisplayNames.typeName("minecraft:quartz", 16));
        assertEquals("Paper", QuestDisplayNames.typeName("minecraft:paper", 16));
        assertEquals("Potions", QuestDisplayNames.typeName("minecraft:potion", 9));
        assertEquals("Sweet Berries", QuestDisplayNames.typeName("minecraft:sweet_berry", 2));
        assertEquals("Silverfish", QuestDisplayNames.typeName("SILVERFISH", 1));
        assertEquals("Silverfish", QuestDisplayNames.typeName("SILVERFISH", 1_850));
        assertEquals("Endermen", QuestDisplayNames.typeName("ENDERMAN", 2));
        assertEquals("Drowned", QuestDisplayNames.typeName("DROWNED", 2));
        assertEquals("Cod", QuestDisplayNames.typeName("COD", 2));
        assertEquals("Squid", QuestDisplayNames.typeName("SQUID", 2));
        assertEquals("Pufferfish", QuestDisplayNames.typeName("PUFFERFISH", 2));
        assertEquals("Tropical Fish", QuestDisplayNames.typeName("TROPICAL_FISH", 2));
    }

    @Test
    public void playerQuestStateSortsAndReplacesQuestsBySlot() {
        GeneratedQuest slotTwo = quest(2, "BREAK_BLOCK", false);
        GeneratedQuest slotOne = quest(1, "KILL_MOB", false);
        PlayerQuestState state = new PlayerQuestState(
                UUID.randomUUID(),
                "daily",
                List.of(slotTwo, slotOne),
                Map.of("easy", 2, "hard", 1),
                Set.of("easy:1")
        );

        assertEquals(List.of(slotOne, slotTwo), state.quests());
        assertEquals(3, state.questsCompleted());
        assertTrue(state.hasActiveQuest(QuestType.of("BREAK_BLOCK")));
        assertTrue(state.hasQuestAtSlot(1));
        assertNull(state.questAtSlot(3));
        assertTrue(state.hasExecutedMilestone("easy", 1));

        GeneratedQuest replacement = quest(1, "TRAVEL", true);
        state.replaceQuestAtSlot(1, replacement);

        assertEquals(replacement, state.questAtSlot(1));
        assertFalse(state.hasActiveQuest(QuestType.of("KILL_MOB")));
    }

    @Test
    public void globalQuestStateCountsPositiveParticipantsOnly() {
        UUID active = UUID.randomUUID();
        UUID zero = UUID.randomUUID();
        GlobalQuestState state = new GlobalQuestState(
                quest(-1, "GLOBAL", false),
                "weekly",
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now().plusDays(7),
                Map.of(active, 10, zero, 0),
                false
        );

        assertEquals(10, state.contribution(active));
        assertEquals(0, state.contribution(UUID.randomUUID()));
        assertEquals(1, state.participants());
    }

    @Test
    public void questDifficultyRequirementsUsePerDifficultyCompletionCounts() {
        PlayerQuestState state = new PlayerQuestState(
                UUID.randomUUID(),
                "daily",
                List.of(),
                Map.of("easy", 10, "medium", 2),
                Set.of()
        );
        QuestDifficulty difficulty = new QuestDifficulty(
                "hard",
                "<red><b>HARD",
                -1,
                -1,
                Map.of("easy", 10, "medium", 3),
                List.of(),
                List.of(),
                List.of()
        );

        assertFalse(difficulty.requirementsMet(state));
        assertEquals(Map.of("medium", 3), difficulty.unmetRequirements(state));

        state.setDifficultyCompletions("medium", 3);

        assertTrue(difficulty.requirementsMet(state));
        assertEquals(Map.of(), difficulty.unmetRequirements(state));
    }

    private static GeneratedQuest quest(int slot, String type, boolean completed) {
        return new GeneratedQuest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "daily",
                "definition-" + slot,
                QuestType.of(type),
                "easy",
                "Easy",
                "Quest",
                List.of(),
                Map.of(),
                slot,
                10,
                completed ? 10 : 0,
                completed
        );
    }
}
