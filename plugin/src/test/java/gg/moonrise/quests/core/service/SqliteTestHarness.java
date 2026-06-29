package gg.moonrise.quests.core.service;

import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class SqliteTestHarness {

    private SqliteTestHarness() {
    }

    static SqliteProvider open(Path dataFolder) {
        QuestsPlusPlugin plugin = mock(QuestsPlusPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());

        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(new Config());

        SqliteProvider provider = new SqliteProvider(plugin, configProvider);
        provider.init();

        assertTrue(provider.isAvailable());
        return provider;
    }
}
