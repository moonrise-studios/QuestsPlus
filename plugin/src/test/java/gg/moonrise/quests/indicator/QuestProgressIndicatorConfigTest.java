package gg.moonrise.quests.indicator;

import gg.moonrise.quests.config.ActionBarProgressIndicator;
import gg.moonrise.quests.config.BossBarProgressIndicator;
import gg.moonrise.quests.config.ChatProgressIndicator;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.ProgressIndicators;
import gg.moonrise.quests.core.service.ConfigProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestProgressIndicatorConfigTest {

    private final ConfigProvider configProvider = mock(ConfigProvider.class);
    private final ProgressIndicators defaults = new Config().getProgressIndicators();

    @Test
    public void indicatorsReadTheirOwnSection() {
        ProgressIndicators config = mock(ProgressIndicators.class);
        BossBarProgressIndicator bossBar = mock(BossBarProgressIndicator.class);
        ActionBarProgressIndicator actionBar = mock(ActionBarProgressIndicator.class);
        ChatProgressIndicator chat = mock(ChatProgressIndicator.class);
        when(config.getBossBar()).thenReturn(bossBar);
        when(config.getActionBar()).thenReturn(actionBar);
        when(config.getChat()).thenReturn(chat);
        when(bossBar.isEnabled()).thenReturn(false);
        when(bossBar.getTitle()).thenReturn("boss");
        when(actionBar.isEnabled()).thenReturn(true);
        when(actionBar.getTitle()).thenReturn("action");
        when(chat.isEnabled()).thenReturn(false);
        when(chat.getLine()).thenReturn("line");

        assertFalse(new BossBarQuestProgressIndicator(configProvider).isEnabled(config));
        assertEquals("boss", new BossBarQuestProgressIndicator(configProvider).titleTemplate(config));
        assertTrue(new ActionBarQuestProgressIndicator().isEnabled(config));
        assertEquals("action", new ActionBarQuestProgressIndicator().titleTemplate(config));
        assertFalse(new ChatQuestProgressIndicator(configProvider).isEnabled(config));
        assertEquals("line", new ChatQuestProgressIndicator(configProvider).titleTemplate(config));
    }

    @Test
    public void missingSectionsFallBackToDefaults() {
        ProgressIndicators empty = mock(ProgressIndicators.class);

        assertTrue(new BossBarQuestProgressIndicator(configProvider).isEnabled(empty));
        assertEquals(defaults.getBossBar().getTitle(), new BossBarQuestProgressIndicator(configProvider).titleTemplate(empty));
        assertTrue(new ActionBarQuestProgressIndicator().isEnabled(empty));
        assertEquals(defaults.getActionBar().getTitle(), new ActionBarQuestProgressIndicator().titleTemplate(empty));
        assertTrue(new ChatQuestProgressIndicator(configProvider).isEnabled(empty));
        assertEquals(defaults.getChat().getLine(), new ChatQuestProgressIndicator(configProvider).titleTemplate(empty));
    }
}
