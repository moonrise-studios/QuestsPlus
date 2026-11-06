package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.indicator.QuestProgressIndicator;
import gg.moonrise.quests.indicator.QuestProgressIndicatorContext;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestTypes;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestProgressIndicatorServiceTest {

    private ServerMock server;
    private ConfigProvider configProvider;
    private QuestDefinitionService definitionService;
    private QuestIndicatorPreferenceService preferenceService;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        configProvider = mock(ConfigProvider.class);
        definitionService = mock(QuestDefinitionService.class);
        preferenceService = mock(QuestIndicatorPreferenceService.class);
        when(configProvider.get()).thenReturn(new Config());
        when(definitionService.variablePlaceholders(any())).thenReturn(Map.of());
        when(preferenceService.cachedPreference(any(), any())).thenReturn(Optional.empty());
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void availableTypesAskEachIndicatorWhetherItIsEnabled() {
        QuestProgressIndicatorService service = service(
                new RecordingIndicator("BOSS_BAR", false, ""),
                new RecordingIndicator("ACTION_BAR", true, ""),
                new RecordingIndicator("CUSTOM", true, "")
        );

        assertEquals(List.of("ACTION_BAR", "CUSTOM"), service.availableIndicatorTypes());
    }

    @Test
    public void titleIsRenderedFromTheShownIndicatorsTemplate() {
        PlayerMock player = server.addPlayer("Miner");
        RecordingIndicator bossBar = new RecordingIndicator("BOSS_BAR", true, "<quest_display_name> <progress>/<goal_amount>");
        RecordingIndicator actionBar = new RecordingIndicator("ACTION_BAR", true, "unused");
        QuestProgressIndicatorService service = service(actionBar, bossBar);

        service.showPersonal(player, quest(5), 4);

        assertTrue(actionBar.shown.isEmpty());
        assertEquals(1, bossBar.shown.size());
        assertEquals("Stone Breaker 5/10", PlainTextComponentSerializer.plainText().serialize(bossBar.shown.getFirst().title()));
    }

    private QuestProgressIndicatorService service(QuestProgressIndicator... indicators) {
        return new QuestProgressIndicatorService(configProvider, definitionService, preferenceService, List.of(indicators));
    }

    private static GeneratedQuest quest(int progress) {
        return new GeneratedQuest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "reset",
                "break-stone",
                QuestTypes.KILL_ALL_MOBS,
                "easy",
                "Easy",
                "<gray>Stone Breaker",
                List.of(),
                Map.of(),
                10,
                progress,
                false
        );
    }

    private static final class RecordingIndicator implements QuestProgressIndicator {

        private final String type;
        private final boolean enabled;
        private final String template;
        private final List<QuestProgressIndicatorContext> shown = new ArrayList<>();

        private RecordingIndicator(String type, boolean enabled, String template) {
            this.type = type;
            this.enabled = enabled;
            this.template = template;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public boolean isEnabled(Config.ProgressIndicators config) {
            return enabled;
        }

        @Override
        public String titleTemplate(Config.ProgressIndicators config) {
            return template;
        }

        @Override
        public void show(QuestProgressIndicatorContext context) {
            shown.add(context);
        }

        @Override
        public void clear(UUID playerId) {
        }

        @Override
        public void clearAll() {
        }
    }
}
