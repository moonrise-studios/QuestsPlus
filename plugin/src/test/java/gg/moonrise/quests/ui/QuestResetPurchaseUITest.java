package gg.moonrise.quests.ui;

import gg.moonrise.engine.paper.gui.button.Button;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.QuestMenuService;
import gg.moonrise.quests.model.QuestResetEligibility;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyButton;
import gg.moonrise.quests.sdk.currency.QuestCurrencyKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestResetPurchaseUITest {

    private ServerMock server;
    private Player player;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("ResetBuyer");
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void purchaseMenuShowsOnlyAvailableCurrencyButtonsAndBackNavigation() {
        QuestMenuService menuService = mock(QuestMenuService.class);
        Config config = new Config();
        QuestCurrency stars = currency("stars");
        QuestCurrency hidden = currency("hidden");
        QuestCurrencyButton starsButton = QuestCurrencyButton.of(11, "NETHER_STAR", "<aqua>Stars", List.of("<gray>Spend stars."));
        QuestCurrencyButton hiddenButton = QuestCurrencyButton.of(15, "BARRIER", "<red>Hidden", List.of("<gray>Unavailable."));
        QuestResetEligibility eligibility = new QuestResetEligibility(3, 3);
        when(menuService.config()).thenReturn(config);
        when(menuService.questResetCurrencies()).thenReturn(List.of(stars, hidden));
        when(menuService.questResetPaymentButton(stars)).thenReturn(starsButton);
        when(menuService.questResetPaymentButton(hidden)).thenReturn(hiddenButton);
        when(menuService.canShowQuestResetPayment(stars)).thenReturn(true);
        when(menuService.canShowQuestResetPayment(hidden)).thenReturn(false);
        when(menuService.buildQuestResetPurchaseItem(player, starsButton, stars, eligibility)).thenReturn(new ItemStack(Material.NETHER_STAR));
        when(menuService.buildTokenItem(eq(config.getMenu().getResetMenu().getBackButton().getItem()), any(QuestMenuService.MapTokens.class), eq(Material.ARROW)))
                .thenReturn(new ItemStack(Material.ARROW));
        when(menuService.buildMenuItem(config.getMenu().getResetMenu().getFiller())).thenReturn(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));

        QuestResetPurchaseUI ui = new QuestResetPurchaseUI(player, menuService, eligibility);

        assertEquals(27, ui.getButtonCount());
        assertButtonItem(Material.NETHER_STAR, ui.getButton(11));
        assertButtonItem(Material.ARROW, ui.getButton(22));
        assertNotNull(ui.getButton(15));
        assertNotNull(ui.getButton(11).clickAction());
        assertNull(ui.getButton(15).clickAction());

        ui.getButton(11).processClickAction(player, null);
        ui.getButton(22).processClickAction(player, null);

        verify(menuService).applyQuestResetPurchase(player, QuestCurrencyKey.of("stars"));
        verify(menuService).openDailyQuests(player);
    }

    private void assertButtonItem(Material expected, Button button) {
        assertNotNull(button);
        assertEquals(expected, button.item(player).getType());
    }

    private static QuestCurrency currency(String key) {
        QuestCurrency currency = mock(QuestCurrency.class);
        when(currency.key()).thenReturn(QuestCurrencyKey.of(key));
        return currency;
    }
}
