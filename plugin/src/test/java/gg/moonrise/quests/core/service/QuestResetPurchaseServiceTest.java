package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.model.QuestResetPaymentType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.entity.Player;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestResetPurchaseServiceTest {

    @Test
    void disabledCurrenciesDoNotRequireBukkitServices() {
        Config.PlayerPointsCurrency playerPoints = new Config.PlayerPointsCurrency(false, "Player Points", 25, new Config.PlayerPointsCurrency().getButton());
        Config.VaultCurrency vault = new Config.VaultCurrency(false, "Ingame Money", 1000.0D, new Config.VaultCurrency().getButton());
        Config config = Config.compose(null, null, null, null, null, null, null, playerPoints, vault);

        QuestResetPurchaseService service = service(config);

        assertFalse(service.isAvailable(QuestResetPaymentType.PLAYER_POINTS));
        assertFalse(service.isAvailable(QuestResetPaymentType.MONEY));
        assertFalse(service.charge(mock(Player.class), QuestResetPaymentType.PLAYER_POINTS));
        assertFalse(service.charge(mock(Player.class), QuestResetPaymentType.MONEY));
        assertFalse(service.hasAvailablePaymentMethods());
    }

    @Test
    void missingOptionalCurrencyPluginsAreUnavailable() {
        MockBukkit.mock();
        try {
            QuestResetPurchaseService service = service(Config.compose(null, null, null, null, null, null, null));

            assertFalse(service.isAvailable(QuestResetPaymentType.PLAYER_POINTS));
            assertFalse(service.isAvailable(QuestResetPaymentType.MONEY));
            assertFalse(service.hasAvailablePaymentMethods());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void questResetCostComesFromCurrencyConfigs() {
        Config.PlayerPointsCurrency playerPoints = new Config.PlayerPointsCurrency(true, "Player Points", 42, new Config.PlayerPointsCurrency().getButton());
        Config.VaultCurrency vault = new Config.VaultCurrency(true, "Ingame Money", 1234.5D, new Config.VaultCurrency().getButton());
        Config config = Config.compose(null, null, null, null, null, null, null, playerPoints, vault);
        MockBukkit.mock();
        try {
            QuestResetPurchaseService service = service(config);

            assertEquals("42", service.displayAmount(QuestResetPaymentType.PLAYER_POINTS));
            assertEquals("$1,234.5", service.displayAmount(QuestResetPaymentType.MONEY));
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void paymentButtonComesFromCurrencyConfigs() {
        Config.PlayerPointsCurrency playerPoints = new Config.PlayerPointsCurrency(
                true,
                "Player Points",
                25,
                new Config.MenuButton(13, new Config.MenuItem("GOLD_INGOT", "<gold>Points", List.of("<amount>")))
        );
        Config.VaultCurrency vault = new Config.VaultCurrency(
                true,
                "Ingame Money",
                1000.0D,
                new Config.MenuButton(14, new Config.MenuItem("DIAMOND", "<aqua>Vault", List.of("<payment>")))
        );
        Config config = Config.compose(null, null, null, null, null, null, null, playerPoints, vault);
        QuestResetPurchaseService service = service(config);

        assertEquals(13, service.button(QuestResetPaymentType.PLAYER_POINTS).getSlot());
        assertEquals("GOLD_INGOT", service.button(QuestResetPaymentType.PLAYER_POINTS).getItem().getMaterial());
        assertEquals(14, service.button(QuestResetPaymentType.MONEY).getSlot());
        assertEquals("DIAMOND", service.button(QuestResetPaymentType.MONEY).getItem().getMaterial());
    }

    private static QuestResetPurchaseService service(Config config) {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);
        return new QuestResetPurchaseService(configProvider);
    }
}
