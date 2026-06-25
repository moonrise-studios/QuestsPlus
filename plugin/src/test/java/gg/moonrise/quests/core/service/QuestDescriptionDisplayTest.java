package gg.moonrise.quests.core.service;

import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestDescriptionDisplayTest {

    @Test
    void rawRegistryVariableValuesRenderAsDisplayNamesInDescriptions() {
        QuestDefinitionService definitionService = mock(QuestDefinitionService.class);
        QuestService service = new QuestService(
                mock(ConfigProvider.class),
                definitionService,
                mock(QuestRepository.class),
                mock(QuestStreakService.class),
                mock(GlobalQuestService.class),
                mock(QuestProgressIndicatorService.class)
        );
        GeneratedQuest quest = quest();
        when(definitionService.variablePlaceholders(quest)).thenReturn(Map.of(
                "item-type", "Carrots",
                "item_type", "Carrots",
                "item-type-key", "minecraft:carrot",
                "item_type_key", "minecraft:carrot"
        ));

        String rendered = service.replaceRawVariableDisplayValues("Harvest 175 minecraft:carrot.", quest);

        assertEquals("Harvest 175 Carrots.", rendered);
    }

    private static GeneratedQuest quest() {
        return new GeneratedQuest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "daily",
                "farmer",
                QuestType.of("HARVEST_ITEM"),
                "easy",
                "Easy",
                "<green>Farmer",
                List.of("Harvest 175 minecraft:carrot."),
                Map.of("item-type", "minecraft:carrot", "goal-amount", "175"),
                0,
                false,
                175,
                175,
                true
        );
    }
}
