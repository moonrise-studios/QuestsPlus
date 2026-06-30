package gg.moonrise.quests.core.service;

import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.sdk.currency.QuestCurrencies;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyButton;
import gg.moonrise.quests.sdk.currency.QuestCurrencyKey;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestApiServiceCurrencyTest {

    @AfterEach
    public void tearDownBukkit() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    public void registerCurrencyDelegatesToResetPurchaseRegistry() {
        QuestResetPurchaseService resetPurchaseService = resetPurchaseService();
        QuestApiService api = api(resetPurchaseService);
        Plugin owner = plugin("ExternalEconomy", true);
        TestCurrency currency = new TestCurrency(QuestCurrencyKey.of("stars"));

        api.registerCurrency(owner, currency);

        assertEquals(List.of(currency.key()), api.registeredCurrencies());
        assertEquals(currency, resetPurchaseService.registeredCurrency(currency.key()));

        api.unregisterCurrency(owner, currency.key());

        assertNull(resetPurchaseService.registeredCurrency(currency.key()));
    }

    @Test
    public void registerCurrencyRejectsDisabledOwners() {
        QuestApiService api = api(resetPurchaseService());
        Plugin owner = plugin("ExternalEconomy", false);
        TestCurrency currency = new TestCurrency(QuestCurrencyKey.of("stars"));

        assertThrows(IllegalArgumentException.class, () -> api.registerCurrency(owner, currency));
    }

    @Test
    public void builtInVaultCurrencyResolvesEconomyProviderAfterInitialRegistration() {
        MockBukkit.mock();
        MockBukkit.createMockPlugin("Vault");
        QuestResetPurchaseService resetPurchaseService = resetPurchaseService();
        QuestApiService api = api(
                resetPurchaseService,
                Config.compose(null, null, null, null, null, null, null, new Config.Currencies(List.of("vault"), null, null))
        );

        api.onEnable();

        QuestCurrency vault = resetPurchaseService.registeredCurrency(QuestCurrencies.VAULT);
        assertEquals(List.of(QuestCurrencies.VAULT), api.registeredCurrencies());
        assertNotNull(vault);
        assertEquals(QuestCurrencies.VAULT, vault.key());
        assertFalse(vault.isAvailable());

        Economy economy = mock(Economy.class);
        when(economy.format(1000.0D)).thenReturn("$1,000.00");
        Bukkit.getServicesManager().register(Economy.class, economy, MockBukkit.createMockPlugin("EconomyPlugin"), ServicePriority.Normal);

        assertTrue(vault.isAvailable());
        assertEquals("$1,000.00", vault.displayAmount(mock(Player.class)));
    }

    private static QuestApiService api(QuestResetPurchaseService resetPurchaseService) {
        return api(resetPurchaseService, Config.compose(null, null, null, null, null, null, null));
    }

    private static QuestApiService api(QuestResetPurchaseService resetPurchaseService, Config config) {
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);
        return new QuestApiService(
                mock(QuestsPlusPlugin.class),
                mock(QuestDefinitionService.class),
                mock(QuestService.class),
                mock(QuestResetService.class),
                resetPurchaseService,
                configProvider
        );
    }

    private static QuestResetPurchaseService resetPurchaseService() {
        return new QuestResetPurchaseService();
    }

    private static Plugin plugin(String name, boolean enabled) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn(name);
        when(plugin.isEnabled()).thenReturn(enabled);
        return plugin;
    }

    private record TestCurrency(QuestCurrencyKey key) implements QuestCurrency {

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
            return true;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}
