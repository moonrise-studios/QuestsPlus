package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.ProgressIndicators;
import gg.moonrise.quests.indicator.QuestProgressIndicator;
import gg.moonrise.quests.indicator.QuestProgressIndicatorContext;
import gg.moonrise.quests.domain.model.GlobalQuestState;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestTypes;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
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

    @Test
    public void globalRankMatchesSortedContributionOrder() {
        PlayerMock player = server.addPlayer("Contributor");
        RecordingIndicator bossBar = new RecordingIndicator("BOSS_BAR", true, "");
        QuestProgressIndicatorService service = service(bossBar);
        Random random = new Random(34L);

        for (int round = 0; round < 50; round++) {
            Map<UUID, Integer> contributions = new HashMap<>();
            for (int index = 0; index < 40; index++) {
                contributions.put(UUID.randomUUID(), random.nextInt(6));
            }
            contributions.put(player.getUniqueId(), random.nextInt(6));

            service.showGlobal(player, globalState(contributions), 0);

            Map<String, String> placeholders = bossBar.shown.getLast().placeholders();
            assertEquals(expectedRank(contributions, player.getUniqueId()), placeholders.get("global_rank"));
            assertEquals(Long.toString(contributions.values().stream().filter(value -> value > 0).count()), placeholders.get("global_participants"));
        }
    }

    @Test
    public void tiedContributorsAreOrderedByUuid() {
        PlayerMock player = server.addPlayer("Tied");
        RecordingIndicator bossBar = new RecordingIndicator("BOSS_BAR", true, "");
        QuestProgressIndicatorService service = service(bossBar);
        UUID before = new UUID(0L, 0L);
        UUID after = new UUID(-1L, -1L);

        service.showGlobal(player, globalState(Map.of(before, 7, player.getUniqueId(), 7, after, 7, UUID.randomUUID(), 0)), 0);

        Map<String, String> placeholders = bossBar.shown.getLast().placeholders();
        assertEquals("2", placeholders.get("global_rank"));
        assertEquals("3", placeholders.get("global_participants"));
    }

    @Test
    public void playerWithoutContributionHasNoRank() {
        PlayerMock player = server.addPlayer("Idle");
        RecordingIndicator bossBar = new RecordingIndicator("BOSS_BAR", true, "");
        QuestProgressIndicatorService service = service(bossBar);

        service.showGlobal(player, globalState(Map.of(UUID.randomUUID(), 3)), 0);

        assertEquals("-", bossBar.shown.getLast().placeholders().get("global_rank"));
    }

    @Test
    public void personalPlaceholdersAreRenderedWithFormattedNumbersAndPercent() {
        PlayerMock player = server.addPlayer("Formatter");
        RecordingIndicator bossBar = new RecordingIndicator("BOSS_BAR", true, "<quest_display_name> <progress>/<goal_amount> (<percent>)");
        QuestProgressIndicatorService service = service(bossBar);

        service.showPersonal(player, quest(5), 4);

        QuestProgressIndicatorContext shown = bossBar.shown.getLast();
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("player", "Formatter");
        expected.put("uuid", player.getUniqueId().toString());
        expected.put("quest_id", "break-stone");
        expected.put("quest_display_name", "Stone Breaker");
        expected.put("quest_difficulty", "Easy");
        expected.put("quest_difficulty_id", "easy");
        expected.put("goal_amount", "10");
        expected.put("goal-amount", "10");
        expected.put("progress", "5");
        expected.put("percent", "50.00%");
        assertEquals(expected, shown.placeholders());
        assertEquals("Stone Breaker 5/10 (50.00%)", PlainTextComponentSerializer.plainText().serialize(shown.title()));
    }

    @Test
    public void globalPlaceholdersAreRenderedWithFormattedNumbersPercentAndRank() {
        PlayerMock player = server.addPlayer("GlobalFormatter");
        RecordingIndicator bossBar = new RecordingIndicator("BOSS_BAR", true, "");
        QuestProgressIndicatorService service = service(bossBar);
        UUID other = UUID.randomUUID();

        service.showGlobal(player, globalState(Map.of(player.getUniqueId(), 1_250, other, 3_750)), 0);

        Map<String, String> placeholders = bossBar.shown.getLast().placeholders();
        assertEquals("5,000", placeholders.get("progress"));
        assertEquals("10,000", placeholders.get("goal_amount"));
        assertEquals("50.00%", placeholders.get("percent"));
        assertEquals("5,000", placeholders.get("global_progress"));
        assertEquals("10,000", placeholders.get("global_goal_amount"));
        assertEquals("50", placeholders.get("global_percent"));
        assertEquals("1,250", placeholders.get("contribution"));
        assertEquals("25", placeholders.get("contribution_percent"));
        assertEquals("2", placeholders.get("global_rank"));
        assertEquals("2", placeholders.get("global_participants"));
        assertEquals("Ends in 6d", placeholders.get("global_time_remaining"));
        assertEquals("Global Slayer", placeholders.get("quest_display_name"));
    }

    private static String expectedRank(Map<UUID, Integer> contributions, UUID playerId) {
        List<UUID> ranked = contributions.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed().thenComparing(entry -> entry.getKey().toString()))
                .map(Map.Entry::getKey)
                .toList();
        int index = ranked.indexOf(playerId);
        return index < 0 ? "-" : Integer.toString(index + 1);
    }

    private static GlobalQuestState globalState(Map<UUID, Integer> contributions) {
        LocalDateTime startsAt = LocalDateTime.now().minusDays(1);
        int progress = contributions.values().stream().mapToInt(Integer::intValue).sum();
        GeneratedQuest quest = new GeneratedQuest(
                UUID.randomUUID(),
                new UUID(0L, 0L),
                "period",
                "global-kill-all-mobs",
                QuestTypes.KILL_ALL_MOBS,
                "easy",
                "Easy",
                "Global Slayer",
                List.of(),
                Map.of(),
                10_000,
                progress,
                false
        );
        return new GlobalQuestState(quest, "period", startsAt, startsAt.plusDays(7), Map.copyOf(contributions), false);
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
        public boolean isEnabled(ProgressIndicators config) {
            return enabled;
        }

        @Override
        public String titleTemplate(ProgressIndicators config) {
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
