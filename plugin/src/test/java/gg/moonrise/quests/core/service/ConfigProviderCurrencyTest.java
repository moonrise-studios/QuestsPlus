package gg.moonrise.quests.core.service;

import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigProviderCurrencyTest {

    @TempDir
    private Path tempDir;

    @Test
    void loadsDefaultCurrencyConfigFromCurrenciesYml() {
        QuestsPlusPlugin plugin = mock(QuestsPlusPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        ConfigProvider provider = new ConfigProvider(plugin);

        provider.init();

        Config config = provider.get();
        assertTrue(Files.exists(tempDir.resolve("currencies.yml")));
        assertEquals(List.of("vault", "playerpoints"), config.getCurrencies().getEnabledCurrencies());
        assertEquals("Player Points", config.getCurrencies().getPlayerPoints().getDisplayName());
        assertEquals(25, config.getCurrencies().getPlayerPoints().getQuestResetCost());
        assertEquals(11, config.getCurrencies().getPlayerPoints().getButton().getSlot());
        assertEquals("Ingame Money", config.getCurrencies().getVault().getDisplayName());
        assertEquals(1000.0D, config.getCurrencies().getVault().getQuestResetCost());
        assertEquals(15, config.getCurrencies().getVault().getButton().getSlot());
    }
}
