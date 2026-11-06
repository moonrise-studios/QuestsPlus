package gg.moonrise.quests.core.service;

import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigProviderReloadTest {

    @TempDir
    private Path tempDir;

    @Test
    public void loadFromDiskDoesNotChangeActiveConfigUntilApplied() throws Exception {
        QuestsPlusPlugin plugin = mock(QuestsPlusPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        ConfigProvider provider = new ConfigProvider(plugin);
        provider.init();
        Config active = provider.get();

        Path storageFile = tempDir.resolve("storage-settings.yml");
        String storage = Files.readString(storageFile);
        Files.writeString(storageFile, storage.replace("storage/quests.db", "storage/changed.db"));

        Config candidate = provider.loadFromDisk();

        assertEquals("storage/changed.db", candidate.getStorage().getDatabaseFile());
        assertSame(active, provider.get());
        assertEquals("storage/quests.db", provider.get().getStorage().getDatabaseFile());

        provider.apply(candidate);

        assertSame(candidate, provider.get());
    }
}
