package gg.moonrise.quests.core.service;

import gg.moonrise.engine.message.Message;
import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.paper.scheduler.impl.EntityScheduler;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.Messages;
import gg.moonrise.quests.config.SharedMessages;
import gg.moonrise.quests.config.SharedMessagesFile;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class QuestMenuServiceMessagesTest {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private PlayerMock player;
    private MockedStatic<Scheduler> scheduler;
    private QuestService questService;
    private QuestStreakService streakService;
    private ConfigProvider configProvider;
    private QuestMenuService menuService;

    @BeforeEach
    public void setUp() {
        ServerMock server = MockBukkit.mock();
        player = server.addPlayer("MessageTester");
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(entityScheduler.run(any())).thenAnswer(invocation -> {
            Consumer<ScheduledTask> action = invocation.getArgument(0);
            action.accept(null);
            return mock(ScheduledTask.class);
        });
        scheduler = mockStatic(Scheduler.class);
        scheduler.when(() -> Scheduler.entity(player)).thenReturn(entityScheduler);

        configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(new Config());
        questService = mock(QuestService.class);
        streakService = mock(QuestStreakService.class);
        QuestResetService resetService = mock(QuestResetService.class);
        when(resetService.currentResetKey()).thenReturn("daily");
        menuService = new QuestMenuService(
                configProvider,
                mock(QuestDefinitionService.class),
                questService,
                mock(QuestSlotService.class),
                mock(QuestUsageService.class),
                mock(QuestSelectionService.class),
                resetService,
                streakService,
                mock(GlobalQuestService.class),
                mock(QuestMenuItems.class),
                mock(QuestResetCurrencies.class),
                mock(QuestResetPurchaseFlow.class)
        );
    }

    @AfterEach
    public void tearDown() {
        scheduler.close();
        MockBukkit.unmock();
    }

    @Test
    public void menuMessagesDefaultToThePreviousHardCodedText() {
        Messages messages = Config.compose(null, null, null, null, null, null, null).getMessages();

        assertEquals("<red>Failed to open quests right now.", messages.getMenuOpenFailed().content());
        assertEquals("<red>Quest milestones are disabled.", messages.getQuestMilestonesDisabled().content());
        assertEquals("<red>Failed to open quest milestones right now.", messages.getMilestonesOpenFailed().content());
        assertEquals("<red>Unknown quest difficulty.", messages.getUnknownQuestDifficulty().content());
        assertEquals("<red>Quest streaks are disabled.", messages.getQuestStreaksDisabled().content());
        assertEquals("<red>Failed to open quest streaks right now.", messages.getStreaksOpenFailed().content());
        assertEquals("<red>Failed to open quest streak confirmation right now.", messages.getStreakConfirmationOpenFailed().content());
        assertEquals("<red>Failed to open quest reset purchases right now.", messages.getQuestResetPurchaseOpenFailed().content());
        assertEquals("<red>Failed to open quest difficulty picker right now.", messages.getDifficultyPickerOpenFailed().content());
        assertEquals("<red>Failed to open quest reroll picker right now.", messages.getRerollPickerOpenFailed().content());
        assertEquals("<red>Failed to select that quest difficulty right now.", messages.getDifficultySelectFailed().content());
    }

    @Test
    public void disabledFeaturesSendConfiguredMessages() throws ReflectiveOperationException {
        SharedMessages shared = new SharedMessages();
        setField(shared, "questMilestonesDisabled", Message.of("Milestones are off."));
        setField(shared, "questStreaksDisabled", Message.of("Streaks are off."));
        Config config = configWith(shared);
        setField(config, "questMilestonesEnabled", false);
        when(configProvider.get()).thenReturn(config);
        when(streakService.isEnabled()).thenReturn(false);

        menuService.openMilestoneSelector(player);
        menuService.openStreaks(player);
        menuService.applyStreakRecovery(player);

        assertEquals("Milestones are off.", nextMessage());
        assertEquals("Streaks are off.", nextMessage());
        assertEquals("Streaks are off.", nextMessage());
    }

    @Test
    public void failedMenuLoadSendsConfiguredFailureMessage() throws ReflectiveOperationException {
        SharedMessages shared = new SharedMessages();
        setField(shared, "menuOpenFailed", Message.of("Quests are unavailable."));
        when(configProvider.get()).thenReturn(configWith(shared));
        when(questService.ensurePlayerStateAsync(player, "daily")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")));

        menuService.openDailyQuests(player);

        assertEquals("Quests are unavailable.", nextMessage());
    }

    @Test
    public void failedDifficultyPickerLoadSendsDefaultFailureMessage() {
        when(questService.ensurePlayerStateAsync(player, "daily")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")));

        menuService.openDifficultyPicker(player, 0);

        assertEquals("Failed to open quest difficulty picker right now.", nextMessage());
    }

    private String nextMessage() {
        Component message = player.nextComponentMessage();
        assertNotNull(message);
        return PLAIN_TEXT.serialize(message);
    }

    private static Config configWith(SharedMessages shared) throws ReflectiveOperationException {
        SharedMessagesFile file = new SharedMessagesFile();
        setField(file, "messages", shared);
        return Config.compose(null, null, null, null, file, null, null);
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
