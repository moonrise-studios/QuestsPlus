package gg.moonrise.quests.command;

import gg.moonrise.quests.config.ConfigProvider;
import gg.moonrise.quests.core.service.global.GlobalQuestService;
import gg.moonrise.quests.core.service.quest.QuestDefinitionService;
import gg.moonrise.quests.core.service.indicator.QuestIndicatorPreferenceService;
import gg.moonrise.quests.ui.QuestMenuService;
import gg.moonrise.quests.core.service.indicator.QuestProgressIndicatorService;
import gg.moonrise.quests.core.service.reset.QuestResetService;
import gg.moonrise.quests.core.service.quest.QuestService;
import gg.moonrise.quests.core.service.quest.QuestProgressService;
import gg.moonrise.quests.core.service.quest.QuestSlotService;
import gg.moonrise.quests.core.service.quest.QuestTextService;
import gg.moonrise.quests.core.service.quest.QuestUsageService;
import gg.moonrise.quests.core.service.streak.QuestStreakService;
import gg.moonrise.quests.core.service.reload.QuestReloadService;
import gg.moonrise.quests.entity.QuestDifficulty;
import gg.moonrise.quests.sdk.model.QuestType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QuestCommandSuggestionTest {

    private QuestDefinitionService definitionService;
    private QuestProgressIndicatorService progressIndicatorService;
    private QuestCommand command;

    @BeforeEach
    public void setUp() {
        ServerMock server = MockBukkit.mock();
        server.addPlayer("Notch");
        server.addPlayer("jeb_");
        server.addPlayer("Dinnerbone");
        definitionService = mock(QuestDefinitionService.class);
        progressIndicatorService = mock(QuestProgressIndicatorService.class);
        command = new QuestCommand(
                mock(ConfigProvider.class),
                definitionService,
                mock(QuestService.class),
                mock(QuestSlotService.class),
                mock(QuestUsageService.class),
                mock(QuestProgressService.class),
                mock(QuestTextService.class),
                mock(QuestMenuService.class),
                mock(QuestResetService.class),
                mock(QuestStreakService.class),
                mock(QuestReloadService.class),
                mock(GlobalQuestService.class),
                progressIndicatorService,
                mock(QuestIndicatorPreferenceService.class)
        );
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void onlinePlayersMatchCaseInsensitiveSubstrings() {
        assertEquals(List.of("Notch", "jeb_", "Dinnerbone"), command.onlinePlayerSuggestions(null, ""));
        assertEquals(List.of("Notch"), command.onlinePlayerSuggestions(null, "TCH"));
        assertEquals(List.of("Dinnerbone"), command.onlinePlayerSuggestions(null, "bone"));
        assertEquals(List.of("jeb_"), command.onlinePlayerSuggestions(null, "EB_"));
        assertEquals(List.of(), command.onlinePlayerSuggestions(null, "steve"));
    }

    @Test
    public void difficultiesMatchCaseInsensitiveSubstrings() {
        when(definitionService.difficulties()).thenReturn(List.of(difficulty("easy"), difficulty("medium"), difficulty("very-hard")));

        assertEquals(List.of("easy", "medium", "very-hard"), command.questDifficultySuggestions(null, null));
        assertEquals(List.of("medium"), command.questDifficultySuggestions(null, "DIU"));
        assertEquals(List.of("very-hard"), command.questDifficultySuggestions(null, "y_h"));
        assertEquals(List.of("very-hard"), command.questDifficultySuggestions(null, "HARD"));
    }

    @Test
    public void questTypesMatchSubstringsAnywhereInTheKey() {
        when(definitionService.registeredTypes()).thenReturn(List.of(QuestType.of("KILL_MOB"), QuestType.of("BLOCK_BREAK"), QuestType.of("CRAFT_ITEM")));

        assertEquals(List.of("BLOCK_BREAK", "CRAFT_ITEM", "KILL_MOB"), command.questTypeSuggestions(null, ""));
        assertEquals(List.of("KILL_MOB"), command.questTypeSuggestions(null, "mob"));
        assertEquals(List.of("BLOCK_BREAK"), command.questTypeSuggestions(null, "k bre"));
        assertEquals(List.of("CRAFT_ITEM"), command.questTypeSuggestions(null, "item"));
    }

    @Test
    public void indicatorScopesMatchSubstrings() {
        assertEquals(List.of("Main", "Both", "Global"), command.questIndicatorScopeSuggestions(null, ""));
        assertEquals(List.of("Main"), command.questIndicatorScopeSuggestions(null, "ain"));
        assertEquals(List.of("Both", "Global"), command.questIndicatorScopeSuggestions(null, "o"));
    }

    @Test
    public void indicatorsMatchSubstringsAndIncludeDefaultAndOff() {
        when(progressIndicatorService.availableIndicatorTypes()).thenReturn(List.of("BOSS_BAR", "ACTION_BAR", "OFF"));

        assertEquals(List.of("DEFAULT", "OFF", "BOSS_BAR", "ACTION_BAR"), command.questIndicatorSuggestions(null, ""));
        assertEquals(List.of("BOSS_BAR", "ACTION_BAR"), command.questIndicatorSuggestions(null, "bar"));
        assertEquals(List.of("ACTION_BAR"), command.questIndicatorSuggestions(null, "tion"));
    }

    private static QuestDifficulty difficulty(String id) {
        return new QuestDifficulty(id, id, 0, 0, null, List.of(), List.of(), List.of());
    }
}
