package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.model.QuestTypes;
import gg.moonrise.quests.infrastructure.persistence.GlobalQuestRepository;
import gg.moonrise.quests.infrastructure.persistence.QuestRepository;
import gg.moonrise.quests.domain.time.QuestClock;
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
