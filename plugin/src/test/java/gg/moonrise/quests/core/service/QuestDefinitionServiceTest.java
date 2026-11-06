package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
        assertEquals("hard", service.difficulty("hard").id());
        assertEquals("easy", service.difficulty("unknown").id());
    }

    private static QuestDefinitionService service(Config config) {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);
        return service(configProvider);
    }

    private static QuestDefinitionService service(ConfigProvider configProvider) {
        GoalHandler handler = mock(GoalHandler.class);
        when(handler.type()).thenReturn(TYPE);
        QuestDefinitionService service = new QuestDefinitionService(configProvider, List.of(), List.of(handler));
        service.init();
        return service;
    }

    private static Config config(List<String> difficultyIds, List<String> definitionIds) {
        Map<String, Config.QuestDifficultyConfig> difficulties = new LinkedHashMap<>();
        for (String id : difficultyIds) {
            difficulties.put(id, new Config.QuestDifficultyConfig());
        }
        List<Config.QuestDefinitionConfig> definitions = definitionIds.stream()
                .map(QuestDefinitionServiceTest::definition)
                .toList();
        Config config = mock(Config.class);
        when(config.getQuestDifficulties()).thenReturn(difficulties);
        when(config.getQuestDefinitions()).thenReturn(definitions);
        return config;
    }

    private static Config.QuestDefinitionConfig definition(String id) {
        Config.QuestDefinitionConfig definition = mock(Config.QuestDefinitionConfig.class);
        when(definition.getId()).thenReturn(id);
        when(definition.getType()).thenReturn(TYPE.key());
        when(definition.isEnabled()).thenReturn(true);
        when(definition.getDifficulty()).thenReturn("easy");
        when(definition.getDisplayName()).thenReturn(id);
        return definition;
    }
}
