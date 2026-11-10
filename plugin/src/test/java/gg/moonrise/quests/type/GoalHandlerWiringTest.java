package gg.moonrise.quests.type;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.model.QuestTypes;
import gg.moonrise.quests.core.repository.GlobalQuestRepository;
import gg.moonrise.quests.core.repository.QuestRepository;
import gg.moonrise.quests.core.time.QuestClock;
import gg.moonrise.quests.core.service.global.GlobalQuestService;
import gg.moonrise.quests.core.service.indicator.QuestIndicatorPreferenceService;
import gg.moonrise.quests.core.service.indicator.QuestProgressIndicatorService;
import gg.moonrise.quests.core.service.quest.ListQuestVariableSelector;
import gg.moonrise.quests.core.service.quest.QuestDefinitionService;
import gg.moonrise.quests.core.service.quest.QuestProgressService;
import gg.moonrise.quests.core.service.quest.QuestRewardService;
import gg.moonrise.quests.core.service.quest.QuestSelectionService;
import gg.moonrise.quests.core.service.quest.QuestService;
import gg.moonrise.quests.core.service.quest.QuestSlotService;
import gg.moonrise.quests.core.service.quest.QuestTextService;
import gg.moonrise.quests.core.service.quest.QuestUsageService;
import gg.moonrise.quests.core.service.reset.QuestResetPurchaseService;
import gg.moonrise.quests.core.service.reset.QuestResetService;
import gg.moonrise.quests.core.service.streak.QuestStreakService;
import gg.moonrise.quests.config.ConfigProvider;
import gg.moonrise.quests.ui.QuestMenuItems;
import gg.moonrise.quests.ui.QuestMenuService;
import gg.moonrise.quests.ui.QuestResetCurrencies;
import gg.moonrise.quests.ui.QuestResetPurchaseFlow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GoalHandlerWiringTest {

    @BeforeEach
    public void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void builtInHandlersWireWithoutCircularReferencesAndAreRegistered() {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(new Config());

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // Fails the refresh if any constructor dependency cycle remains between the handlers and the services.
            context.setAllowCircularReferences(false);
            context.registerBean(ConfigProvider.class, () -> configProvider);
            context.registerBean(QuestRepository.class, () -> mock(QuestRepository.class));
            context.registerBean(GlobalQuestRepository.class, () -> mock(GlobalQuestRepository.class));
            context.registerBean(QuestStreakService.class, () -> mock(QuestStreakService.class));
            context.registerBean(QuestIndicatorPreferenceService.class, () -> mock(QuestIndicatorPreferenceService.class));
            context.registerBean(QuestClock.class, () -> mock(QuestClock.class));
            context.register(
                    ListQuestVariableSelector.class,
                    QuestDefinitionService.class,
                    QuestProgressIndicatorService.class,
                    GlobalQuestService.class,
                    QuestSlotService.class,
                    QuestTextService.class,
                    QuestRewardService.class,
                    QuestService.class,
                    QuestUsageService.class,
                    QuestSelectionService.class,
                    QuestProgressService.class,
                    QuestResetService.class,
                    QuestResetPurchaseService.class,
                    QuestResetCurrencies.class,
                    QuestResetPurchaseFlow.class,
                    QuestMenuItems.class,
                    QuestMenuService.class,
                    BuiltInGoalHandlerRegistrar.class
            );
            context.scan("gg.moonrise.quests.type", "gg.moonrise.quests.indicator");
            context.refresh();

            QuestDefinitionService definitionService = context.getBean(QuestDefinitionService.class);
            Map<String, GoalHandler> handlers = context.getBeansOfType(GoalHandler.class);
            assertFalse(handlers.isEmpty());
            for (GoalHandler handler : handlers.values()) {
                assertSame(handler, definitionService.handlerIfRegistered(handler.type()).orElseThrow());
            }
            assertTrue(definitionService.handlerIfRegistered(QuestTypes.BLOCK_BREAK).isPresent());
            assertEquals(1L, definitionService.revision());
        }
    }
}
