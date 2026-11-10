package gg.moonrise.quests.presentation.command;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.engine.paper.scheduler.impl.EntityScheduler;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.ConfigProvider;
import gg.moonrise.quests.application.global.GlobalQuestService;
import gg.moonrise.quests.application.quest.QuestDefinitionService;
import gg.moonrise.quests.application.indicator.QuestIndicatorPreferenceService;
import gg.moonrise.quests.presentation.menu.QuestMenuService;
import gg.moonrise.quests.application.indicator.QuestProgressIndicatorService;
import gg.moonrise.quests.application.reset.QuestResetService;
import gg.moonrise.quests.application.quest.QuestService;
import gg.moonrise.quests.application.quest.QuestProgressService;
import gg.moonrise.quests.application.quest.QuestSlotService;
import gg.moonrise.quests.application.quest.QuestTextService;
import gg.moonrise.quests.application.quest.QuestUsageService;
import gg.moonrise.quests.application.streak.QuestStreakService;
import gg.moonrise.quests.application.streak.QuestStreakService.StreakCurrency;
import gg.moonrise.quests.application.reload.QuestReloadService;
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

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QuestCommandCurrencyTest {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private PlayerMock admin;
    private PlayerMock target;
    private CommandSourceStack source;
    private MockedStatic<Scheduler> scheduler;
    private QuestStreakService streakService;
    private QuestCommand command;

    @BeforeEach
    public void setUp() {
        ServerMock server = MockBukkit.mock();
        admin = server.addPlayer("Admin");
        target = server.addPlayer("Target");
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

        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(new Config());
        streakService = mock(QuestStreakService.class);
        when(streakService.isEnabled()).thenReturn(true);
        when(streakService.adjustCurrency(any(), any(), anyInt())).thenReturn(CompletableFuture.completedFuture(7));
        command = new QuestCommand(
                configProvider,
                mock(QuestDefinitionService.class),
                mock(QuestService.class),
                mock(QuestSlotService.class),
                mock(QuestUsageService.class),
                mock(QuestProgressService.class),
                mock(QuestTextService.class),
                mock(QuestMenuService.class),
                mock(QuestResetService.class),
                streakService,
                mock(QuestReloadService.class),
                mock(GlobalQuestService.class),
                mock(QuestProgressIndicatorService.class),
                mock(QuestIndicatorPreferenceService.class)
        );
    }

    @AfterEach
    public void tearDown() {
        scheduler.close();
        MockBukkit.unmock();
    }

    @Test
    public void giveAddsTheAmount() {
        command.shieldGive(source, 5, "Target");
        command.recoveryGive(source, 2, "Target");

        verify(streakService).adjustCurrency(target.getUniqueId(), StreakCurrency.SHIELD, 5);
        verify(streakService).adjustCurrency(target.getUniqueId(), StreakCurrency.RECOVERY, 2);
        assertEquals("Updated Target's shield balance to 7.", nextMessage());
        assertEquals("Updated Target's recovery balance to 7.", nextMessage());
    }

    @Test
    public void takeSubtractsTheAmount() {
        command.shieldTake(source, 3, "Target");
        command.recoveryTake(source, Integer.MAX_VALUE, "Target");

        verify(streakService).adjustCurrency(target.getUniqueId(), StreakCurrency.SHIELD, -3);
        verify(streakService).adjustCurrency(target.getUniqueId(), StreakCurrency.RECOVERY, -Integer.MAX_VALUE);
    }

    @Test
    public void nonPositiveAmountsAreRejectedBeforeAnyLookup() {
        String invalid = PLAIN_TEXT.serialize(new Config().getMessages().getInvalidAdminProgressAmount().asComponent());

        command.shieldGive(source, 0, "Target");
        command.shieldTake(source, -4, "Target");
        command.recoveryTake(source, Integer.MIN_VALUE, "Target");
        when(streakService.isEnabled()).thenReturn(false);
        command.recoveryGive(source, -1, "Target");

        assertEquals(invalid, nextMessage());
        assertEquals(invalid, nextMessage());
        assertEquals(invalid, nextMessage());
        assertEquals(invalid, nextMessage());
        verify(streakService, never()).adjustCurrency(any(), any(), anyInt());
    }

    @Test
    public void disabledStreaksAreReported() {
        when(streakService.isEnabled()).thenReturn(false);

        command.shieldGive(source, 1, "Target");

        assertEquals("Quest streaks are disabled.", nextMessage());
        verify(streakService, never()).adjustCurrency(any(), any(), anyInt());
    }

    @Test
    public void unknownPlayerIsReported() {
        command.recoveryTake(source, 1, "Nobody");

        assertNotNull(admin.nextComponentMessage());
        assertNull(admin.nextComponentMessage());
        verify(streakService, never()).adjustCurrency(any(), any(), anyInt());
    }

    private String nextMessage() {
        Component message = admin.nextComponentMessage();
        assertNotNull(message);
        return PLAIN_TEXT.serialize(message);
    }
}
