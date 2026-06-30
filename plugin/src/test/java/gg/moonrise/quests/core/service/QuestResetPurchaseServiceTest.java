package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.sdk.currency.QuestCurrencies;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyButton;
import gg.moonrise.quests.sdk.currency.QuestCurrencyKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestResetPurchaseServiceTest {

    @Test
    public void builtInCurrenciesAreRegistryEntriesAndCannotBeUnregisteredByExternalOwners() {
        QuestResetPurchaseService service = service(Config.compose(null, null, null, null, null, null, null));
        Plugin owner = plugin("ExternalEconomy", true);

        assertEquals(List.of(), service.registeredCurrencyKeys());

        TestCurrency builtIn = new TestCurrency(QuestCurrencies.VAULT);
        service.registerBuiltInCurrency(builtIn);

        assertEquals(List.of(QuestCurrencies.VAULT), service.registeredCurrencyKeys());
        assertEquals(builtIn, service.registeredCurrency(QuestCurrencies.VAULT));

        service.unregisterCurrency(owner, QuestCurrencies.PLAYER_POINTS);
        service.unregisterCurrency(owner, QuestCurrencies.VAULT);

        assertEquals(List.of(QuestCurrencies.VAULT), service.registeredCurrencyKeys());
        assertThrows(IllegalArgumentException.class, () -> service.registerCurrency(owner, new TestCurrency(QuestCurrencies.VAULT)));
    }

    @Test
    public void externalCurrenciesRegisterThroughSdkContract() {
        Config.PlayerPointsCurrency playerPoints = new Config.PlayerPointsCurrency("Player Points", 25, new Config.PlayerPointsCurrency().getButton());
        Config.VaultCurrency vault = new Config.VaultCurrency("Ingame Money", 1000.0D, new Config.VaultCurrency().getButton());
        QuestResetPurchaseService service = service(Config.compose(null, null, null, null, null, null, null, playerPoints, vault));
        Plugin owner = plugin("ExternalEconomy", true);
        Player player = player(UUID.randomUUID());
        TestCurrency currency = new TestCurrency(QuestCurrencyKey.of("stars"));

        service.registerCurrency(owner, currency);
        QuestCurrency registered = service.registeredCurrency(currency.key());

        assertEquals(List.of(currency.key()), service.registeredCurrencyKeys());
        assertEquals(currency, registered);
        assertTrue(service.registeredCurrencies().contains(currency));
        assertTrue(registered.isAvailable());
        assertEquals("Stars", registered.displayName());
        assertEquals("5 stars", registered.displayAmount(player));
        assertTrue(registered.charge(player, 5.0D));
        assertEquals(player, currency.chargedPlayer());

        service.unregisterCurrency(owner, currency.key());

        assertNull(service.registeredCurrency(currency.key()));
        assertFalse(service.registeredCurrencies().contains(currency));
    }

    @Test
    public void externalCurrenciesCannotUseReservedOrDuplicateKeys() {
        QuestResetPurchaseService service = service(Config.compose(null, null, null, null, null, null, null));
        Plugin owner = plugin("ExternalEconomy", true);
        TestCurrency currency = new TestCurrency(QuestCurrencyKey.of("stars"));

        service.registerCurrency(owner, currency);

        assertThrows(IllegalArgumentException.class, () -> service.registerCurrency(owner, new TestCurrency(currency.key())));
        assertThrows(IllegalArgumentException.class, () -> service.registerCurrency(owner, new TestCurrency(QuestCurrencies.PLAYER_POINTS)));
    }

    @Test
    public void beginBlocksDuplicateAttemptsUntilFinish() {
        QuestResetPurchaseService service = unloadedService(Config.compose(null, null, null, null, null, null, null));
        Player player = player(UUID.randomUUID());

        assertTrue(service.begin(player));
        assertFalse(service.begin(player));
        assertTrue(service.isProcessing(player));

        service.finish(player);

        assertFalse(service.isProcessing(player));
        assertTrue(service.begin(player));
    }

    @Test
    public void finishIsIdempotentForRetiredAndCompletionCallbacks() {
        QuestResetPurchaseService service = unloadedService(Config.compose(null, null, null, null, null, null, null));
        Player player = player(UUID.randomUUID());

        assertTrue(service.begin(player));

        service.finish(player);
        service.finish(player);

        assertFalse(service.isProcessing(player));
        assertTrue(service.begin(player));
    }

    private static QuestResetPurchaseService service(Config config) {
        return unloadedService(config);
    }

    private static QuestResetPurchaseService unloadedService(Config config) {
        return new QuestResetPurchaseService();
    }

    private static Player player(UUID playerId) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        return player;
    }

    private static Plugin plugin(String name, boolean enabled) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn(name);
        when(plugin.isEnabled()).thenReturn(enabled);
        return plugin;
    }

    private static final class TestCurrency implements QuestCurrency {

        private final QuestCurrencyKey key;
        private Player chargedPlayer;

        private TestCurrency(QuestCurrencyKey key) {
            this.key = key;
        }

        @Override
        public QuestCurrencyKey key() {
            return key;
        }

        @Override
        public String displayName() {
            return "Stars";
        }

        @Override
        public String displayAmount(Player player) {
            return "5 stars";
        }

        @Override
        public double questResetCost() {
            return 5.0D;
        }

        @Override
        public QuestCurrencyButton button() {
            return QuestCurrencyButton.of(13, "NETHER_STAR", "<aqua>Stars", List.of("<gray>Spend 5 stars."));
        }

        @Override
        public boolean charge(Player player, double amount) {
            this.chargedPlayer = player;
            return true;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        private Player chargedPlayer() {
            return chargedPlayer;
        }
    }
}
