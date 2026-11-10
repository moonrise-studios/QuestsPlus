package gg.moonrise.quests.application.quest;

import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.infrastructure.persistence.QuestRepository;
import gg.moonrise.quests.application.global.GlobalQuestService;
import gg.moonrise.quests.application.indicator.QuestProgressIndicatorService;
import gg.moonrise.quests.application.streak.QuestStreakService;
import gg.moonrise.quests.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestDescriptionDisplayTest {

    @Test
    public void rawRegistryVariableValuesRenderAsDisplayNamesInDescriptions() {
        QuestDefinitionService definitionService = mock(QuestDefinitionService.class);
        QuestServices service = QuestServices.create(
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

        String rendered = service.text().replaceRawVariableDisplayValues("Harvest 175 minecraft:carrot.", quest);

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
