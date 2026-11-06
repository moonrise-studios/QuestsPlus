package gg.moonrise.quests.command;

import gg.moonrise.engine.message.Message;
import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.paper.scheduler.impl.EntityScheduler;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.Messages;
import gg.moonrise.quests.config.SharedMessages;
import gg.moonrise.quests.config.SharedMessagesFile;
import gg.moonrise.quests.core.service.ConfigProvider;
import gg.moonrise.quests.core.service.GlobalQuestService;
import gg.moonrise.quests.core.service.QuestApiService;
import gg.moonrise.quests.core.service.QuestDefinitionService;
import gg.moonrise.quests.core.service.QuestIndicatorPreferenceService;
import gg.moonrise.quests.core.service.QuestMenuService;
import gg.moonrise.quests.core.service.QuestProgressIndicatorService;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import gg.moonrise.quests.core.service.QuestProgressService;
import gg.moonrise.quests.core.service.QuestSlotService;
import gg.moonrise.quests.core.service.QuestTextService;
import gg.moonrise.quests.core.service.QuestUsageService;
import gg.moonrise.quests.core.service.QuestStreakService;
import gg.moonrise.quests.core.service.SqlProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class QuestCommandMessagesTest {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private PlayerMock admin;
    private CommandSourceStack source;
    private MockedStatic<Scheduler> scheduler;
    private ConfigProvider configProvider;
    private QuestService questService;
    private QuestUsageService usageService;
    private QuestStreakService streakService;
    private QuestCommand command;

    @BeforeEach
    public void setUp() {
        ServerMock server = MockBukkit.mock();
        admin = server.addPlayer("Admin");
        server.addPlayer("Target");
        source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(admin);

        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(entityScheduler.run(any())).thenAnswer(invocation -> {
            Consumer<ScheduledTask> action = invocation.getArgument(0);
            action.accept(null);
            return mock(ScheduledTask.class);
        });
        scheduler = mockStatic(Scheduler.class);
        scheduler.when(() -> Scheduler.entity(admin)).thenReturn(entityScheduler);

        configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(new Config());
        questService = mock(QuestService.class);
        usageService = mock(QuestUsageService.class);
        streakService = mock(QuestStreakService.class);
        QuestResetService resetService = mock(QuestResetService.class);
        when(resetService.currentResetKey()).thenReturn("daily");
        command = new QuestCommand(
                configProvider,
                mock(QuestDefinitionService.class),
                questService,
                mock(QuestSlotService.class),
                usageService,
                mock(QuestProgressService.class),
                mock(QuestTextService.class),
                mock(QuestMenuService.class),
                resetService,
                streakService,
                mock(SqlProvider.class),
                mock(GlobalQuestService.class),
                mock(QuestProgressIndicatorService.class),
                mock(QuestIndicatorPreferenceService.class),
                mock(QuestApiService.class)
        );
    }

    @AfterEach
    public void tearDown() {
        scheduler.close();
        MockBukkit.unmock();
    }

    @Test
    public void commandMessagesDefaultToThePreviousHardCodedText() {
        Messages messages = Config.compose(null, null, null, null, null, null, null).getMessages();

        assertEquals("<red>Failed to load quest progress right now.", messages.getProgressLoadFailed().content());
        assertEquals("<red>Failed to load completed quest count right now.", messages.getCompletedCountLoadFailed().content());
        assertEquals("<red>Failed to update quest indicator preference.", messages.getIndicatorUpdateFailed().content());
        assertEquals("<red>Failed to reset that player's quests.", messages.getResetPlayerFailed().content());
        assertEquals("<red>Failed to complete that player's daily quests.", messages.getCompleteDailyPlayerFailed().content());
        assertEquals("<red>Failed to refresh the active global quest.", messages.getGlobalQuestRefreshFailed().content());
        assertEquals("<red>Failed to reset that player's rerolls.", messages.getRerollsResetFailed().content());
        assertEquals("<red>Failed to update streak currency.", messages.getStreakCurrencyUpdateFailed().content());
    }

    @Test
    public void disabledStreaksSendConfiguredMessage() throws ReflectiveOperationException {
        SharedMessages shared = new SharedMessages();
        setField(shared, "questStreaksDisabled", Message.of("Streaks are off."));
        when(configProvider.get()).thenReturn(configWith(shared));
        when(streakService.isEnabled()).thenReturn(false);

        command.shieldGive(source, 1, "Target");

        assertEquals("Streaks are off.", nextMessage());
    }

    @Test
    public void failedAdminActionsSendConfiguredMessages() throws ReflectiveOperationException {
        SharedMessages shared = new SharedMessages();
        setField(shared, "resetPlayerFailed", Message.of("Reset broke."));
        setField(shared, "rerollsResetFailed", Message.of("Reroll reset broke."));
        setField(shared, "streakCurrencyUpdateFailed", Message.of("Currency broke."));
        when(configProvider.get()).thenReturn(configWith(shared));
        when(questService.resetDaily(any(), any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")));
        when(usageService.resetRerolls(any(), any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")));
        when(streakService.isEnabled()).thenReturn(true);
        when(streakService.adjustCurrency(any(), any(), anyInt())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")));

        command.reset(source, "Target");
        command.resetRerolls(source, "Target");
        command.recoveryTake(source, 2, "Target");

        assertEquals("Reset broke.", nextMessage());
        assertEquals("Reroll reset broke.", nextMessage());
        assertEquals("Currency broke.", nextMessage());
    }

    @Test
    public void failedProgressLoadSendsDefaultMessage() {
        when(questService.ensurePlayerStateAsync(admin, "daily")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")));

        command.progress(source);

        assertEquals("Failed to load quest progress right now.", nextMessage());
    }

    private String nextMessage() {
        Component message = admin.nextComponentMessage();
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
