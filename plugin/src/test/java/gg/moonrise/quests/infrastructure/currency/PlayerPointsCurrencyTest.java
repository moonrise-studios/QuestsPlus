package gg.moonrise.quests.infrastructure.currency;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.PlayerPointsCurrencyConfig;
import gg.moonrise.quests.core.service.ConfigProvider;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerPointsCurrencyTest {

    @Test
    public void toPointsRoundsHalfUpAndClampsInvalidAmounts() {
        assertEquals(5, PlayerPointsCurrency.toPoints(4.5D));
        assertEquals(4, PlayerPointsCurrency.toPoints(4.49D));
        assertEquals(25, PlayerPointsCurrency.toPoints(25.0D));
        assertEquals(0, PlayerPointsCurrency.toPoints(-3.0D));
        assertEquals(0, PlayerPointsCurrency.toPoints(Double.NaN));
        assertEquals(Integer.MAX_VALUE, PlayerPointsCurrency.toPoints(1.0E12D));
    }

    @Test
    public void displayAmountMatchesTheRoundedChargeAmount() {
        PlayerPointsCurrency currency = currency(new PlayerPointsCurrencyConfig("Player Points", 40, new PlayerPointsCurrencyConfig().getButton()));

        assertEquals(40.0D, currency.questResetCost());
        assertEquals(Integer.toString(PlayerPointsCurrency.toPoints(currency.questResetCost())), currency.displayAmount(null));
        assertEquals("40", currency.displayAmount(null));
    }

    @Test
    public void negativeConfiguredCostDisplaysZero() {
        PlayerPointsCurrency currency = currency(new PlayerPointsCurrencyConfig("Player Points", -5, new PlayerPointsCurrencyConfig().getButton()));

        assertEquals("0", currency.displayAmount(null));
    }

    @Test
    public void chargeAndRefundAreRejectedWhenPlayerPointsIsUnavailable() {
        PlayerPointsCurrency currency = currency(new PlayerPointsCurrencyConfig());
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        assertFalse(currency.charge(player, 4.5D));
        assertFalse(currency.refund(player, 4.5D));
    }

    private static PlayerPointsCurrency currency(PlayerPointsCurrencyConfig playerPoints) {
        Config config = Config.compose(null, null, null, null, null, null, null, playerPoints, null);
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);
        return new PlayerPointsCurrency(configProvider);
    }
}
