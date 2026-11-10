package gg.moonrise.quests.core.service.quest;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.file.GlobalQuestsFile;
import gg.moonrise.quests.config.quest.QuestDefinitionConfig;
import gg.moonrise.quests.config.quest.QuestDifficultyConfig;
import gg.moonrise.quests.config.quest.VariableConfig;
import gg.moonrise.quests.entity.QuestDifficulty;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.QuestVariableSelector;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestDefinitionServiceTest {

    private static final QuestType TYPE = QuestType.of("KILL_MOB");

    @Test
    public void difficultiesKeepConfiguredOrder() {
        List<String> ids = List.of("easy", "zeta", "alpha", "medium", "hard", "beta", "omega", "delta", "gamma", "kappa");
        QuestDefinitionService service = service(config(ids, List.of()));

        assertEquals(ids, service.difficulties().stream().map(QuestDifficulty::id).toList());
        assertThrows(UnsupportedOperationException.class, () -> service.difficulties().clear());
    }

    @Test
    public void definitionsKeepConfiguredOrder() {
        List<String> ids = List.of("zulu-quest", "alpha-quest", "mike-quest", "bravo-quest", "yankee-quest");
        QuestDefinitionService service = service(config(List.of("easy"), ids));

        assertEquals(ids, service.definitions().stream().map(QuestDefinition::id).toList());
    }

    @Test
    public void definitionLooksUpNormalizedIds() {
        QuestDefinitionService service = service(config(List.of("easy"), List.of("alpha-quest", "bravo-quest")));

        assertEquals("bravo-quest", service.definition(" BRAVO_QUEST ").map(QuestDefinition::id).orElseThrow());
        assertEquals("alpha-quest", service.definition("alpha-quest").map(QuestDefinition::id).orElseThrow());
        assertTrue(service.definition("missing-quest").isEmpty());
        assertTrue(service.definition(" ").isEmpty());
        assertTrue(service.definition((String) null).isEmpty());
    }

    @Test
    public void reloadReplacesDefinitionsAndDifficultiesTogether() {
        Config initial = config(List.of("easy"), List.of("alpha-quest"));
        Config reloaded = config(List.of("easy", "hard"), List.of("bravo-quest"));
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(initial);
        QuestDefinitionService service = service(configProvider);

        when(configProvider.get()).thenReturn(reloaded);
        service.reload();

        assertTrue(service.definition("alpha-quest").isEmpty());
        assertEquals("bravo-quest", service.definition("bravo-quest").map(QuestDefinition::id).orElseThrow());
        assertEquals(List.of("bravo-quest"), service.definitions().stream().map(QuestDefinition::id).toList());
        assertEquals("hard", service.difficulty("hard").orElseThrow().id());
        assertEquals("easy", service.difficulty("unknown").orElseThrow().id());
    }

    @Test
    public void difficultyIsEmptyBeforeDifficultiesAreLoaded() {
        QuestDefinitionService service = new QuestDefinitionService(mock(ConfigProvider.class), List.of());

        assertTrue(service.difficulty("easy").isEmpty());
        assertTrue(service.difficulty("unknown").isEmpty());
    }

    @Test
    public void globalDefinitionsAreParsedOnReloadAndLookedUpById() {
        GlobalQuestsFile globalQuests = globalQuests(List.of(
                definition("global-zulu"),
                definition("global-alpha"),
                definition("global-unknown-type", "NOT_REGISTERED")
        ));
        Config config = config(List.of("easy"), List.of());
        when(config.getGlobalQuests()).thenReturn(globalQuests);
        QuestDefinitionService service = service(config);

        assertEquals(List.of("global-zulu", "global-alpha"), service.globalDefinitions().stream().map(QuestDefinition::id).toList());
        for (int i = 0; i < 5; i++) {
            assertEquals("global-alpha", service.globalDefinition(" GLOBAL_ALPHA ").map(QuestDefinition::id).orElseThrow());
        }
        assertTrue(service.globalDefinition("global-unknown-type").isEmpty());
        assertTrue(service.globalDefinition(" ").isEmpty());
        assertTrue(service.definition("global-alpha").isEmpty());
        verify(globalQuests, times(1)).getQuestDefinitions();
    }

    @Test
    public void globalDefinitionsAreReparsedWhenGoalHandlerIsRegistered() {
        QuestType fishType = QuestType.of("FISH");
        GlobalQuestsFile globalQuests = globalQuests(List.of(definition("global-fish", fishType.key())));
        Config config = config(List.of("easy"), List.of());
        when(config.getGlobalQuests()).thenReturn(globalQuests);
        QuestDefinitionService service = service(config);
        long initialRevision = service.revision();
        assertTrue(service.globalDefinition("global-fish").isEmpty());

        GoalHandler fishHandler = mock(GoalHandler.class);
        when(fishHandler.type()).thenReturn(fishType);
        service.registerGoalHandler("TestPlugin", fishHandler);

        assertEquals("global-fish", service.globalDefinition("global-fish").map(QuestDefinition::id).orElseThrow());
        assertTrue(service.revision() > initialRevision);
    }

    @Test
    public void personalAndGlobalDefinitionsShareParsingButKeepTheirIdPolicies() {
        QuestDefinitionConfig blankSelector = definition("global-blank-selector");
        Map<String, VariableConfig> blankSelectorVariables = Map.of("block", variable(" ", List.of("STONE")));
        when(blankSelector.getVariables()).thenReturn(blankSelectorVariables);
        QuestDefinitionConfig unknownDifficulty = definition("global-unknown-difficulty");
        when(unknownDifficulty.getDifficulty()).thenReturn("legendary");
        GlobalQuestsFile globalQuests = globalQuests(List.of(
                definition("global-valid"),
                definition("GLOBAL_VALID"),
                definition(" "),
                blankSelector,
                unknownDifficulty
        ));
        QuestDefinitionConfig personalUnknownDifficulty = definition("personal-unknown-difficulty");
        when(personalUnknownDifficulty.getDifficulty()).thenReturn("legendary");
        List<QuestDefinitionConfig> personalDefinitions = List.of(definition("personal-valid"), personalUnknownDifficulty);
        Config config = config(List.of("easy"), List.of());
        when(config.getQuestDefinitions()).thenReturn(personalDefinitions);
        when(config.getGlobalQuests()).thenReturn(globalQuests);

        QuestDefinitionService service = service(config);

        assertEquals(List.of("global-valid", "global-unknown-difficulty"), service.globalDefinitions().stream().map(QuestDefinition::id).toList());
        assertEquals("easy", service.globalDefinition("global-unknown-difficulty").orElseThrow().difficultyId());
        assertEquals(List.of("personal-valid"), service.definitions().stream().map(QuestDefinition::id).toList());
    }

    @Test
    public void duplicatePersonalDefinitionIdFailsTheReload() {
        Config config = config(List.of("easy"), List.of("alpha-quest", "ALPHA_QUEST"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service(config));

        assertEquals("Duplicate quest definition id: alpha-quest", exception.getMessage());
    }

    @Test
    public void resolveVariablesUsesTheConfiguredSelectors() {
        QuestVariableSelector selector = mock(QuestVariableSelector.class);
        when(selector.type()).thenReturn("list");
        when(selector.select(eq(List.of("STONE", "DIRT")), any())).thenReturn("DIRT");
        QuestDefinitionConfig withVariable = definition("block-quest");
        Map<String, VariableConfig> variables = Map.of("Block_Type", variable("list", List.of("STONE", "DIRT")));
        when(withVariable.getVariables()).thenReturn(variables);
        Config config = config(List.of("easy"), List.of());
        List<QuestDefinitionConfig> definitions = List.of(withVariable);
        when(config.getQuestDefinitions()).thenReturn(definitions);
        QuestDefinitionService service = service(config, List.of(selector));

        QuestDefinition definition = service.definition("block-quest").orElseThrow();

        assertEquals(Map.of("block-type", "LIST"), definition.selectorTypes());
        assertEquals(Map.of("block-type", "DIRT"), service.resolveVariables(definition));
    }

    private static VariableConfig variable(String selector, List<String> values) {
        VariableConfig variable = mock(VariableConfig.class);
        when(variable.getSelector()).thenReturn(selector);
        when(variable.getValues()).thenReturn(values);
        return variable;
    }

    private static GlobalQuestsFile globalQuests(List<QuestDefinitionConfig> definitions) {
        GlobalQuestsFile globalQuests = mock(GlobalQuestsFile.class);
        when(globalQuests.getQuestDefinitions()).thenReturn(definitions);
        return globalQuests;
    }

    private static QuestDefinitionService service(Config config) {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);
        return service(configProvider);
    }

    private static QuestDefinitionService service(Config config, List<QuestVariableSelector> selectors) {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);
        return service(configProvider, selectors);
    }

    private static QuestDefinitionService service(ConfigProvider configProvider) {
        return service(configProvider, List.of());
    }

    private static QuestDefinitionService service(ConfigProvider configProvider, List<QuestVariableSelector> selectors) {
        GoalHandler handler = mock(GoalHandler.class);
        when(handler.type()).thenReturn(TYPE);
        QuestDefinitionService service = new QuestDefinitionService(configProvider, selectors);
        service.init();
        service.registerBuiltInGoalHandlers(List.of(handler));
        return service;
    }

    private static Config config(List<String> difficultyIds, List<String> definitionIds) {
        Map<String, QuestDifficultyConfig> difficulties = new LinkedHashMap<>();
        for (String id : difficultyIds) {
            difficulties.put(id, new QuestDifficultyConfig());
        }
        List<QuestDefinitionConfig> definitions = definitionIds.stream()
                .map(QuestDefinitionServiceTest::definition)
                .toList();
        Config config = mock(Config.class);
        when(config.getQuestDifficulties()).thenReturn(difficulties);
        when(config.getQuestDefinitions()).thenReturn(definitions);
        return config;
    }

    private static QuestDefinitionConfig definition(String id) {
        return definition(id, TYPE.key());
    }

    private static QuestDefinitionConfig definition(String id, String type) {
        QuestDefinitionConfig definition = mock(QuestDefinitionConfig.class);
        when(definition.getId()).thenReturn(id);
        when(definition.getType()).thenReturn(type);
        when(definition.isEnabled()).thenReturn(true);
        when(definition.getDifficulty()).thenReturn("easy");
        when(definition.getDisplayName()).thenReturn(id);
        return definition;
    }
}
