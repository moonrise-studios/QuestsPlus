package gg.moonrise.quests.core.service;

import gg.moonrise.quests.QuestsPlusPlugin;
import gg.moonrise.quests.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigProviderCurrencyTest {

    @TempDir
    private Path tempDir;

    @Test
    public void loadsDefaultCurrencyConfigFromCurrenciesYml() throws Exception {
        QuestsPlusPlugin plugin = mock(QuestsPlusPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        ConfigProvider provider = new ConfigProvider(plugin);

        provider.init();

        Config config = provider.get();
        assertTrue(Files.exists(tempDir.resolve("storage-settings.yml")));
        assertTrue(Files.exists(tempDir.resolve("quest-menu.yml")));
        assertTrue(Files.exists(tempDir.resolve("currencies.yml")));
        assertTrue(Files.exists(tempDir.resolve("quest-resets.yml")));
        assertTrue(Files.exists(tempDir.resolve("quest-milestones.yml")));
        assertTrue(Files.exists(tempDir.resolve("progress-indicators.yml")));
        assertEquals(List.of("vault", "playerpoints"), config.getCurrencies().getEnabledCurrencies());
        assertEquals("storage/quests.db", config.getStorage().getDatabaseFile());
        assertEquals("<dark_gray>Daily Quests", config.getMenu().getTitle());
        assertEquals("<dark_gray>Select Difficulty", config.getMenu().getDifficultyPicker().getTitle());
        assertEquals("Player Points", config.getCurrencies().getPlayerPoints().getDisplayName());
        assertEquals(25, config.getCurrencies().getPlayerPoints().getQuestResetCost());
        assertEquals(11, config.getCurrencies().getPlayerPoints().getButton().getSlot());
        assertEquals("Ingame Money", config.getCurrencies().getVault().getDisplayName());
        assertEquals(1000.0D, config.getCurrencies().getVault().getQuestResetCost());
        assertEquals(15, config.getCurrencies().getVault().getButton().getSlot());
        assertEquals(1, config.getMenu().getResetMenu().getDailyLimit());
        assertEquals(18, config.getMenu().getResetButton().getSlot());
        assertEquals("<dark_gray>Quest Milestones", config.getMilestoneMenu().getSelectorTitle());
        assertEquals("<green>Milestone complete: <white><milestone_display_name></white> <gray>(<quest_difficulty>, <milestone_completed> quests).", config.getMessages().getMilestoneCompleted().content());
        assertTrue(config.getProgressIndicators().isEnabled());
        assertTrue(config.getProgressIndicators().getBossBar().isEnabled());
        assertTrue(config.getProgressIndicators().getChat().isEnabled());
        assertTrue(config.getProgressIndicators().getActionBar().isEnabled());

        String daily = Files.readString(tempDir.resolve("daily.yml"));
        String messages = Files.readString(tempDir.resolve("messages.yml"));
        assertFalse(daily.contains("difficulty-picker"));
        assertFalse(daily.contains("active-quest"));
        assertFalse(messages.contains("milestone-menu"));
        assertFalse(messages.contains("progress-indicators"));
    }
}
