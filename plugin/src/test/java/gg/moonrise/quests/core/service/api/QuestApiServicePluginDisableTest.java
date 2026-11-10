package gg.moonrise.quests.core.service.api;

import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.sdk.QuestVariableSelector;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyKey;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.core.service.quest.QuestDefinitionService;
import gg.moonrise.quests.core.service.quest.QuestProgressService;
import gg.moonrise.quests.core.service.reset.QuestResetPurchaseService;
import gg.moonrise.quests.core.service.reset.QuestResetService;
import gg.moonrise.quests.config.ConfigProvider;
import java.util.Optional;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuestApiServicePluginDisableTest {

    private static final QuestType TYPE = QuestType.of("EXTERNAL_GOAL");
    private static final QuestCurrencyKey CURRENCY = QuestCurrencyKey.of("stars");

    private ServerMock server;
    private QuestDefinitionService definitionService;
    private QuestResetPurchaseService resetPurchaseService;
    private QuestApiService api;
    private GoalHandler handler;
    private QuestVariableSelector selector;
    private QuestCurrency currency;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        definitionService = mock(QuestDefinitionService.class);
        resetPurchaseService = new QuestResetPurchaseService();
        api = new QuestApiService(
                mock(QuestsPlusPlugin.class),
                definitionService,
                mock(QuestProgressService.class),
                mock(QuestResetService.class),
                resetPurchaseService,
                mock(ConfigProvider.class)
        );
        server.getPluginManager().registerEvents(api, MockBukkit.createMockPlugin("QuestsPlusHost"));

        handler = mock(GoalHandler.class);
        when(handler.type()).thenReturn(TYPE);
        selector = mock(QuestVariableSelector.class);
        when(selector.type()).thenReturn("external");
        currency = mock(QuestCurrency.class);
        when(currency.key()).thenReturn(CURRENCY);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void disablingOwnerUnregistersItsExtensions() {
        Plugin owner = MockBukkit.createMockPlugin("ExternalQuests");
        api.registerGoalHandler(owner, handler);
        api.registerVariableSelector(owner, selector);
        api.registerCurrency(owner, currency);

        server.getPluginManager().disablePlugin(owner);

        verify(definitionService).unregisterGoalHandler(TYPE, handler);
        verify(definitionService).unregisterVariableSelector("EXTERNAL", selector);
        assertTrue(resetPurchaseService.registeredCurrency(CURRENCY).isEmpty());
    }

    @Test
    public void reEnabledOwnerCanRegisterAgain() {
        Plugin owner = MockBukkit.createMockPlugin("ExternalQuests");
        api.registerGoalHandler(owner, handler);
        api.registerCurrency(owner, currency);

        server.getPluginManager().disablePlugin(owner);
        server.getPluginManager().enablePlugin(owner);

        assertDoesNotThrow(() -> api.registerGoalHandler(owner, handler));
        assertDoesNotThrow(() -> api.registerCurrency(owner, currency));
        verify(definitionService, times(2)).registerGoalHandler("ExternalQuests", handler);
        assertEquals(Optional.of(currency), resetPurchaseService.registeredCurrency(CURRENCY));
    }

    @Test
    public void disablingUntrackedPluginLeavesOtherOwnersRegistered() {
        Plugin owner = MockBukkit.createMockPlugin("ExternalQuests");
        Plugin unrelated = MockBukkit.createMockPlugin("Unrelated");
        api.registerCurrency(owner, currency);

        server.getPluginManager().disablePlugin(unrelated);

        verifyNoInteractions(definitionService);
        assertEquals(Optional.of(currency), resetPurchaseService.registeredCurrency(CURRENCY));
    }
}
